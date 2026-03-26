package com.biddo.api.review.dto.response;

import com.biddo.api.common.response.CursorResponse;
import lombok.Getter;

@Getter
public class ReviewListResponse {

    private final CursorResponse<ReviewResponse> reviews;
    private final double averageRating;
    private final long totalCount;

    private ReviewListResponse(CursorResponse<ReviewResponse> reviews, double averageRating, long totalCount) {
        this.reviews = reviews;
        this.averageRating = averageRating;
        this.totalCount = totalCount;
    }

    public static ReviewListResponse of(CursorResponse<ReviewResponse> reviews,
                                         double averageRating, long totalCount) {
        return new ReviewListResponse(reviews, averageRating, totalCount);
    }
}