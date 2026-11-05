package com.ludoarena.controller;

import com.ludoarena.dto.Dtos.ActiveGameDto;
import com.ludoarena.dto.Dtos.ApiResponse;
import com.ludoarena.dto.Dtos.PageResponse;
import com.ludoarena.dto.Dtos.RoomDto;
import com.ludoarena.dto.Requests.CreateBotGame;
import com.ludoarena.dto.Requests.CreateRoom;
import com.ludoarena.security.AuthUser;
import com.ludoarena.service.RoomService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {
    private final RoomService rooms;

    public RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoomDto> create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateRoom req) {
        return ApiResponse.ok(rooms.create(me.id(), req), "Room created");
    }

    /** "Play with Computer": body {"players": 2|4, "difficulty": "EASY"|"MEDIUM"|"HARD"}; the match starts immediately. */
    @PostMapping("/bot")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ActiveGameDto> createBotGame(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateBotGame req) {
        return ApiResponse.ok(rooms.createBotGame(me.id(), req), "Match against the computer started");
    }

    @GetMapping
    public ApiResponse<PageResponse<RoomDto>> list(@RequestParam(required = false) Integer maxPlayers,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(rooms.listPublic(maxPlayers, page, size));
    }

    /** The room/match the caller is currently in, or null (data: null) when none. */
    @GetMapping("/active")
    public ApiResponse<ActiveGameDto> active(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(rooms.active(me.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<RoomDto> get(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
        return ApiResponse.ok(rooms.get(me.id(), id));
    }

    /** {id} may be the room id or its 6-character join code. */
    @PostMapping("/{id}/join")
    public ApiResponse<RoomDto> join(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
        return ApiResponse.ok(rooms.join(me.id(), id), "Joined room");
    }

    @PostMapping("/{id}/leave")
    public ApiResponse<Void> leave(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
        rooms.leave(me.id(), id);
        return ApiResponse.ok(null, "Left room");
    }

    @PostMapping("/{id}/ready")
    public ApiResponse<RoomDto> ready(@AuthenticationPrincipal AuthUser me, @PathVariable String id,
                                      @RequestBody Map<String, Boolean> body) {
        return ApiResponse.ok(rooms.setReady(me.id(), id, Boolean.TRUE.equals(body.get("ready"))));
    }
}
