package com.biddo.infra.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPushEvent {

    private Long receiverId;
    private Long notificationId;
    private Long auctionId;
    private String type;
    private String message;
    private LocalDateTime createdAt;
}