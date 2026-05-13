package com.biddo.domain.auction.port.out;

import com.biddo.domain.auction.model.Auction;

public interface AuctionEventPublisher {

    void publishAuctionCreated(Auction auction);

    void publishAuctionUpdated(Auction auction);

    void publishAuctionCancelled(Auction auction);

    void publishAuctionSold(Auction auction);

    void publishAuctionActivated(Auction auction);

    void publishAuctionEnded(Auction auction);
}