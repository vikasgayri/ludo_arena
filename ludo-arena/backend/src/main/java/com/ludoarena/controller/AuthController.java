package com.ludoarena.controller;

import com.ludoarena.dto.Dtos.ApiResponse;
import com.ludoarena.dto.Dtos.AuthResponse;
import com.ludoarena.dto.Dtos.UserDto;
import com.ludoarena.dto.Requests.ForgotPassword;
import com.ludoarena.dto.Requests.Login;
import com.ludoarena.dto.Requests.Logout;
import com.ludoarena.dto.Requests.Refresh;
import com.ludoarena.dto.Requests.Register;
import com.ludoarena.dto.Requests.ResetPassword;
import com.ludoarena.security.AuthUser;
import com.ludoarena.service.AuthService;
import com.ludoarena.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final UserService users;

    public AuthController(AuthService auth, UserService users) {
        this.auth = auth;
        this.users = users;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody Register req) {
        return ApiResponse.ok(auth.register(req), "Account created");
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody Login req) {
        return ApiResponse.ok(auth.login(req));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody Refresh req) {
        return ApiResponse.ok(auth.refresh(req.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) Logout req) {
        auth.logout(req == null ? null : req.refreshToken());
        return ApiResponse.ok(null, "Signed out");
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(users.me(me.id()));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgot(@Valid @RequestBody ForgotPassword req) {
        auth.forgotPassword(req.email());
        return ApiResponse.ok(null, "If that email is registered, a reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetPassword req) {
        auth.resetPassword(req);
        return ApiResponse.ok(null, "Password updated. You can sign in now.");
    }
}
