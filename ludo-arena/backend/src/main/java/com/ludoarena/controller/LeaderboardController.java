package com.ludoarena.controller;

import com.ludoarena.dto.Dtos.ApiResponse;
import com.ludoarena.dto.Dtos.LeaderboardEntryDto;
import com.ludoarena.dto.Dtos.PageResponse;
import com.ludoarena.security.AuthUser;
import com.ludoarena.service.LeaderboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leaderboard")
public class LeaderboardController {
    private final LeaderboardService leaderboard;

    public LeaderboardController(LeaderboardService leaderboard) {
        this.leaderboard = leaderboard;
    }

    @GetMapping
    public ApiResponse<PageResponse<LeaderboardEntryDto>> top(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(leaderboard.top(page, size));
    }

    @GetMapping("/me")
    public ApiResponse<LeaderboardEntryDto> me(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(leaderboard.me(me.id()));
    }
}
