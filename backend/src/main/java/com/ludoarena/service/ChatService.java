package com.ludoarena.service;

import com.ludoarena.dto.Dtos.ChatMessageDto;
import com.ludoarena.exception.ApiException;
import com.ludoarena.game.model.PlayerState;
import com.ludoarena.model.ChatMessage;
import com.ludoarena.model.Match;
import com.ludoarena.repository.ChatMessageRepository;
import com.ludoarena.repository.MatchRepository;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * In-match chat between the human players of a match. The sender always comes from the authenticated session, the
 * text is normalised and length-limited, and each user is rate limited. Messages are broadcast to
 * /topic/match/{id}/chat, which only participants (and admins) may subscribe to.
 */
@Service
public class ChatService {
    static final int MAX_LENGTH = 200;
    /** at most BURST messages per WINDOW_MS per user */
    static final int BURST = 5;
    static final long WINDOW_MS = 10_000;

    private final MatchRepository matches;
    private final ChatMessageRepository chat;
    private final SimpMessagingTemplate messaging;
    private final ConcurrentHashMap<String, Deque<Long>> recent = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    @Autowired // two constructors: tell Spring which one to use (the other injects a test clock)
    public ChatService(MatchRepository matches, ChatMessageRepository chat, SimpMessagingTemplate messaging) {
        this(matches, chat, messaging, System::currentTimeMillis);
    }

    ChatService(MatchRepository matches, ChatMessageRepository chat, SimpMessagingTemplate messaging, LongSupplier clock) {
        this.matches = matches;
        this.chat = chat;
        this.messaging = messaging;
        this.clock = clock;
    }

    public ChatMessageDto send(String matchId, String userId, String rawText) {
        String text = normalise(rawText);
        if (text.isEmpty()) throw ApiException.badRequest("EMPTY_MESSAGE", "Type a message first");
        if (text.length() > MAX_LENGTH) throw ApiException.badRequest("MESSAGE_TOO_LONG", "Messages can be at most " + MAX_LENGTH + " characters");
        Match m = matches.findById(matchId).orElseThrow(() -> ApiException.notFound("MATCH_NOT_FOUND", "Match not found"));
        if (!m.playerIds.contains(userId)) throw ApiException.forbidden("NOT_A_PLAYER", "You are not part of this match");
        if (m.vsComputer) throw ApiException.badRequest("CHAT_UNAVAILABLE", "Chat is only available in online matches");
        throttle(userId);

        PlayerState p = m.game.player(userId);
        ChatMessage msg = new ChatMessage();
        msg.matchId = matchId;
        msg.userId = userId;
        msg.name = p == null ? "Player" : p.name;
        msg.color = p == null || p.color == null ? null : p.color.name();
        msg.text = text;
        msg.at = Instant.ofEpochMilli(clock.getAsLong());
        msg = chat.save(msg);
        ChatMessageDto dto = ChatMessageDto.from(msg);
        messaging.convertAndSend("/topic/match/" + matchId + "/chat", dto);
        return dto;
    }

    /** Last 50 messages, oldest first. Participants and admins only. */
    public List<ChatMessageDto> history(String matchId, String viewerId, boolean admin) {
        Match m = matches.findById(matchId).orElseThrow(() -> ApiException.notFound("MATCH_NOT_FOUND", "Match not found"));
        if (!admin && !m.playerIds.contains(viewerId)) throw ApiException.forbidden("NOT_A_PLAYER", "You are not part of this match");
        List<ChatMessageDto> out = new ArrayList<>();
        for (ChatMessage c : chat.findTop50ByMatchIdOrderByAtDesc(matchId)) out.add(ChatMessageDto.from(c));
        Collections.reverse(out);
        return out;
    }

    /** Trims, removes control characters and collapses runs of whitespace (React escapes HTML on display). */
    static String normalise(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
    }

    private void throttle(String userId) {
        long now = clock.getAsLong();
        Deque<Long> times = recent.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MS) times.pollFirst();
            if (times.size() >= BURST) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CHAT_RATE_LIMITED", "You are sending messages too fast");
            }
            times.addLast(now);
        }
    }
}
