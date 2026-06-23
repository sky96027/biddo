package com.biddo.domain.notification.port.out;

import com.biddo.domain.notification.model.PriceAlert;

import java.util.List;
import java.util.Optional;

public interface PriceAlertRepository {

    boolean existsByMemberIdAndAuctionId(Long memberId, Long auctionId);

    List<PriceAlert> findByMemberIdOrderByIdDesc(Long memberId);

    List<PriceAlert> findActiveByAuctionId(Long auctionId);

    Optional<PriceAlert> findByMemberIdAndAuctionId(Long memberId, Long auctionId);

    List<Long> findActiveAuctionIdsByMemberId(Long memberId);

    PriceAlert save(PriceAlert priceAlert);

    Optional<PriceAlert> findById(Long id);

    void delete(PriceAlert priceAlert);
}
