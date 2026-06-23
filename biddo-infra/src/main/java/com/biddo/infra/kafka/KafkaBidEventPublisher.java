package com.biddo.infra.kafka;

import com.biddo.domain.bid.model.Bid;
import com.biddo.domain.bid.port.out.BidEventPublisher;
import com.biddo.infra.kafka.event.BidEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaBidEventPublisher implements BidEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishBidPlaced(Bid bid) {
        BidEvent event = BidEvent.builder()
                .eventType(BidEvent.BID_PLACED)
                .auctionId(bid.getAuction().getId())
                .bidId(bid.getId())
                .bidderId(bid.getBidder().getId())
                .bidAmount(bid.getBidAmount())
                .bidType(bid.getBidType().name())
                .occurredAt(LocalDateTime.now())
                .build();

        String key = String.valueOf(bid.getAuction().getId());
        Runnable sendAction = () -> kafkaTemplate.send(KafkaConfig.BID_EVENTS, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish BID_PLACED event: auctionId={}, bidId={}",
                                event.getAuctionId(), event.getBidId(), ex);
                    } else {
                        log.debug("Published BID_PLACED event: auctionId={}, bidId={}, partition={}",
                                event.getAuctionId(), event.getBidId(),
                                result.getRecordMetadata().partition());
                    }
                });

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendAction.run();
                }
            });
        } else {
            sendAction.run();
        }
    }
}