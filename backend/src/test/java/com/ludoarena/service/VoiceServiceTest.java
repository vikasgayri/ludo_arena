package com.ludoarena.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ludoarena.config.VoiceProperties;
import com.ludoarena.dto.Dtos.IceServerDto;
import com.ludoarena.dto.Dtos.VoiceSignalDto;
import com.ludoarena.exception.ApiException;
import com.ludoarena.game.model.GameState;
import com.ludoarena.game.model.PlayerState;
import com.ludoarena.model.Enums.MatchStatus;
import com.ludoarena.model.Match;
import com.ludoarena.repository.MatchRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class VoiceServiceTest {
    private MatchRepository matches;
    private SimpMessagingTemplate messaging;
    private final AtomicLong now = new AtomicLong(1_700_000_000_000L);
    private Match match;

    private VoiceService service(VoiceProperties props) {
        return new VoiceService(matches, messaging, props, new ObjectMapper(), now::get);
    }

    private static VoiceProperties stunOnly() {
        return new VoiceProperties(true, "stun:stun.example:3478", "", "", "", "", 0);
    }

    @BeforeEach
    void setUp() {
        matches = mock(MatchRepository.class);
        messaging = mock(SimpMessagingTemplate.class);
        match = new Match();
        match.id = "m1";
        match.status = MatchStatus.ACTIVE;
        match.playerIds.addAll(List.of("u1", "u2"));
        match.game = new GameState();
        PlayerState a = new PlayerState();
        a.userId = "u1";
        a.name = "Asha";
        match.game.players.add(a);
        when(matches.findById("m1")).thenReturn(Optional.of(match));
    }

    @Test
    void aSignalIsForwardedOnlyToTheAddressedPlayerWithTheSessionIdentity() {
        service(stunOnly()).relay("m1", "u1", "u2", "offer", Map.of("sdp", "v=0..."));

        ArgumentCaptor<VoiceSignalDto> sent = ArgumentCaptor.forClass(VoiceSignalDto.class);
        verify(messaging).convertAndSendToUser(eq("u2"), eq("/queue/voice"), sent.capture());
        assertEquals("u1", sent.getValue().from());
        assertEquals("Asha", sent.getValue().fromName());
        assertEquals("offer", sent.getValue().type());
    }

    @Test
    void outsidersAndNonPlayersAreRejected() {
        VoiceService s = service(stunOnly());
        assertEquals("NOT_A_PLAYER", assertThrows(ApiException.class, () -> s.relay("m1", "intruder", "u2", "offer", Map.of())).code());
        assertEquals("INVALID_PEER", assertThrows(ApiException.class, () -> s.relay("m1", "u1", "stranger", "offer", Map.of())).code());
        assertEquals("INVALID_PEER", assertThrows(ApiException.class, () -> s.relay("m1", "u1", "u1", "offer", Map.of())).code());
        verify(messaging, never()).convertAndSendToUser(anyString(), anyString(), any(Object.class));
    }

    @Test
    void noVoiceAgainstTheComputerOrAfterTheMatchExceptHangingUp() {
        VoiceService s = service(stunOnly());
        match.status = MatchStatus.FINISHED;
        assertEquals("MATCH_FINISHED", assertThrows(ApiException.class, () -> s.relay("m1", "u1", "u2", "offer", Map.of())).code());
        s.relay("m1", "u1", "u2", "leave", Map.of()); // allowed
        match.status = MatchStatus.ACTIVE;
        match.vsComputer = true;
        assertEquals("VOICE_UNAVAILABLE", assertThrows(ApiException.class, () -> s.relay("m1", "u1", "u2", "join", Map.of())).code());
    }

    @Test
    void oversizedSignalsAndFloodingAreRejected() {
        VoiceService s = service(stunOnly());
        String huge = "x".repeat(VoiceService.MAX_PAYLOAD_CHARS + 1);
        assertEquals("SIGNAL_TOO_LARGE", assertThrows(ApiException.class, () -> s.relay("m1", "u1", "u2", "offer", Map.of("sdp", huge))).code());
        for (int i = 0; i < VoiceService.BURST; i++) s.relay("m1", "u1", "u2", "ice", Map.of("c", i));
        assertEquals("VOICE_RATE_LIMITED", assertThrows(ApiException.class, () -> s.relay("m1", "u1", "u2", "ice", Map.of())).code());
        now.addAndGet(VoiceService.WINDOW_MS);
        s.relay("m1", "u1", "u2", "ice", Map.of());
    }

    @Test
    void disabledVoiceRefusesSignals() {
        VoiceService s = service(new VoiceProperties(false, "stun:x", "", "", "", "", 0));
        assertEquals("VOICE_DISABLED", assertThrows(ApiException.class, () -> s.relay("m1", "u1", "u2", "join", Map.of())).code());
    }

    @Test
    void iceServersAreStunOnlyByDefault() {
        List<IceServerDto> ice = service(stunOnly()).iceServers("u1");
        assertEquals(1, ice.size());
        assertEquals(List.of("stun:stun.example:3478"), ice.get(0).urls());
        assertNull(ice.get(0).username());
    }

    @Test
    void coturnSecretProducesShortLivedHmacCredentials() {
        VoiceProperties p = new VoiceProperties(true, "stun:s", "turn:turn.example:3478,turns:turn.example:5349", "shh", "", "", 3600);
        List<IceServerDto> ice = service(p).iceServers("u1");
        IceServerDto turn = ice.get(1);
        long expiry = now.get() / 1000 + 3600;
        assertEquals(expiry + ":u1", turn.username());
        assertEquals(VoiceService.hmacSha1Base64("shh", expiry + ":u1"), turn.credential());
        assertEquals(2, turn.urls().size());
    }

    @Test
    void staticTurnCredentialsArePassedThrough() {
        VoiceProperties p = new VoiceProperties(true, "stun:s", "turn:relay.example:80", "", "user", "pass", 0);
        IceServerDto turn = service(p).iceServers("u1").get(1);
        assertEquals("user", turn.username());
        assertEquals("pass", turn.credential());
    }
}
