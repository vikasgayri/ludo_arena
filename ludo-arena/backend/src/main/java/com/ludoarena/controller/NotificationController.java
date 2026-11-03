package com.ludoarena.controller;

import com.ludoarena.dto.Dtos.ApiResponse;
import com.ludoarena.dto.Dtos.NotificationDto;
import com.ludoarena.dto.Dtos.PageResponse;
import com.ludoarena.dto.Dtos.UnreadCount;
import com.ludoarena.security.AuthUser;
import com.ludoarena.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationDto>> list(@AuthenticationPrincipal AuthUser me,
                                                           @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notifications.list(me.id(), unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCount> unread(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(notifications.unread(me.id()));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> read(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
        notifications.markRead(me.id(), id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/read-all")
    public ApiResponse<Long> readAll(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(notifications.markAllRead(me.id()));
    }
}
