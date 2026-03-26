package com.biddo.domain.review.service;

import com.biddo.domain.auction.exception.AuctionNotFoundException;
import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.model.AuctionStatus;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.review.entity.Review;
import com.biddo.domain.review.exception.ReviewErrorCode;
import com.biddo.domain.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final AuctionRepository auctionRepository;

    @Transactional
    public Review create(Long auctionId, Long reviewerId, int rating, String content) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(AuctionNotFoundException::new);

        validateAuctionEnded(auction);
        validateWinner(auction, reviewerId);
        validateNotAlreadyReviewed(auctionId, reviewerId);

        Review review = Review.builder()
                .auction(auction)
                .reviewer(auction.getWinner())
                .reviewee(auction.getSeller())
                .rating(rating)
                .content(content)
                .build();

        return reviewRepository.save(review);
    }

    @Transactional
    public Review update(Long reviewId, Long memberId, int rating, String content) {
        Review review = findReviewById(reviewId);
        validateReviewer(review, memberId);
        review.update(rating, content);
        return review;
    }

    @Transactional
    public void delete(Long reviewId, Long memberId) {
        Review review = findReviewById(reviewId);
        validateReviewer(review, memberId);
        reviewRepository.delete(review);
    }

    public List<Review> findByRevieweeId(Long revieweeId, Long cursor, int size) {
        PageRequest pageRequest = PageRequest.of(0, size);
        if (cursor == null) {
            return reviewRepository.findByRevieweeIdFirstPage(revieweeId, pageRequest);
        }
        return reviewRepository.findByRevieweeIdWithCursor(revieweeId, cursor, pageRequest);
    }

    public Double getAverageRating(Long revieweeId) {
        return reviewRepository.findAverageRatingByRevieweeId(revieweeId).orElse(0.0);
    }

    public long getReviewCount(Long revieweeId) {
        return reviewRepository.countByRevieweeId(revieweeId);
    }

    private Review findReviewById(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));
    }

    private void validateAuctionEnded(Auction auction) {
        if (auction.getStatus() != AuctionStatus.ENDED && auction.getStatus() != AuctionStatus.SOLD) {
            throw new BusinessException(ReviewErrorCode.AUCTION_NOT_ENDED);
        }
    }

    private void validateWinner(Auction auction, Long memberId) {
        if (auction.getWinner() == null || !auction.getWinner().getId().equals(memberId)) {
            throw new BusinessException(ReviewErrorCode.NOT_WINNER);
        }
    }

    private void validateNotAlreadyReviewed(Long auctionId, Long reviewerId) {
        if (reviewRepository.existsByAuctionIdAndReviewerId(auctionId, reviewerId)) {
            throw new BusinessException(ReviewErrorCode.ALREADY_REVIEWED);
        }
    }

    private void validateReviewer(Review review, Long memberId) {
        if (!review.isReviewer(memberId)) {
            throw new BusinessException(ReviewErrorCode.NOT_REVIEWER);
        }
    }
}