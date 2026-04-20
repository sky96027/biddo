package com.biddo.domain.bid.port.out;

import com.biddo.domain.bid.model.AutoBid;

import java.util.List;
import java.util.Optional;

public interface AutoBidRepository {

    AutoBid save(AutoBid autoBid);

    Optional<AutoBid> findByAuctionIdAndBidderId(Long auctionId, Long bidderId);

    List<AutoBid> findActiveByAuctionIdExcludingBidder(Long auctionId, Long excludeBidderId);

    void deactivateAllByAuctionId(Long auctionId);
}