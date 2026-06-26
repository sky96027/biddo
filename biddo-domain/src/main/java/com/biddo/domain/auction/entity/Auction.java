package com.biddo.domain.auction.entity;

import com.biddo.domain.auction.exception.AuctionErrorCode;
import com.biddo.domain.category.entity.Category;
import com.biddo.domain.common.entity.BaseModifiedTimeEntity;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "auction", indexes = {
        @Index(name = "idx_auction_status_end_time", columnList = "status, end_time"),
        @Index(name = "idx_auction_status_start_time", columnList = "status, start_time"),
        @Index(name = "idx_auction_seller", columnList = "seller_id"),
        @Index(name = "idx_auction_category", columnList = "category_id"),
        @Index(name = "idx_auction_winner", columnList = "winner_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auction extends BaseModifiedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auction_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Member seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", nullable = false, length = 20)
    private ItemCondition condition;

    @Column(nullable = false)
    private Long startingPrice;

    @Column(nullable = false)
    private Long currentPrice;

    private Long buyNowPrice;

    @Column(nullable = false)
    private int bidCount;

    @Column(nullable = false)
    private int viewCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuctionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private Member winner;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @OneToMany(mappedBy = "auction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<AuctionImage> images = new ArrayList<>();

    @Builder
    public Auction(Member seller, Category category, String title, String description,
                   ItemCondition condition, Long startingPrice, Long buyNowPrice,
                   LocalDateTime startTime, LocalDateTime endTime) {
        validateStartingPrice(startingPrice);
        validateBuyNowPrice(buyNowPrice, startingPrice);
        this.seller = seller;
        this.category = category;
        this.title = title;
        this.description = description;
        this.condition = condition;
        this.startingPrice = startingPrice;
        this.currentPrice = startingPrice;
        this.buyNowPrice = buyNowPrice;
        this.bidCount = 0;
        this.viewCount = 0;
        this.status = AuctionStatus.PENDING;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void update(Category category, String title, String description,
                       ItemCondition condition, Long startingPrice, Long buyNowPrice,
                       LocalDateTime endTime) {
        validateStartingPrice(startingPrice);
        validateBuyNowPrice(buyNowPrice, startingPrice);
        this.category = category;
        this.title = title;
        this.description = description;
        this.condition = condition;
        this.startingPrice = startingPrice;
        this.currentPrice = startingPrice;
        this.buyNowPrice = buyNowPrice;
        this.endTime = endTime;
    }

    public void cancel() {
        this.status = AuctionStatus.CANCELLED;
    }

    public boolean isSeller(Long memberId) {
        return this.seller.getId().equals(memberId);
    }

    public boolean isPending() {
        return this.status == AuctionStatus.PENDING;
    }

    public void updateImages(List<AuctionImage> newImages) {
        if (newImages == null || newImages.isEmpty() || newImages.size() > 10) {
            throw new BusinessException(AuctionErrorCode.INVALID_IMAGE_COUNT);
        }
        this.images.clear();
        this.images.addAll(newImages);
    }

    public void applyBid(Long bidAmount, Member bidder) {
        this.currentPrice = bidAmount;
        this.bidCount++;
        this.winner = bidder;
    }

    public void sell(Member buyer) {
        this.status = AuctionStatus.SOLD;
        this.winner = buyer;
        this.currentPrice = this.buyNowPrice;
    }

    public void activate() {
        if (this.status != AuctionStatus.PENDING) {
            throw new BusinessException(AuctionErrorCode.AUCTION_NOT_PENDING);
        }
        this.status = AuctionStatus.ACTIVE;
    }

    public void end() {
        this.status = AuctionStatus.ENDED;
    }

    public void extendEndTime(LocalDateTime newEndTime) {
        this.endTime = newEndTime;
    }

    public boolean isActive() {
        return this.status == AuctionStatus.ACTIVE;
    }

    private void validateStartingPrice(Long startingPrice) {
        if (startingPrice < 1000) {
            throw new BusinessException(AuctionErrorCode.INVALID_STARTING_PRICE);
        }
    }

    private void validateBuyNowPrice(Long buyNowPrice, Long startingPrice) {
        if (buyNowPrice != null && buyNowPrice <= startingPrice) {
            throw new BusinessException(AuctionErrorCode.INVALID_BUY_NOW_PRICE);
        }
    }
}
