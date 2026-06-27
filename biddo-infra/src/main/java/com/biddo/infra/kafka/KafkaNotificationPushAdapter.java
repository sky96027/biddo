package com.biddo.infra.kafka;

import com.biddo.domain.notification.entity.Notification;
import com.biddo.domain.notification.port.out.NotificationPushPort;
import com.biddo.infra.kafka.event.NotificationPushEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaNotificationPushAdapter implements NotificationPushPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void push(Long receiverId, Notification notification) {
        NotificationPushEvent event = NotificationPushEvent.builder()
                .receiverId(receiverId)
                .notificationId(notification.getId())
                .auctionId(notification.getAuctionId())
                .type(notification.getType().name())
                .message(notification.getMessage())
                .createdAt(notification.getCreatedAt())
                .build();

        kafkaTemplate.send(KafkaConfig.NOTIFICATION_EVENTS, String.valueOf(receiverId), event);
        log.debug("Notification push event published: receiverId={}, notificationId={}", receiverId, notification.getId());
    }
}