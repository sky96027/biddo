package com.biddo.domain.auction.exception;

import com.biddo.domain.common.exception.BusinessException;

public class AuctionNotFoundException extends BusinessException {

    public AuctionNotFoundException() {
        super(AuctionErrorCode.AUCTION_NOT_FOUND);
    }
}