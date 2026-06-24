package com.biddo.domain.auction.port.out;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.auction.entity.AuctionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository {

    Auction save(Auction auction);

    Optional<Auction> findById(Long id);

    Optional<Auction> findByIdWithImages(Long id);

    List<Auction> findBySellerId(Long sellerId, AuctionStatus status, Long cursor, int size);

    List<Auction> findByWinnerId(Long winnerId, Long cursor, int size);

    List<Auction> findActiveAuctionsByBidderId(Long bidderId, Long cursor, int size);

    long countCompletedBySellerId(Long sellerId);

    long countTotalEndedBySellerId(Long sellerId);

    List<Auction> findByIdIn(List<Long> ids);

    List<Auction> findPendingAuctionsToActivate(LocalDateTime now);

    List<Auction> findActiveAuctionsToEnd(LocalDateTime now);

    List<Auction> findSimilarByCategory(Long auctionId, int size);
}
