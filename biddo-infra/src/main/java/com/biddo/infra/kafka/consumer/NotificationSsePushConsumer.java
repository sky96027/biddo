package com.biddo.infra.kafka.consumer;

import com.biddo.infra.kafka.KafkaConfig;
import com.biddo.infra.kafka.event.NotificationPushEvent;
import com.biddo.infra.sse.NotificationSseAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSsePushConsumer {

    private final NotificationSseAdapter notificationSseAdapter;

    @KafkaListener(topics = KafkaConfig.NOTIFICATION_EVENTS,
            groupId = "#{T(java.util.UUID).randomUUID().toString()}")
    public void handlePushEvent(NotificationPushEvent event) {
        notificationSseAdapter.pushDirect(event.getReceiverId(), event);
        log.debug("SSE push attempted: receiverId={}, notificationId={}", event.getReceiverId(), event.getNotificationId());
    }
}