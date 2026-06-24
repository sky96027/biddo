package com.biddo.infra.bid;

import com.biddo.domain.bid.entity.AutoBid;
import com.biddo.domain.bid.port.out.AutoBidRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AutoBidRepositoryImpl implements AutoBidRepository {

    private final AutoBidJpaRepository autoBidJpaRepository;

    @Override
    public AutoBid save(AutoBid autoBid) {
        return autoBidJpaRepository.save(autoBid);
    }

    @Override
    public Optional<AutoBid> findByAuctionIdAndBidderId(Long auctionId, Long bidderId) {
        return autoBidJpaRepository.findByAuctionIdAndBidderId(auctionId, bidderId);
    }

    @Override
    public List<AutoBid> findActiveByAuctionIdExcludingBidder(Long auctionId, Long excludeBidderId) {
        return autoBidJpaRepository.findActiveByAuctionIdExcludingBidder(auctionId, excludeBidderId);
    }

    @Override
    public void deactivateAllByAuctionId(Long auctionId) {
        autoBidJpaRepository.deactivateAllByAuctionId(auctionId);
    }
}