package com.biddo.domain.bid.port.out;

import com.biddo.domain.bid.model.Bid;

import java.util.List;
import java.util.Optional;

public interface BidRepository {

    Bid save(Bid bid);

    Optional<Bid> findWinningBidByAuctionId(Long auctionId);

    List<Bid> findByAuctionIdOrderByBidAmountDesc(Long auctionId);

    void clearWinningBid(Long auctionId);
}