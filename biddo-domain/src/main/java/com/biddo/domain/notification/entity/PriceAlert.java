package com.biddo.domain.notification.entity;

import com.biddo.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "price_alert", uniqueConstraints = {
        @UniqueConstraint(name = "uk_price_alert_member_auction", columnNames = {"member_id", "auction_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "price_alert_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "auction_id", nullable = false)
    private Long auctionId;

    @Column(nullable = false)
    private int thresholdPercent;

    @Column(nullable = false)
    private Long basePrice;

    @Column(nullable = false)
    private boolean isActive;

    @Builder
    public PriceAlert(Member member, Long auctionId, int thresholdPercent, Long basePrice) {
        this.member = member;
        this.auctionId = auctionId;
        this.thresholdPercent = thresholdPercent;
        this.basePrice = basePrice;
        this.isActive = true;
    }

    public void updateThreshold(int thresholdPercent) {
        this.thresholdPercent = thresholdPercent;
    }

    public void updateBasePrice(Long basePrice) {
        this.basePrice = basePrice;
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public boolean isOwner(Long memberId) {
        return this.member.getId().equals(memberId);
    }

    public boolean isTriggered(Long currentPrice) {
        if (!this.isActive) return false;
        if (currentPrice <= this.basePrice) return false;
        double increasePercent = ((double) (currentPrice - this.basePrice) / this.basePrice) * 100;
        return increasePercent >= this.thresholdPercent;
    }
}
