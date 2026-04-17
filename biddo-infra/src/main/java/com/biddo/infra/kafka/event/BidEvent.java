package com.biddo.infra.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidEvent {

    private String eventType;
    private Long auctionId;
    private Long bidId;
    private Long bidderId;
    private Long bidAmount;
    private String bidType;
    private LocalDateTime occurredAt;

    public static final String BID_PLACED = "BID_PLACED";
}