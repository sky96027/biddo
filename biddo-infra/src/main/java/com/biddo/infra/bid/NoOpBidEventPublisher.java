package com.biddo.infra.bid;

import com.biddo.domain.bid.model.Bid;
import com.biddo.domain.bid.port.out.BidEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NoOpBidEventPublisher implements BidEventPublisher {

    @Override
    public void publishBidPlaced(Bid bid) {
        log.debug("NoOp: BID_PLACED event for bid {} on auction {}",
                bid.getId(), bid.getAuction().getId());
    }
}