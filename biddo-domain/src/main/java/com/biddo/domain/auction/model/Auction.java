package com.biddo.domain.auction.model;

import com.biddo.domain.category.entity.Category;
import com.biddo.domain.common.entity.BaseTimeEntity;
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
        @Index(name = "idx_auction_seller", columnList = "seller_id"),
        @Index(name = "idx_auction_category", columnList = "category_id"),
        @Index(name = "idx_auction_winner", columnList = "winner_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auction extends BaseTimeEntity {

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
        this.images.clear();
        this.images.addAll(newImages);
    }
}
