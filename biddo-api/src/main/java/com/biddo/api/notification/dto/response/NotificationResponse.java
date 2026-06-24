package com.biddo.api.notification.dto.response;

import com.biddo.domain.notification.model.Notification;
import com.biddo.domain.notification.model.NotificationType;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class NotificationResponse {

    private final Long notificationId;
    private final Long auctionId;
    private final NotificationType type;
    private final String message;
    private final boolean isRead;
    private final LocalDateTime createdAt;

    private NotificationResponse(Long notificationId, Long auctionId, NotificationType type,
                                  String message, boolean isRead, LocalDateTime createdAt) {
        this.notificationId = notificationId;
        this.auctionId = auctionId;
        this.type = type;
        this.message = message;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getAuctionId(),
                notification.getType(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}