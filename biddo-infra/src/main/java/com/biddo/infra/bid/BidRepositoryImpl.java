package com.biddo.infra.bid;

import com.biddo.domain.bid.model.Bid;
import com.biddo.domain.bid.port.out.BidRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BidRepositoryImpl implements BidRepository {

    private final BidJpaRepository bidJpaRepository;

    @Override
    public Bid save(Bid bid) {
        return bidJpaRepository.save(bid);
    }

    @Override
    public Optional<Bid> findWinningBidByAuctionId(Long auctionId) {
        return bidJpaRepository.findWinningBidByAuctionId(auctionId);
    }

    @Override
    public List<Bid> findByAuctionIdOrderByBidAmountDesc(Long auctionId) {
        return bidJpaRepository.findByAuctionIdOrderByBidAmountDesc(auctionId);
    }

    @Override
    public List<Bid> findBidHistory(Long auctionId, Long cursor, int size) {
        PageRequest pageRequest = PageRequest.of(0, size);
        return cursor == null
                ? bidJpaRepository.findBidHistoryFirstPage(auctionId, pageRequest)
                : bidJpaRepository.findBidHistoryWithCursor(auctionId, cursor, pageRequest);
    }

    @Override
    public void clearWinningBid(Long auctionId) {
        bidJpaRepository.clearWinningBid(auctionId);
    }

    @Override
    public List<Long> findDistinctBidderIdsByAuctionId(Long auctionId) {
        return bidJpaRepository.findDistinctBidderIdsByAuctionId(auctionId);
    }

    @Override
    public List<Long> findTopCategoryIdsByBidderId(Long bidderId, int limit) {
        return bidJpaRepository.findTopCategoryIdsByBidderId(bidderId, PageRequest.of(0, limit));
    }
}