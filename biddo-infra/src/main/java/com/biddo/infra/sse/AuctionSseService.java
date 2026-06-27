package com.biddo.infra.sse;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Service
public class AuctionSseService {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30분

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long auctionId, LocalDateTime endTime) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        Set<SseEmitter> auctionEmitters = emitters.computeIfAbsent(auctionId, k -> new CopyOnWriteArraySet<>());
        auctionEmitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(auctionId, emitter));
        emitter.onTimeout(() -> removeEmitter(auctionId, emitter));
        emitter.onError(e -> removeEmitter(auctionId, emitter));

        // 초기 시간 동기화 이벤트 전송
        sendTimeSync(emitter, auctionId, endTime);

        return emitter;
    }

    public void broadcastCountdownExtended(Long auctionId, LocalDateTime newEndTime) {
        Set<SseEmitter> auctionEmitters = emitters.get(auctionId);
        if (auctionEmitters == null || auctionEmitters.isEmpty()) {
            return;
        }

        Map<String, Object> data = Map.of(
                "type", "COUNTDOWN_EXTENDED",
                "auctionId", auctionId,
                "endTime", newEndTime.toString(),
                "serverTime", LocalDateTime.now().toString()
        );

        broadcast(auctionId, auctionEmitters, "countdown", data);
    }

    public void broadcastAuctionEnded(Long auctionId) {
        Set<SseEmitter> auctionEmitters = emitters.get(auctionId);
        if (auctionEmitters == null || auctionEmitters.isEmpty()) {
            return;
        }

        Map<String, Object> data = Map.of(
                "type", "AUCTION_ENDED",
                "auctionId", auctionId,
                "serverTime", LocalDateTime.now().toString()
        );

        broadcast(auctionId, auctionEmitters, "countdown", data);

        // 경매 종료 → 모든 emitter 완료 처리
        auctionEmitters.forEach(SseEmitter::complete);
        emitters.remove(auctionId);
    }

    private void sendTimeSync(SseEmitter emitter, Long auctionId, LocalDateTime endTime) {
        try {
            Map<String, Object> data = Map.of(
                    "type", "TIME_SYNC",
                    "auctionId", auctionId,
                    "endTime", endTime.toString(),
                    "serverTime", LocalDateTime.now().toString()
            );
            emitter.send(SseEmitter.event().name("countdown").data(data));
        } catch (IOException e) {
            log.warn("Failed to send initial time sync: auctionId={}", auctionId);
            emitter.completeWithError(e);
        }
    }

    private void broadcast(Long auctionId, Set<SseEmitter> auctionEmitters, String eventName, Object data) {
        for (SseEmitter emitter : auctionEmitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                log.debug("Failed to send SSE event, removing emitter: auctionId={}", auctionId);
                removeEmitter(auctionId, emitter);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        emitters.values().forEach(set -> set.forEach(SseEmitter::complete));
        emitters.clear();
        log.info("AuctionSseService: all emitters completed on shutdown");
    }

    private void removeEmitter(Long auctionId, SseEmitter emitter) {
        Set<SseEmitter> auctionEmitters = emitters.get(auctionId);
        if (auctionEmitters != null) {
            auctionEmitters.remove(emitter);
            if (auctionEmitters.isEmpty()) {
                emitters.remove(auctionId);
            }
        }
    }
}