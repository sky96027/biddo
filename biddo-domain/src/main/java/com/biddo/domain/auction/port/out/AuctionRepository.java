package com.biddo.domain.auction.port.out;

import com.biddo.domain.auction.model.Auction;

import java.util.Optional;

public interface AuctionRepository {

    Auction save(Auction auction);

    Optional<Auction> findById(Long id);

    Optional<Auction> findByIdWithImages(Long id);
}
