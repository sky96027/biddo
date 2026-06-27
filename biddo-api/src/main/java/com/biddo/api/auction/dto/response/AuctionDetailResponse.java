package com.biddo.api.auction.dto.response;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.auction.entity.AuctionStatus;
import com.biddo.domain.auction.entity.ItemCondition;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class AuctionDetailResponse {

    private final Long auctionId;
    private final SellerInfo seller;
    private final CategoryInfo category;
    private final String title;
    private final String description;
    private final ItemCondition condition;
    private final Long startingPrice;
    private final Long currentPrice;
    private final Long buyNowPrice;
    private final int bidCount;
    private final int viewCount;
    private final AuctionStatus status;
    private final Long winnerId;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final List<String> imageUrls;
    private final LocalDateTime createdAt;

    private AuctionDetailResponse(Auction auction) {
        this.auctionId = auction.getId();
        this.seller = new SellerInfo(
                auction.getSeller().getId(),
                auction.getSeller().getNickname(),
                auction.getSeller().getProfileImageUrl(),
                auction.getSeller().getTrustScore().doubleValue()
        );
        this.category = new CategoryInfo(
                auction.getCategory().getId(),
                auction.getCategory().getName()
        );
        this.title = auction.getTitle();
        this.description = auction.getDescription();
        this.condition = auction.getCondition();
        this.startingPrice = auction.getStartingPrice();
        this.currentPrice = auction.getCurrentPrice();
        this.buyNowPrice = auction.getBuyNowPrice();
        this.bidCount = auction.getBidCount();
        this.viewCount = auction.getViewCount();
        this.status = auction.getStatus();
        this.winnerId = auction.getWinner() != null ? auction.getWinner().getId() : null;
        this.startTime = auction.getStartTime();
        this.endTime = auction.getEndTime();
        this.imageUrls = auction.getImages().stream()
                .map(img -> img.getImageUrl())
                .toList();
        this.createdAt = auction.getCreatedAt();
    }

    public static AuctionDetailResponse from(Auction auction) {
        return new AuctionDetailResponse(auction);
    }

    @Getter
    public static class SellerInfo {
        private final Long memberId;
        private final String nickname;
        private final String profileImageUrl;
        private final double trustScore;

        public SellerInfo(Long memberId, String nickname, String profileImageUrl, double trustScore) {
            this.memberId = memberId;
            this.nickname = nickname;
            this.profileImageUrl = profileImageUrl;
            this.trustScore = trustScore;
        }
    }

    @Getter
    public static class CategoryInfo {
        private final Long categoryId;
        private final String name;

        public CategoryInfo(Long categoryId, String name) {
            this.categoryId = categoryId;
            this.name = name;
        }
    }
}