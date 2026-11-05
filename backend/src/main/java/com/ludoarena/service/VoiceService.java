package com.ludoarena.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ludoarena.config.VoiceProperties;
import com.ludoarena.dto.Dtos.IceServerDto;
import com.ludoarena.dto.Dtos.VoiceSignalDto;
import com.ludoarena.exception.ApiException;
import com.ludoarena.game.model.PlayerState;
import com.ludoarena.model.Enums.MatchStatus;
import com.ludoarena.model.Match;
import com.ludoarena.repository.MatchRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Voice chat signalling. Audio never passes through the server: browsers connect peer-to-peer (WebRTC) and this
 * service only forwards their join/leave/offer/answer/ice messages to ONE other human player of the same running
 * match. The sender always comes from the authenticated session.
 */
@Service
public class VoiceService {
    static final int MAX_PAYLOAD_CHARS = 20_000; // an SDP offer is a few KB
    static final int BURST = 120; // ICE trickling sends many small messages
    static final long WINDOW_MS = 10_000;

    private final MatchRepository matches;
    private final SimpMessagingTemplate messaging;
    private final VoiceProperties props;
    private final ObjectMapper mapper;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Deque<Long>> recent = new ConcurrentHashMap<>();

    @Autowired
    public VoiceService(MatchRepository matches, SimpMessagingTemplate messaging, VoiceProperties props, ObjectMapper mapper) {
        this(matches, messaging, props, mapper, System::currentTimeMillis);
    }

    VoiceService(MatchRepository matches, SimpMessagingTemplate messaging, VoiceProperties props, ObjectMapper mapper, LongSupplier clock) {
        this.matches = matches;
        this.messaging = messaging;
        this.props = props;
        this.mapper = mapper;
        this.clock = clock;
    }

    public void relay(String matchId, String fromUserId, String toUserId, String type, Map<String, Object> payload) {
        if (!props.enabled()) throw ApiException.badRequest("VOICE_DISABLED", "Voice chat is turned off");
        if (fromUserId.equals(toUserId)) throw ApiException.badRequest("INVALID_PEER", "Cannot signal yourself");
        if (payloadChars(payload) > MAX_PAYLOAD_CHARS) throw ApiException.badRequest("SIGNAL_TOO_LARGE", "Signal too large");
        Match m = matches.findById(matchId).orElseThrow(() -> ApiException.notFound("MATCH_NOT_FOUND", "Match not found"));
        if (!m.playerIds.contains(fromUserId)) throw ApiException.forbidden("NOT_A_PLAYER", "You are not part of this match");
        if (!m.playerIds.contains(toUserId)) throw ApiException.forbidden("INVALID_PEER", "That player is not in this match");
        if (m.vsComputer) throw ApiException.badRequest("VOICE_UNAVAILABLE", "Voice chat is only available in online matches");
        // "leave" is always allowed so peers can hang up cleanly after the match ended
        if (m.status != MatchStatus.ACTIVE && !"leave".equals(type)) {
            throw ApiException.conflict("MATCH_FINISHED", "The match is over");
        }
        throttle(fromUserId);
        PlayerState p = m.game.player(fromUserId);
        messaging.convertAndSendToUser(toUserId, "/queue/voice",
                new VoiceSignalDto(matchId, fromUserId, p == null ? "Player" : p.name, type, payload == null ? Map.of() : payload));
    }

    /** STUN servers plus, when configured, a TURN relay with per-user credentials. */
    public List<IceServerDto> iceServers(String userId) {
        List<IceServerDto> out = new ArrayList<>();
        List<String> stun = VoiceProperties.split(props.stunUrls());
        if (!stun.isEmpty()) out.add(new IceServerDto(stun, null, null));
        List<String> turn = VoiceProperties.split(props.turnUrls());
        if (turn.isEmpty()) return out;
        if (props.turnSecret() != null && !props.turnSecret().isBlank()) {
            // coturn "use-auth-secret" (TURN REST API): username = expiry:user, credential = base64(HMAC-SHA1(secret, username))
            long expiry = clock.getAsLong() / 1000 + props.turnTtlSeconds();
            String username = expiry + ":" + userId;
            out.add(new IceServerDto(turn, username, hmacSha1Base64(props.turnSecret(), username)));
        } else if (props.turnUsername() != null && !props.turnUsername().isBlank()) {
            out.add(new IceServerDto(turn, props.turnUsername(), props.turnCredential()));
        }
        return out;
    }

    static String hmacSha1Base64(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private int payloadChars(Map<String, Object> payload) {
        if (payload == null) return 0;
        try {
            return mapper.writeValueAsString(payload).length();
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("INVALID_SIGNAL", "Invalid signal");
        }
    }

    private void throttle(String userId) {
        long now = clock.getAsLong();
        Deque<Long> times = recent.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MS) times.pollFirst();
            if (times.size() >= BURST) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "VOICE_RATE_LIMITED", "Too many voice signals");
            }
            times.addLast(now);
        }
    }
}
