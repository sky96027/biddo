package com.biddo.infra.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionWebSocketMessage {

    private String type;
    private Long auctionId;
    private Long bidId;
    private Long bidderId;
    private String bidderNickname;
    private Long bidAmount;
    private Long currentPrice;
    private int bidCount;
    private String bidType;
    private LocalDateTime endTime;
    private LocalDateTime occurredAt;

    public static final String NEW_BID = "NEW_BID";
    public static final String AUCTION_ENDED = "AUCTION_ENDED";
    public static final String AUCTION_SOLD = "AUCTION_SOLD";
    public static final String COUNTDOWN_EXTENDED = "COUNTDOWN_EXTENDED";
}