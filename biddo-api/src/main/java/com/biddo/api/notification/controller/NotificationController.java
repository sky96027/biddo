package com.biddo.api.notification.controller;

import com.biddo.api.common.response.ApiResponse;
import com.biddo.api.common.response.CursorResponse;
import com.biddo.api.common.security.CustomUserDetails;
import com.biddo.api.notification.dto.response.NotificationResponse;
import com.biddo.domain.notification.entity.Notification;
import com.biddo.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final int DEFAULT_SIZE = 20;

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<CursorResponse<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        int fetchSize = Math.min(size, DEFAULT_SIZE);
        List<Notification> notifications = notificationService.findByReceiverId(
                userDetails.getMemberId(), isRead, cursor, fetchSize + 1);

        boolean hasNext = notifications.size() > fetchSize;
        List<Notification> content = hasNext ? notifications.subList(0, fetchSize) : notifications;
        List<NotificationResponse> responses = content.stream()
                .map(NotificationResponse::from).toList();

        String nextCursor = hasNext
                ? String.valueOf(content.get(content.size() - 1).getId())
                : null;

        return ApiResponse.success(CursorResponse.of(responses, nextCursor, hasNext));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long notificationId) {
        notificationService.markAsRead(notificationId, userDetails.getMemberId());
        return ApiResponse.success();
    }
}