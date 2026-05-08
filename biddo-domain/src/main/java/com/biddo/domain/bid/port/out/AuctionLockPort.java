package com.biddo.domain.bid.port.out;

public interface AuctionLockPort {

    void executeWithLock(Long auctionId, Runnable action);
}