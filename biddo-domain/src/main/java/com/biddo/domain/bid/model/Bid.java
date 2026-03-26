package com.biddo.domain.bid.model;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bid", indexes = {
        @Index(name = "idx_bid_auction_amount", columnList = "auction_id, bid_amount DESC"),
        @Index(name = "idx_bid_bidder", columnList = "bidder_id"),
        @Index(name = "idx_bid_auction_created", columnList = "auction_id, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bid_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bidder_id", nullable = false)
    private Member bidder;

    @Column(nullable = false)
    private Long bidAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BidType bidType;

    @Column(nullable = false)
    private boolean isWinning;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Bid(Auction auction, Member bidder, Long bidAmount, BidType bidType) {
        this.auction = auction;
        this.bidder = bidder;
        this.bidAmount = bidAmount;
        this.bidType = bidType;
        this.isWinning = true;
        this.createdAt = LocalDateTime.now();
    }

    public void loseWinning() {
        this.isWinning = false;
    }
}