package com.biddo.infra.kafka.consumer;

import com.biddo.infra.kafka.KafkaConfig;
import com.biddo.infra.kafka.event.AuctionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuctionEventConsumer {

    @KafkaListener(topics = KafkaConfig.AUCTION_EVENTS, groupId = "biddo-auction")
    public void handleAuctionEvent(AuctionEvent event) {
        log.info("Received {}: auctionId={}, sellerId={}, status={}, currentPrice={}",
                event.getEventType(), event.getAuctionId(), event.getSellerId(),
                event.getStatus(), event.getCurrentPrice());

        // TODO: Notification 발행 (경매 종료/낙찰 알림)
        // TODO: Elasticsearch 동기화
    }
}