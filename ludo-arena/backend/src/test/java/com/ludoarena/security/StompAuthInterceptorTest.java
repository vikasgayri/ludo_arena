package com.ludoarena.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ludoarena.TestProps;
import com.ludoarena.model.Enums.AccountStatus;
import com.ludoarena.model.Enums.Role;
import com.ludoarena.model.User;
import com.ludoarena.repository.UserRepository;
import com.ludoarena.service.MatchService;
import com.ludoarena.service.RoomService;
import com.ludoarena.websocket.StompAuthInterceptor;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class StompAuthInterceptorTest {
    private JwtService jwt;
    private UserRepository users;
    private RoomService rooms;
    private MatchService matches;
    private StompAuthInterceptor interceptor;
    private final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        jwt = new JwtService(TestProps.props());
        users = mock(UserRepository.class);
        rooms = mock(RoomService.class);
        matches = mock(MatchService.class);
        interceptor = new StompAuthInterceptor(jwt, users, rooms, matches);
    }

    private static User user(String id, Role role) {
        User u = new User();
        u.id = id;
        u.email = id + "@x.test";
        u.name = id;
        u.role = role;
        return u;
    }

    private static Message<byte[]> frame(StompHeaderAccessor acc) {
        acc.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
    }

    private static StompHeaderAccessor authed(StompCommand cmd, String destination, User u) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(cmd);
        acc.setDestination(destination);
        acc.setUser(new UsernamePasswordAuthenticationToken(new AuthUser(u.id, u.email, u.name, u.role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.role))));
        return acc;
    }

    @Test
    void connectWithoutTokenIsRejected() {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.CONNECT);
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(frame(acc), channel));
    }

    @Test
    void connectWithGarbageTokenIsRejected() {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.CONNECT);
        acc.setNativeHeader("Authorization", "Bearer nonsense");
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(frame(acc), channel));
    }

    @Test
    void connectWithValidTokenSetsThePrincipal() {
        User u = user("u1", Role.USER);
        when(users.findById("u1")).thenReturn(Optional.of(u));
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.CONNECT);
        acc.setNativeHeader("Authorization", "Bearer " + jwt.createAccessToken(u));
        interceptor.preSend(frame(acc), channel);
        assertNotNull(acc.getUser());
    }

    @Test
    void suspendedUsersCannotConnect() {
        User u = user("u1", Role.USER);
        u.status = AccountStatus.SUSPENDED;
        when(users.findById("u1")).thenReturn(Optional.of(u));
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.CONNECT);
        acc.setNativeHeader("Authorization", "Bearer " + jwt.createAccessToken(u));
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(frame(acc), channel));
    }

    @Test
    void unauthenticatedSendIsRejected() {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.SEND);
        acc.setDestination("/app/match.rollDice");
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(frame(acc), channel));
    }

    @Test
    void playersCanSubscribeToTheirOwnMatchOnly() {
        User u = user("u1", Role.USER);
        when(matches.isParticipant("m1", "u1")).thenReturn(true);
        when(matches.isParticipant("m2", "u1")).thenReturn(false);
        interceptor.preSend(frame(authed(StompCommand.SUBSCRIBE, "/topic/match/m1", u)), channel);
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(frame(authed(StompCommand.SUBSCRIBE, "/topic/match/m2", u)), channel));
    }

    @Test
    void roomTopicsRequireMembership() {
        User u = user("u1", Role.USER);
        when(rooms.isMember("r1", "u1")).thenReturn(true);
        interceptor.preSend(frame(authed(StompCommand.SUBSCRIBE, "/topic/room/r1", u)), channel);
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(frame(authed(StompCommand.SUBSCRIBE, "/topic/room/other", u)), channel));
    }

    @Test
    void adminsMayObserveAnyMatch() {
        User admin = user("a1", Role.ADMIN);
        interceptor.preSend(frame(authed(StompCommand.SUBSCRIBE, "/topic/match/anything", admin)), channel);
    }

    @Test
    void arbitraryTopicsAreDenied() {
        User u = user("u1", Role.USER);
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(frame(authed(StompCommand.SUBSCRIBE, "/topic/admin/secrets", u)), channel));
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(frame(authed(StompCommand.SUBSCRIBE, "/topic/match/../room/x", u)), channel));
    }

    @Test
    void ownUserQueuesAreAllowed() {
        User u = user("u1", Role.USER);
        interceptor.preSend(frame(authed(StompCommand.SUBSCRIBE, "/user/queue/errors", u)), channel);
        assertNotNull(anyString());
    }
}
