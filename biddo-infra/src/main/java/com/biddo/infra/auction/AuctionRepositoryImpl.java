package com.biddo.infra.auction;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.model.AuctionStatus;
import com.biddo.domain.auction.port.out.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AuctionRepositoryImpl implements AuctionRepository {

    private final AuctionJpaRepository auctionJpaRepository;

    @Override
    public Auction save(Auction auction) {
        return auctionJpaRepository.save(auction);
    }

    @Override
    public Optional<Auction> findById(Long id) {
        return auctionJpaRepository.findByIdWithSeller(id);
    }

    @Override
    public Optional<Auction> findByIdWithImages(Long id) {
        return auctionJpaRepository.findByIdWithImages(id);
    }

    @Override
    public List<Auction> findBySellerId(Long sellerId, AuctionStatus status, Long cursor, int size) {
        PageRequest pageRequest = PageRequest.of(0, size);
        if (status != null) {
            return cursor == null
                    ? auctionJpaRepository.findBySellerIdAndStatusFirstPage(sellerId, status, pageRequest)
                    : auctionJpaRepository.findBySellerIdAndStatusWithCursor(sellerId, status, cursor, pageRequest);
        }
        return cursor == null
                ? auctionJpaRepository.findBySellerIdFirstPage(sellerId, pageRequest)
                : auctionJpaRepository.findBySellerIdWithCursor(sellerId, cursor, pageRequest);
    }

    @Override
    public List<Auction> findByWinnerId(Long winnerId, Long cursor, int size) {
        PageRequest pageRequest = PageRequest.of(0, size);
        return cursor == null
                ? auctionJpaRepository.findByWinnerIdFirstPage(winnerId, pageRequest)
                : auctionJpaRepository.findByWinnerIdWithCursor(winnerId, cursor, pageRequest);
    }

    @Override
    public List<Auction> findActiveAuctionsByBidderId(Long bidderId, Long cursor, int size) {
        PageRequest pageRequest = PageRequest.of(0, size);
        return cursor == null
                ? auctionJpaRepository.findActiveAuctionsByBidderIdFirstPage(bidderId, pageRequest)
                : auctionJpaRepository.findActiveAuctionsByBidderIdWithCursor(bidderId, cursor, pageRequest);
    }
}