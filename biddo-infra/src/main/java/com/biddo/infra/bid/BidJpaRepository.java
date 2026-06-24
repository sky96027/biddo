package com.biddo.infra.bid;

import com.biddo.domain.bid.entity.Bid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BidJpaRepository extends JpaRepository<Bid, Long> {

    @Query("SELECT b FROM Bid b JOIN FETCH b.bidder WHERE b.auction.id = :auctionId AND b.isWinning = true")
    Optional<Bid> findWinningBidByAuctionId(@Param("auctionId") Long auctionId);

    @Query("SELECT b FROM Bid b JOIN FETCH b.bidder WHERE b.auction.id = :auctionId ORDER BY b.bidAmount DESC")
    List<Bid> findByAuctionIdOrderByBidAmountDesc(@Param("auctionId") Long auctionId);

    @Query("SELECT b FROM Bid b JOIN FETCH b.bidder WHERE b.auction.id = :auctionId ORDER BY b.id DESC")
    List<Bid> findBidHistoryFirstPage(@Param("auctionId") Long auctionId, Pageable pageable);

    @Query("SELECT b FROM Bid b JOIN FETCH b.bidder WHERE b.auction.id = :auctionId AND b.id < :cursor ORDER BY b.id DESC")
    List<Bid> findBidHistoryWithCursor(@Param("auctionId") Long auctionId,
                                       @Param("cursor") Long cursor,
                                       Pageable pageable);

    @Modifying
    @Query("UPDATE Bid b SET b.isWinning = false WHERE b.auction.id = :auctionId AND b.isWinning = true")
    void clearWinningBid(@Param("auctionId") Long auctionId);

    @Query("SELECT DISTINCT b.bidder.id FROM Bid b WHERE b.auction.id = :auctionId")
    List<Long> findDistinctBidderIdsByAuctionId(@Param("auctionId") Long auctionId);

    @Query("""
            SELECT a.category.id FROM Bid b JOIN b.auction a
            WHERE b.bidder.id = :bidderId
            GROUP BY a.category.id
            ORDER BY COUNT(b) DESC
            """)
    List<Long> findTopCategoryIdsByBidderId(@Param("bidderId") Long bidderId, Pageable pageable);
}