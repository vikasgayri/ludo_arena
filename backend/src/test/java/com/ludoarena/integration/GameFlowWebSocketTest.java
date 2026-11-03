package com.ludoarena.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Two real WebSocket clients play against the real server. Needs MongoDB (TEST_MONGODB_URI), see ApiIntegrationTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "TEST_MONGODB_URI", matches = ".+")
class GameFlowWebSocketTest {

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> System.getenv("TEST_MONGODB_URI"));
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper om;

    record Player(String id, String token) {
    }

    private Player register(String label) {
        String email = label + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        JsonNode res = rest.postForObject("/api/v1/auth/register",
                Map.of("name", "WS " + label, "email", email, "password", "Passw0rdOK"), JsonNode.class);
        return new Player(res.get("data").get("user").get("id").asText(), res.get("data").get("accessToken").asText());
    }

    private JsonNode rest(String method, String path, Player p, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(p.token());
        h.setContentType(MediaType.APPLICATION_JSON);
        var res = rest.exchange(path, org.springframework.http.HttpMethod.valueOf(method), new HttpEntity<>(body, h), JsonNode.class);
        return res.getBody();
    }

    private StompSession connect(Player p) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter conv = new MappingJackson2MessageConverter();
        conv.setObjectMapper(om);
        client.setMessageConverter(conv);
        StompHeaders connect = new StompHeaders();
        if (p != null) connect.add("Authorization", "Bearer " + p.token());
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connect,
                new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
    }

    private BlockingQueue<JsonNode> subscribe(StompSession s, String destination) {
        BlockingQueue<JsonNode> q = new LinkedBlockingQueue<>();
        s.subscribe(destination, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return JsonNode.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { q.add((JsonNode) payload); }
        });
        return q;
    }

    private JsonNode next(BlockingQueue<JsonNode> q, String what) throws InterruptedException {
        JsonNode n = q.poll(5, TimeUnit.SECONDS);
        assertNotNull(n, "timed out waiting for " + what);
        return n;
    }

    @Test
    void connectionWithoutAValidTokenIsRefused() {
        assertThrows(Exception.class, () -> connect(null));
        assertThrows(Exception.class, () -> connect(new Player("x", "not-a-token")));
    }

    @Test
    void twoClientsStayInSyncAndDuplicateOrOutOfTurnActionsAreRejected() throws Exception {
        Player a = register("a");
        Player b = register("b");
        Player outsider = register("c");

        String roomId = rest("POST", "/api/v1/rooms", a, Map.of("name", "WS room", "maxPlayers", 2, "isPrivate", false))
                .get("data").get("id").asText();
        rest("POST", "/api/v1/rooms/" + roomId + "/join", b, null);

        StompSession sa = connect(a);
        StompSession sb = connect(b);
        BlockingQueue<JsonNode> roomA = subscribe(sa, "/topic/room/" + roomId);
        BlockingQueue<JsonNode> errA = subscribe(sa, "/user/queue/errors");
        BlockingQueue<JsonNode> errB = subscribe(sb, "/user/queue/errors");
        subscribe(sb, "/topic/room/" + roomId);
        Thread.sleep(300);

        sa.send("/app/room.ready", Map.of("roomId", roomId, "ready", true));
        sb.send("/app/room.ready", Map.of("roomId", roomId, "ready", true));

        String matchId = null;
        for (int i = 0; i < 10 && matchId == null; i++) {
            JsonNode u = next(roomA, "room updates");
            if ("MATCH_STARTED".equals(u.get("type").asText())) matchId = u.get("matchId").asText();
        }
        assertNotNull(matchId, "match should start once both players are ready");

        BlockingQueue<JsonNode> matchA = subscribe(sa, "/topic/match/" + matchId);
        BlockingQueue<JsonNode> matchB = subscribe(sb, "/topic/match/" + matchId);
        BlockingQueue<JsonNode> stateA = subscribe(sa, "/user/queue/state");
        Thread.sleep(300);

        sa.send("/app/match.requestState", Map.of("matchId", matchId));
        JsonNode snap = next(stateA, "state snapshot");
        String current = snap.get("state").get("currentUserId").asText();
        assertNotNull(snap.get("reconnectToken").asText());

        StompSession currentSession = current.equals(a.id()) ? sa : sb;
        StompSession otherSession = current.equals(a.id()) ? sb : sa;
        BlockingQueue<JsonNode> otherErrors = current.equals(a.id()) ? errB : errA;

        // out-of-turn action is rejected privately
        otherSession.send("/app/match.rollDice", Map.of("matchId", matchId, "actionId", UUID.randomUUID().toString()));
        assertEquals("NOT_YOUR_TURN", next(otherErrors, "NOT_YOUR_TURN").get("code").asText());

        // in-turn roll is broadcast to BOTH clients with the server-generated dice value
        String actionId = UUID.randomUUID().toString();
        currentSession.send("/app/match.rollDice", Map.of("matchId", matchId, "actionId", actionId));
        JsonNode updA = next(matchA, "dice update on A");
        JsonNode updB = next(matchB, "dice update on B");
        assertEquals("DICE_ROLLED", updA.get("events").get(0).get("type").asText());
        int value = updA.get("events").get(0).get("data").get("value").asInt();
        assertTrue(value >= 1 && value <= 6);
        assertEquals(updA.get("events").get(0).get("eventId").asText(), updB.get("events").get(0).get("eventId").asText());

        // the same actionId delivered again must not roll again
        currentSession.send("/app/match.rollDice", Map.of("matchId", matchId, "actionId", actionId));
        Thread.sleep(700);
        for (JsonNode m : matchA) {
            for (JsonNode e : m.get("events")) assertFalse("DICE_ROLLED".equals(e.get("type").asText()), "duplicate action rolled twice");
        }
        sa.disconnect();
        sb.disconnect();
    }

    @Test
    void aNonParticipantCannotSubscribeToAMatchOrRoom() throws Exception {
        Player a = register("pa");
        Player intruder = register("pi");
        String roomId = rest("POST", "/api/v1/rooms", a, Map.of("name", "Private", "maxPlayers", 2, "isPrivate", true))
                .get("data").get("id").asText();
        StompSession s = connect(intruder);
        s.subscribe("/topic/room/" + roomId, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return JsonNode.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { }
        });
        long end = System.currentTimeMillis() + 3000;
        while (s.isConnected() && System.currentTimeMillis() < end) Thread.sleep(50);
        assertFalse(s.isConnected(), "server must close a session that subscribes to a room it is not in");
    }
}
