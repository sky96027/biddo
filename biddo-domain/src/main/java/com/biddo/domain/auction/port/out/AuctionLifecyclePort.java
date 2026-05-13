package com.biddo.domain.auction.port.out;

import java.time.LocalDateTime;

public interface AuctionLifecyclePort {

    void scheduleActivation(Long auctionId, LocalDateTime startTime);

    void scheduleEnd(Long auctionId, LocalDateTime endTime);

    void cancelSchedule(Long auctionId);
}