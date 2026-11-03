package com.ludoarena.controller;

import com.ludoarena.dto.Dtos.AchievementDto;
import com.ludoarena.dto.Dtos.ApiResponse;
import com.ludoarena.dto.Dtos.PublicProfileDto;
import com.ludoarena.dto.Dtos.UserDto;
import com.ludoarena.dto.Requests.ChangePassword;
import com.ludoarena.dto.Requests.UpdateProfile;
import com.ludoarena.security.AuthUser;
import com.ludoarena.service.AuthService;
import com.ludoarena.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService users;
    private final AuthService auth;

    public UserController(UserService users, AuthService auth) {
        this.users = users;
        this.auth = auth;
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(users.me(me.id()));
    }

    @PutMapping("/me")
    public ApiResponse<UserDto> update(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateProfile req) {
        return ApiResponse.ok(users.update(me.id(), req), "Profile updated");
    }

    @PostMapping("/me/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChangePassword req) {
        auth.changePassword(me.id(), req);
        return ApiResponse.ok(null, "Password changed");
    }

    @GetMapping("/me/achievements")
    public ApiResponse<List<AchievementDto>> achievements(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(users.achievements(me.id()));
    }

    @GetMapping("/{id}/profile")
    public ApiResponse<PublicProfileDto> profile(@PathVariable String id) {
        return ApiResponse.ok(users.profile(id));
    }
}
