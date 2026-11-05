package com.ludoarena.websocket;

import com.ludoarena.model.Enums.AccountStatus;
import com.ludoarena.model.User;
import com.ludoarena.repository.UserRepository;
import com.ludoarena.security.AuthUser;
import com.ludoarena.security.JwtService;
import com.ludoarena.service.MatchService;
import com.ludoarena.service.RoomService;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP CONNECT frame with the same JWT as the REST API and authorises every SUBSCRIBE:
 * a user can only listen to rooms they are in and matches they play (admins may observe).
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {
    private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/room/([A-Za-z0-9]+)$");
    private static final Pattern MATCH_TOPIC = Pattern.compile("^/topic/match/([A-Za-z0-9]+)(/chat)?$");

    private final JwtService jwt;
    private final UserRepository users;
    private final RoomService rooms;
    private final MatchService matches;

    public StompAuthInterceptor(JwtService jwt, UserRepository users, RoomService rooms, MatchService matches) {
        this.jwt = jwt;
        this.users = users;
        this.rooms = rooms;
        this.matches = matches;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acc == null || acc.getCommand() == null) return message;
        StompCommand cmd = acc.getCommand();

        if (StompCommand.CONNECT.equals(cmd)) {
            String header = acc.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new MessageDeliveryException("Missing credentials");
            }
            Optional<String> id = jwt.validateAndGetUserId(header.substring(7).trim());
            User u = id.flatMap(users::findById).orElse(null);
            if (u == null || u.status == AccountStatus.SUSPENDED) {
                throw new MessageDeliveryException("Invalid or expired credentials");
            }
            AuthUser principal = new AuthUser(u.id, u.email, u.name, u.role);
            Authentication auth = new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + u.role.name())));
            acc.setUser(auth);
            return message;
        }

        if (StompCommand.SEND.equals(cmd) || StompCommand.SUBSCRIBE.equals(cmd)) {
            AuthUser me = currentUser(acc);
            if (me == null) throw new MessageDeliveryException("Not authenticated");
            if (StompCommand.SUBSCRIBE.equals(cmd)) authorizeSubscription(me, acc.getDestination());
        }
        return message;
    }

    private static AuthUser currentUser(StompHeaderAccessor acc) {
        if (acc.getUser() instanceof Authentication a && a.getPrincipal() instanceof AuthUser u) return u;
        return null;
    }

    private void authorizeSubscription(AuthUser me, String destination) {
        if (destination == null) throw new MessageDeliveryException("Missing destination");
        if (destination.startsWith("/user/queue/")) return; // user destinations are resolved per authenticated session
        Matcher room = ROOM_TOPIC.matcher(destination);
        if (room.matches()) {
            if (me.isAdmin() || rooms.isMember(room.group(1), me.id())) return;
            throw new MessageDeliveryException("You are not a member of this room");
        }
        Matcher match = MATCH_TOPIC.matcher(destination);
        if (match.matches()) {
            if (me.isAdmin() || matches.isParticipant(match.group(1), me.id())) return;
            throw new MessageDeliveryException("You are not a player in this match");
        }
        throw new MessageDeliveryException("Subscription not allowed");
    }
}
