package com.biddo.infra.auction;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.auction.entity.AuctionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuctionJpaRepository extends JpaRepository<Auction, Long> {

@Query("SELECT a FROM Auction a JOIN FETCH a.seller JOIN FETCH a.category WHERE a.id = :id")
    Optional<Auction> findByIdWithSeller(@Param("id") Long id);

    @Query("SELECT a FROM Auction a JOIN FETCH a.seller JOIN FETCH a.category LEFT JOIN FETCH a.images LEFT JOIN FETCH a.winner WHERE a.id = :id")
    Optional<Auction> findByIdWithImages(@Param("id") Long id);

    @Query("SELECT a FROM Auction a WHERE a.seller.id = :sellerId AND a.status = :status ORDER BY a.id DESC")
    List<Auction> findBySellerIdAndStatusFirstPage(@Param("sellerId") Long sellerId,
                                                    @Param("status") AuctionStatus status,
                                                    Pageable pageable);

    @Query("SELECT a FROM Auction a WHERE a.seller.id = :sellerId AND a.status = :status AND a.id < :cursor ORDER BY a.id DESC")
    List<Auction> findBySellerIdAndStatusWithCursor(@Param("sellerId") Long sellerId,
                                                    @Param("status") AuctionStatus status,
                                                    @Param("cursor") Long cursor,
                                                    Pageable pageable);

    @Query("SELECT a FROM Auction a WHERE a.seller.id = :sellerId ORDER BY a.id DESC")
    List<Auction> findBySellerIdFirstPage(@Param("sellerId") Long sellerId, Pageable pageable);

    @Query("SELECT a FROM Auction a WHERE a.seller.id = :sellerId AND a.id < :cursor ORDER BY a.id DESC")
    List<Auction> findBySellerIdWithCursor(@Param("sellerId") Long sellerId,
                                           @Param("cursor") Long cursor,
                                           Pageable pageable);

    @Query("SELECT a FROM Auction a WHERE a.winner.id = :winnerId AND a.status IN ('ENDED', 'SOLD') ORDER BY a.id DESC")
    List<Auction> findByWinnerIdFirstPage(@Param("winnerId") Long winnerId, Pageable pageable);

    @Query("SELECT a FROM Auction a WHERE a.winner.id = :winnerId AND a.status IN ('ENDED', 'SOLD') AND a.id < :cursor ORDER BY a.id DESC")
    List<Auction> findByWinnerIdWithCursor(@Param("winnerId") Long winnerId,
                                           @Param("cursor") Long cursor,
                                           Pageable pageable);

    @Query("SELECT DISTINCT a FROM Auction a JOIN Bid b ON b.auction = a WHERE b.bidder.id = :bidderId AND a.status = 'ACTIVE' ORDER BY a.id DESC")
    List<Auction> findActiveAuctionsByBidderIdFirstPage(@Param("bidderId") Long bidderId, Pageable pageable);

    @Query("SELECT DISTINCT a FROM Auction a JOIN Bid b ON b.auction = a WHERE b.bidder.id = :bidderId AND a.status = 'ACTIVE' AND a.id < :cursor ORDER BY a.id DESC")
    List<Auction> findActiveAuctionsByBidderIdWithCursor(@Param("bidderId") Long bidderId,
                                                         @Param("cursor") Long cursor,
                                                         Pageable pageable);

    @Query("SELECT COUNT(a) FROM Auction a WHERE a.seller.id = :sellerId AND a.status IN ('ENDED', 'SOLD') AND a.winner IS NOT NULL")
    long countCompletedBySellerId(@Param("sellerId") Long sellerId);

    @Query("SELECT COUNT(a) FROM Auction a WHERE a.seller.id = :sellerId AND a.status IN ('ENDED', 'SOLD', 'CANCELLED')")
    long countTotalEndedBySellerId(@Param("sellerId") Long sellerId);

    @Query("SELECT a FROM Auction a JOIN FETCH a.seller JOIN FETCH a.category LEFT JOIN FETCH a.images WHERE a.id IN :ids")
    List<Auction> findAllByIdWithImages(@Param("ids") List<Long> ids);

    @Query("SELECT a FROM Auction a JOIN FETCH a.seller JOIN FETCH a.category LEFT JOIN FETCH a.images WHERE a.id IN :ids AND a.status = 'ACTIVE'")
    List<Auction> findByIdInAndActive(@Param("ids") List<Long> ids);

    @Query("""
            SELECT a FROM Auction a JOIN FETCH a.seller JOIN FETCH a.category LEFT JOIN FETCH a.images
            WHERE a.status = 'ACTIVE' AND a.id <> :auctionId
            AND a.category.id = (SELECT a2.category.id FROM Auction a2 WHERE a2.id = :auctionId)
            ORDER BY a.bidCount DESC, a.id DESC
            """)
    List<Auction> findSimilarByCategory(@Param("auctionId") Long auctionId, Pageable pageable);

    @Query("SELECT a FROM Auction a WHERE a.status = 'PENDING' AND a.startTime <= :now")
    List<Auction> findPendingAuctionsToActivate(@Param("now") LocalDateTime now);

    @Query("SELECT a FROM Auction a WHERE a.status = 'ACTIVE' AND a.endTime <= :now")
    List<Auction> findActiveAuctionsToEnd(@Param("now") LocalDateTime now);
}