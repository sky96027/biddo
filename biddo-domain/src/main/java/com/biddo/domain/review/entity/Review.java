package com.biddo.domain.review.entity;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.review.exception.ReviewErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "review", uniqueConstraints = {
        @UniqueConstraint(name = "uq_review", columnNames = {"auction_id", "reviewer_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private Member reviewer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewee_id", nullable = false)
    private Member reviewee;

    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Review(Auction auction, Member reviewer, Member reviewee, int rating, String content) {
        validateRating(rating);
        this.auction = auction;
        this.reviewer = reviewer;
        this.reviewee = reviewee;
        this.rating = rating;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public void update(int rating, String content) {
        validateRating(rating);
        this.rating = rating;
        this.content = content;
    }

    public boolean isReviewer(Long memberId) {
        return this.reviewer.getId().equals(memberId);
    }

    private void validateRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new BusinessException(ReviewErrorCode.INVALID_RATING);
        }
    }
}