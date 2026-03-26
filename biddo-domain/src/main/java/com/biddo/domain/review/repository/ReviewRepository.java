package com.biddo.domain.review.repository;

import com.biddo.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByAuctionIdAndReviewerId(Long auctionId, Long reviewerId);

    @Query("SELECT r FROM Review r JOIN FETCH r.reviewer WHERE r.reviewee.id = :revieweeId AND r.id < :cursor ORDER BY r.id DESC")
    List<Review> findByRevieweeIdWithCursor(@Param("revieweeId") Long revieweeId,
                                            @Param("cursor") Long cursor,
                                            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT r FROM Review r JOIN FETCH r.reviewer WHERE r.reviewee.id = :revieweeId ORDER BY r.id DESC")
    List<Review> findByRevieweeIdFirstPage(@Param("revieweeId") Long revieweeId,
                                           org.springframework.data.domain.Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.reviewee.id = :revieweeId")
    Optional<Double> findAverageRatingByRevieweeId(@Param("revieweeId") Long revieweeId);

    long countByRevieweeId(Long revieweeId);
}