package com.biddo.infra.kafka.consumer;

import com.biddo.infra.kafka.KafkaConfig;
import com.biddo.infra.kafka.event.BidEvent;
import com.biddo.infra.redis.PopularAuctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopularAuctionConsumer {

    private final PopularAuctionRepository popularAuctionRepository;

    @KafkaListener(topics = KafkaConfig.BID_EVENTS, groupId = "popular-auction")
    public void handleBidEvent(BidEvent event) {
        if (!BidEvent.BID_PLACED.equals(event.getEventType())) {
            return;
        }

        log.debug("Incrementing popularity for auctionId={}", event.getAuctionId());
        popularAuctionRepository.incrementScore(event.getAuctionId());
    }
}