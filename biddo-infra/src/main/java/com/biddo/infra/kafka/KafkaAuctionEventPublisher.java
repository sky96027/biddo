package com.biddo.infra.kafka;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.port.out.AuctionEventPublisher;
import com.biddo.infra.kafka.event.AuctionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaAuctionEventPublisher implements AuctionEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishAuctionCreated(Auction auction) {
        publish(AuctionEvent.AUCTION_CREATED, auction);
    }

    @Override
    public void publishAuctionUpdated(Auction auction) {
        publish(AuctionEvent.AUCTION_UPDATED, auction);
    }

    @Override
    public void publishAuctionCancelled(Auction auction) {
        publish(AuctionEvent.AUCTION_CANCELLED, auction);
    }

    @Override
    public void publishAuctionSold(Auction auction) {
        publish(AuctionEvent.AUCTION_SOLD, auction);
    }

    private void publish(String eventType, Auction auction) {
        AuctionEvent event = AuctionEvent.builder()
                .eventType(eventType)
                .auctionId(auction.getId())
                .sellerId(auction.getSeller().getId())
                .status(auction.getStatus().name())
                .currentPrice(auction.getCurrentPrice())
                .winnerId(auction.getWinner() != null ? auction.getWinner().getId() : null)
                .occurredAt(LocalDateTime.now())
                .build();

        String key = String.valueOf(auction.getId());
        kafkaTemplate.send(KafkaConfig.AUCTION_EVENTS, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} event: auctionId={}",
                                eventType, event.getAuctionId(), ex);
                    } else {
                        log.debug("Published {} event: auctionId={}, partition={}",
                                eventType, event.getAuctionId(),
                                result.getRecordMetadata().partition());
                    }
                });
    }
}