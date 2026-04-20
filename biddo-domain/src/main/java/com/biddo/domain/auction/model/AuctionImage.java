package com.biddo.domain.auction.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "auction_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @Column(nullable = false, length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AuctionImage(Auction auction, String imageUrl, int sortOrder) {
        this.auction = auction;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
        this.createdAt = LocalDateTime.now();
    }
}