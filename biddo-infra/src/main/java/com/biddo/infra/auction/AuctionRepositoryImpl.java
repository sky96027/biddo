package com.biddo.infra.auction;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.port.out.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
}