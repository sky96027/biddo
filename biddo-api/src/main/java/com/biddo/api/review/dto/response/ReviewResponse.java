package com.biddo.api.review.dto.response;

import com.biddo.domain.review.entity.Review;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ReviewResponse {

    private final Long reviewId;
    private final String reviewerNickname;
    private final int rating;
    private final String content;
    private final LocalDateTime createdAt;

    private ReviewResponse(Long reviewId, String reviewerNickname, int rating,
                           String content, LocalDateTime createdAt) {
        this.reviewId = reviewId;
        this.reviewerNickname = reviewerNickname;
        this.rating = rating;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getReviewer().getNickname(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt()
        );
    }
}