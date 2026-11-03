package com.ludoarena.websocket;

import com.ludoarena.service.MatchService;
import java.security.Principal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** Tracks open STOMP sessions per user so a dropped connection marks the player disconnected in their running match. */
@Component
public class SessionTracker {
    private static final Logger log = LoggerFactory.getLogger(SessionTracker.class);

    private final ConcurrentHashMap<String, Set<String>> sessionsByUser = new ConcurrentHashMap<>();
    private final MatchService matches;

    public SessionTracker(MatchService matches) {
        this.matches = matches;
    }

    public int onlineUsers() {
        return sessionsByUser.size();
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (user == null || sessionId == null) return;
        Set<String> sessions = sessionsByUser.computeIfAbsent(user.getName(), k -> ConcurrentHashMap.newKeySet());
        boolean first = sessions.isEmpty();
        sessions.add(sessionId);
        if (first) safely(() -> matches.onUserConnected(user.getName()));
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user == null) return;
        String userId = user.getName();
        Set<String> sessions = sessionsByUser.get(userId);
        if (sessions == null) return;
        sessions.remove(event.getSessionId());
        if (sessions.isEmpty()) {
            sessionsByUser.remove(userId, sessions);
            safely(() -> matches.onUserDisconnected(userId));
        }
    }

    private void safely(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            log.warn("Presence update failed: {}", e.getMessage());
        }
    }
}
