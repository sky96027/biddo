package com.biddo.infra.kafka.consumer;

import com.biddo.infra.kafka.KafkaConfig;
import com.biddo.infra.kafka.event.AuctionEvent;
import com.biddo.infra.sse.AuctionSseService;
import com.biddo.infra.websocket.dto.AuctionWebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionWebSocketConsumer {

    private final SimpMessagingTemplate messagingTemplate;
    private final AuctionSseService auctionSseService;

    @KafkaListener(topics = KafkaConfig.AUCTION_EVENTS, groupId = "biddo-websocket")
    public void handleAuctionEvent(AuctionEvent event) {
        String type = switch (event.getEventType()) {
            case AuctionEvent.AUCTION_ENDED -> AuctionWebSocketMessage.AUCTION_ENDED;
            case AuctionEvent.AUCTION_SOLD -> AuctionWebSocketMessage.AUCTION_SOLD;
            default -> null;
        };

        if (type == null) {
            return;
        }

        AuctionWebSocketMessage message = AuctionWebSocketMessage.builder()
                .type(type)
                .auctionId(event.getAuctionId())
                .currentPrice(event.getCurrentPrice())
                .occurredAt(event.getOccurredAt())
                .build();

        String destination = "/topic/auction/" + event.getAuctionId();
        messagingTemplate.convertAndSend(destination, message);

        // SSE 카운트다운 종료 알림
        auctionSseService.broadcastAuctionEnded(event.getAuctionId());

        log.debug("WebSocket broadcast to {}: type={}", destination, type);
    }
}