package com.biddo.infra.bid;

import com.biddo.domain.bid.model.Bid;
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

    @Modifying
    @Query("UPDATE Bid b SET b.isWinning = false WHERE b.auction.id = :auctionId AND b.isWinning = true")
    void clearWinningBid(@Param("auctionId") Long auctionId);
}