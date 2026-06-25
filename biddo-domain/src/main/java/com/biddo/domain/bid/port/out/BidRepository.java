package com.biddo.domain.bid.port.out;

import com.biddo.domain.bid.entity.Bid;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface BidRepository {

    Bid save(Bid bid);

    Optional<Bid> findWinningBidByAuctionId(Long auctionId);

    List<Bid> findByAuctionIdOrderByBidAmountDesc(Long auctionId);

    List<Bid> findBidHistory(Long auctionId, Long cursor, int size);

    void clearWinningBid(Long auctionId);

    List<Long> findDistinctBidderIdsByAuctionId(Long auctionId);

    Map<Long, List<Long>> findDistinctBidderIdsByAuctionIdIn(List<Long> auctionIds);

    List<Long> findTopCategoryIdsByBidderId(Long bidderId, int limit);
}