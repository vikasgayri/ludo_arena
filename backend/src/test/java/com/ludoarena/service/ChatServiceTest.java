package com.ludoarena.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ludoarena.dto.Dtos.ChatMessageDto;
import com.ludoarena.exception.ApiException;
import com.ludoarena.game.model.Color;
import com.ludoarena.game.model.GameState;
import com.ludoarena.game.model.PlayerState;
import com.ludoarena.model.ChatMessage;
import com.ludoarena.model.Match;
import com.ludoarena.repository.ChatMessageRepository;
import com.ludoarena.repository.MatchRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class ChatServiceTest {
    private MatchRepository matches;
    private ChatMessageRepository repo;
    private SimpMessagingTemplate messaging;
    private final AtomicLong now = new AtomicLong(1_000_000);
    private ChatService service;
    private Match match;

    @BeforeEach
    void setUp() {
        matches = mock(MatchRepository.class);
        repo = mock(ChatMessageRepository.class);
        messaging = mock(SimpMessagingTemplate.class);
        service = new ChatService(matches, repo, messaging, now::get);
        when(repo.save(any(ChatMessage.class))).thenAnswer(i -> {
            ChatMessage c = i.getArgument(0);
            c.id = "c" + now.get();
            return c;
        });
        match = new Match();
        match.id = "m1";
        match.playerIds.add("u1");
        match.playerIds.add("u2");
        match.game = new GameState();
        PlayerState a = new PlayerState();
        a.userId = "u1";
        a.name = "Asha";
        a.color = Color.RED;
        match.game.players.add(a);
        when(matches.findById("m1")).thenReturn(Optional.of(match));
    }

    @Test
    void aPlayerMessageIsStoredAndBroadcastWithServerSideIdentity() {
        ChatMessageDto dto = service.send("m1", "u1", "  Good   luck!  ");

        assertEquals("Good luck!", dto.text());
        assertEquals("Asha", dto.name());
        assertEquals("RED", dto.color());
        assertEquals("u1", dto.userId());
        verify(messaging).convertAndSend(eq("/topic/match/m1/chat"), any(ChatMessageDto.class));
    }

    @Test
    void outsidersCannotChat() {
        ApiException e = assertThrows(ApiException.class, () -> service.send("m1", "intruder", "hi"));
        assertEquals("NOT_A_PLAYER", e.code());
        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void emptyAndOverlongMessagesAreRejected() {
        assertEquals("EMPTY_MESSAGE", assertThrows(ApiException.class, () -> service.send("m1", "u1", " \n\t ")).code());
        assertEquals("MESSAGE_TOO_LONG", assertThrows(ApiException.class, () -> service.send("m1", "u1", "x".repeat(201))).code());
        verify(repo, never()).save(any(ChatMessage.class));
    }

    @Test
    void controlCharactersAreRemoved() {
        assertEquals("a b", ChatService.normalise("a\u0000\u0007b"));
    }

    @Test
    void chatIsNotAvailableAgainstTheComputer() {
        match.vsComputer = true;
        assertEquals("CHAT_UNAVAILABLE", assertThrows(ApiException.class, () -> service.send("m1", "u1", "hi")).code());
    }

    @Test
    void floodingIsRateLimitedAndRecoversAfterTheWindow() {
        for (int i = 0; i < ChatService.BURST; i++) service.send("m1", "u1", "msg " + i);
        ApiException e = assertThrows(ApiException.class, () -> service.send("m1", "u1", "one too many"));
        assertEquals("CHAT_RATE_LIMITED", e.code());
        assertEquals(429, e.status().value());

        now.addAndGet(ChatService.WINDOW_MS);
        service.send("m1", "u1", "fine again");
    }

    @Test
    void historyIsOldestFirstAndOnlyForParticipantsOrAdmins() {
        ChatMessage newer = new ChatMessage();
        newer.id = "2";
        newer.text = "second";
        newer.at = Instant.ofEpochMilli(2);
        ChatMessage older = new ChatMessage();
        older.id = "1";
        older.text = "first";
        older.at = Instant.ofEpochMilli(1);
        when(repo.findTop50ByMatchIdOrderByAtDesc("m1")).thenReturn(List.of(newer, older));

        List<ChatMessageDto> h = service.history("m1", "u2", false);
        assertEquals(List.of("first", "second"), h.stream().map(ChatMessageDto::text).toList());
        assertEquals(2, service.history("m1", "admin", true).size());
        assertThrows(ApiException.class, () -> service.history("m1", "intruder", false));
    }
}
