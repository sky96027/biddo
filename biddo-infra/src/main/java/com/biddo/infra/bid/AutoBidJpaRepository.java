package com.biddo.infra.bid;

import com.biddo.domain.bid.model.AutoBid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AutoBidJpaRepository extends JpaRepository<AutoBid, Long> {

    @Query("SELECT ab FROM AutoBid ab JOIN FETCH ab.bidder WHERE ab.auction.id = :auctionId AND ab.bidder.id = :bidderId")
    Optional<AutoBid> findByAuctionIdAndBidderId(@Param("auctionId") Long auctionId, @Param("bidderId") Long bidderId);

    @Query("SELECT ab FROM AutoBid ab JOIN FETCH ab.bidder WHERE ab.auction.id = :auctionId AND ab.bidder.id != :excludeBidderId AND ab.isActive = true")
    List<AutoBid> findActiveByAuctionIdExcludingBidder(@Param("auctionId") Long auctionId, @Param("excludeBidderId") Long excludeBidderId);

    @Modifying
    @Query("UPDATE AutoBid ab SET ab.isActive = false WHERE ab.auction.id = :auctionId AND ab.isActive = true")
    void deactivateAllByAuctionId(@Param("auctionId") Long auctionId);
}