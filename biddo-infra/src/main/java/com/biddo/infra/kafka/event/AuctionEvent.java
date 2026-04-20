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
public class AuctionEvent {

    private String eventType;
    private Long auctionId;
    private Long sellerId;
    private String status;
    private Long currentPrice;
    private Long winnerId;
    private LocalDateTime occurredAt;

    public static final String AUCTION_CREATED = "AUCTION_CREATED";
    public static final String AUCTION_UPDATED = "AUCTION_UPDATED";
    public static final String AUCTION_CANCELLED = "AUCTION_CANCELLED";
    public static final String AUCTION_ACTIVATED = "AUCTION_ACTIVATED";
    public static final String AUCTION_ENDED = "AUCTION_ENDED";
    public static final String AUCTION_SOLD = "AUCTION_SOLD";
}