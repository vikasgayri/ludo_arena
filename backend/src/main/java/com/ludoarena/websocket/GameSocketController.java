package com.ludoarena.websocket;

import com.ludoarena.dto.Dtos.RoomDto;
import com.ludoarena.dto.Dtos.StateSnapshot;
import com.ludoarena.dto.Requests.MoveToken;
import com.ludoarena.dto.Requests.RequestState;
import com.ludoarena.dto.Requests.SendChat;
import com.ludoarena.dto.Requests.RollDice;
import com.ludoarena.dto.Requests.RoomReady;
import com.ludoarena.dto.Requests.RoomRef;
import com.ludoarena.exception.ApiException;
import com.ludoarena.game.model.EngineException;
import com.ludoarena.service.ChatService;
import com.ludoarena.service.MatchService;
import com.ludoarena.service.RoomService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.stereotype.Controller;

/**
 * Thin STOMP adapter. The authenticated user id always comes from the session principal - never from the payload -
 * and every call is re-validated by the services against current server-side state.
 */
@Controller
public class GameSocketController {
    private static final Logger log = LoggerFactory.getLogger(GameSocketController.class);

    public record SocketError(String code, String message, Instant at) {
    }

    private final RoomService rooms;
    private final MatchService matches;
    private final SimpMessagingTemplate messaging;
    private final ChatService chat;

    public GameSocketController(RoomService rooms, MatchService matches, SimpMessagingTemplate messaging, ChatService chat) {
        this.rooms = rooms;
        this.matches = matches;
        this.messaging = messaging;
        this.chat = chat;
    }

    @MessageMapping("/room.join")
    public void join(@Payload @Valid RoomRef p, Principal principal) {
        RoomDto room = rooms.join(principal.getName(), p.roomId());
        messaging.convertAndSendToUser(principal.getName(), "/queue/state", room);
    }

    @MessageMapping("/room.leave")
    public void leave(@Payload @Valid RoomRef p, Principal principal) {
        rooms.leave(principal.getName(), p.roomId());
    }

    @MessageMapping("/room.ready")
    public void ready(@Payload @Valid RoomReady p, Principal principal) {
        rooms.setReady(principal.getName(), p.roomId(), p.ready());
    }

    @MessageMapping("/match.rollDice")
    public void roll(@Payload @Valid RollDice p, Principal principal) {
        matches.roll(p.matchId(), principal.getName(), p.actionId());
    }

    @MessageMapping("/match.moveToken")
    public void move(@Payload @Valid MoveToken p, Principal principal) {
        matches.move(p.matchId(), principal.getName(), p.tokenIndex(), p.actionId());
    }

    @MessageMapping("/match.requestState")
    public void requestState(@Payload @Valid RequestState p, Principal principal) {
        StateSnapshot snap = matches.requestState(p.matchId(), principal.getName(), p.reconnectToken());
        messaging.convertAndSendToUser(principal.getName(), "/queue/state", snap);
    }

    /** In-match chat; the sender is the session principal, never a field of the payload. */
    @MessageMapping("/match.chat")
    public void chat(@Payload @Valid SendChat p, Principal principal) {
        chat.send(p.matchId(), principal.getName(), p.text());
    }

    // ---------------------------------------------------------------- error replies

    @MessageExceptionHandler(EngineException.class)
    @SendToUser("/queue/errors")
    public SocketError onEngine(EngineException e) {
        return new SocketError(e.code(), e.getMessage(), Instant.now());
    }

    @MessageExceptionHandler(ApiException.class)
    @SendToUser("/queue/errors")
    public SocketError onApi(ApiException e) {
        return new SocketError(e.code(), e.getMessage(), Instant.now());
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser("/queue/errors")
    public SocketError onInvalid(MethodArgumentNotValidException e) {
        return new SocketError("VALIDATION_FAILED", "Invalid message", Instant.now());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public SocketError onOther(Exception e) {
        log.error("Unhandled WebSocket error", e);
        return new SocketError("INTERNAL_ERROR", "Something went wrong", Instant.now());
    }
}
