package com.biddo.domain.bid.entity;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.common.entity.BaseModifiedTimeEntity;
import com.biddo.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "auto_bid", uniqueConstraints = {
        @UniqueConstraint(name = "uq_auto_bid", columnNames = {"auction_id", "bidder_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AutoBid extends BaseModifiedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auto_bid_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bidder_id", nullable = false)
    private Member bidder;

    @Column(nullable = false)
    private Long maxAmount;

    @Column(nullable = false)
    private boolean isActive;

    @Builder
    public AutoBid(Auction auction, Member bidder, Long maxAmount) {
        this.auction = auction;
        this.bidder = bidder;
        this.maxAmount = maxAmount;
        this.isActive = true;
    }

    public void updateMaxAmount(Long maxAmount) {
        this.maxAmount = maxAmount;
    }

    public void deactivate() {
        this.isActive = false;
    }
}