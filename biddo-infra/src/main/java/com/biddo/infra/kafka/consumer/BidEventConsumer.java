package com.biddo.infra.kafka.consumer;

import com.biddo.infra.kafka.KafkaConfig;
import com.biddo.infra.kafka.event.BidEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BidEventConsumer {

    @KafkaListener(topics = KafkaConfig.BID_EVENTS, groupId = "biddo-bid")
    public void handleBidEvent(BidEvent event) {
        log.info("Received {}: auctionId={}, bidId={}, bidderId={}, amount={}, type={}",
                event.getEventType(), event.getAuctionId(), event.getBidId(),
                event.getBidderId(), event.getBidAmount(), event.getBidType());

        // TODO: WebSocket broadcast (실시간 입찰 업데이트)
        // TODO: Notification 발행 (입찰 알림, 추월 알림)
        // TODO: Elasticsearch 동기화
    }
}