package com.biddo.domain.bid.entity;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.common.entity.BaseCreatedTimeEntity;
import com.biddo.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bid", indexes = {
        @Index(name = "idx_bid_auction_amount", columnList = "auction_id, bid_amount DESC"),
        @Index(name = "idx_bid_bidder", columnList = "bidder_id"),
        @Index(name = "idx_bid_auction_created", columnList = "auction_id, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bid extends BaseCreatedTimeEntity {

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

    @Builder
    public Bid(Auction auction, Member bidder, Long bidAmount, BidType bidType) {
        this.auction = auction;
        this.bidder = bidder;
        this.bidAmount = bidAmount;
        this.bidType = bidType;
        this.isWinning = true;
    }

    public void loseWinning() {
        this.isWinning = false;
    }
}