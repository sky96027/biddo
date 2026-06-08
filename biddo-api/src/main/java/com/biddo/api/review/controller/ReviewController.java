package com.biddo.api.review.controller;

import com.biddo.api.common.response.ApiResponse;
import com.biddo.api.common.response.CursorResponse;
import com.biddo.api.common.security.CustomUserDetails;
import com.biddo.api.review.dto.request.ReviewRequest;
import com.biddo.api.review.dto.response.ReviewListResponse;
import com.biddo.api.review.dto.response.ReviewResponse;
import com.biddo.domain.review.entity.Review;
import com.biddo.domain.review.service.ReviewService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "후기")
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private static final int DEFAULT_SIZE = 20;

    private final ReviewService reviewService;

    @PostMapping("/api/v1/auctions/{auctionId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewResponse> createReview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId,
            @Valid @RequestBody ReviewRequest request) {
        Review review = reviewService.create(
                auctionId, userDetails.getMemberId(),
                request.getRating(), request.getContent());
        return ApiResponse.success(ReviewResponse.from(review));
    }

    @PutMapping("/api/v1/reviews/{reviewId}")
    public ApiResponse<ReviewResponse> updateReview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewRequest request) {
        Review review = reviewService.update(
                reviewId, userDetails.getMemberId(),
                request.getRating(), request.getContent());
        return ApiResponse.success(ReviewResponse.from(review));
    }

    @DeleteMapping("/api/v1/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteReview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long reviewId) {
        reviewService.delete(reviewId, userDetails.getMemberId());
        return ApiResponse.success();
    }

    @GetMapping("/api/v1/members/{memberId}/reviews")
    public ApiResponse<ReviewListResponse> getReviews(
            @PathVariable Long memberId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        int fetchSize = Math.min(size, DEFAULT_SIZE);
        List<Review> reviews = reviewService.findByRevieweeId(memberId, cursor, fetchSize + 1);

        boolean hasNext = reviews.size() > fetchSize;
        List<Review> content = hasNext ? reviews.subList(0, fetchSize) : reviews;
        List<ReviewResponse> responses = content.stream().map(ReviewResponse::from).toList();

        String nextCursor = hasNext ? String.valueOf(content.get(content.size() - 1).getId()) : null;
        CursorResponse<ReviewResponse> cursorResponse = CursorResponse.of(responses, nextCursor, hasNext);

        double avgRating = reviewService.getAverageRating(memberId);
        long totalCount = reviewService.getReviewCount(memberId);

        return ApiResponse.success(ReviewListResponse.of(cursorResponse, avgRating, totalCount));
    }
}