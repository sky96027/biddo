package com.biddo.infra.sse;

import com.biddo.domain.notification.entity.Notification;
import com.biddo.infra.kafka.event.NotificationPushEvent;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class NotificationSseAdapter {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30분

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long memberId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        Set<SseEmitter> memberEmitters = emitters.computeIfAbsent(memberId, k -> new CopyOnWriteArraySet<>());
        memberEmitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(memberId, emitter));
        emitter.onTimeout(() -> removeEmitter(memberId, emitter));
        emitter.onError(e -> removeEmitter(memberId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connect").data("connected"));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE connect event: memberId={}", memberId);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    public void push(Long receiverId, Notification notification) {
        Set<SseEmitter> memberEmitters = emitters.get(receiverId);
        if (memberEmitters == null || memberEmitters.isEmpty()) {
            return;
        }

        Map<String, Object> data = Map.of(
                "notificationId", notification.getId(),
                "auctionId", notification.getAuctionId() != null ? notification.getAuctionId() : "",
                "type", notification.getType().name(),
                "message", notification.getMessage(),
                "createdAt", notification.getCreatedAt().toString()
        );

        sendToEmitters(receiverId, memberEmitters, String.valueOf(notification.getId()), data);
    }

    public void pushDirect(Long receiverId, NotificationPushEvent event) {
        Set<SseEmitter> memberEmitters = emitters.get(receiverId);
        if (memberEmitters == null || memberEmitters.isEmpty()) {
            return;
        }

        Map<String, Object> data = Map.of(
                "notificationId", event.getNotificationId(),
                "auctionId", event.getAuctionId() != null ? event.getAuctionId() : "",
                "type", event.getType(),
                "message", event.getMessage(),
                "createdAt", event.getCreatedAt().toString()
        );

        sendToEmitters(receiverId, memberEmitters, String.valueOf(event.getNotificationId()), data);
    }

    @PreDestroy
    public void shutdown() {
        emitters.values().forEach(set -> set.forEach(SseEmitter::complete));
        emitters.clear();
        log.info("NotificationSseAdapter: all emitters completed on shutdown");
    }

    private void sendToEmitters(Long memberId, Set<SseEmitter> memberEmitters, String eventId, Map<String, Object> data) {
        for (SseEmitter emitter : memberEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(eventId)
                        .name("notification")
                        .data(data));
            } catch (IOException e) {
                log.debug("Failed to send SSE notification, removing emitter: memberId={}", memberId);
                removeEmitter(memberId, emitter);
            }
        }
    }

    private void removeEmitter(Long memberId, SseEmitter emitter) {
        Set<SseEmitter> memberEmitters = emitters.get(memberId);
        if (memberEmitters != null) {
            memberEmitters.remove(emitter);
            if (memberEmitters.isEmpty()) {
                emitters.remove(memberId);
            }
        }
    }
}