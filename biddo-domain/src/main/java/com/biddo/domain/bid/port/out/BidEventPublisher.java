package com.biddo.domain.bid.port.out;

import com.biddo.domain.bid.entity.Bid;

public interface BidEventPublisher {

    void publishBidPlaced(Bid bid);
}