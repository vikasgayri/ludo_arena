package com.ludoarena.controller;

import com.ludoarena.dto.Dtos.ApiResponse;
import com.ludoarena.dto.Dtos.EventDto;
import com.ludoarena.dto.Dtos.MatchStateDto;
import com.ludoarena.dto.Dtos.MatchSummaryDto;
import com.ludoarena.dto.Dtos.PageResponse;
import com.ludoarena.model.Enums.MatchStatus;
import com.ludoarena.security.AuthUser;
import com.ludoarena.service.MatchService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {
    private final MatchService matches;
    private final com.ludoarena.service.ChatService chat;

    public MatchController(MatchService matches, com.ludoarena.service.ChatService chat) {
        this.matches = matches;
        this.chat = chat;
    }

    /** The caller's own match history. */
    @GetMapping
    public ApiResponse<PageResponse<MatchSummaryDto>> mine(@AuthenticationPrincipal AuthUser me,
                                                           @RequestParam(required = false) MatchStatus status,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(matches.listForUser(me.id(), status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<MatchStateDto> get(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
        return ApiResponse.ok(matches.stateFor(id, me.id(), me.isAdmin()));
    }

    /** Last 50 chat messages of the match (participants / admins). */
    @GetMapping("/{id}/chat")
    public ApiResponse<java.util.List<com.ludoarena.dto.Dtos.ChatMessageDto>> chat(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
        return ApiResponse.ok(chat.history(id, me.id(), me.isAdmin()));
    }

    @GetMapping("/{id}/events")
    public ApiResponse<PageResponse<EventDto>> events(@AuthenticationPrincipal AuthUser me, @PathVariable String id,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "100") int size) {
        return ApiResponse.ok(matches.events(id, me.id(), me.isAdmin(), page, size));
    }
}
