package com.biddo.infra.auction;

import com.biddo.domain.auction.model.Auction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuctionJpaRepository extends JpaRepository<Auction, Long> {

    @Query("SELECT a FROM Auction a JOIN FETCH a.seller JOIN FETCH a.category WHERE a.id = :id")
    Optional<Auction> findByIdWithSeller(@Param("id") Long id);

    @Query("SELECT a FROM Auction a JOIN FETCH a.seller JOIN FETCH a.category LEFT JOIN FETCH a.images WHERE a.id = :id")
    Optional<Auction> findByIdWithImages(@Param("id") Long id);
}