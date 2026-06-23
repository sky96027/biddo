package com.biddo.infra.sse;

import com.biddo.domain.notification.model.Notification;
import com.biddo.domain.notification.port.out.NotificationPushPort;
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
public class NotificationSseAdapter implements NotificationPushPort {

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

    @Override
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

        for (SseEmitter emitter : memberEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(notification.getId()))
                        .name("notification")
                        .data(data));
            } catch (IOException e) {
                log.debug("Failed to send SSE notification, removing emitter: memberId={}", receiverId);
                removeEmitter(receiverId, emitter);
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