package com.biddo.infra.notification;

import com.biddo.domain.notification.model.PriceAlert;
import com.biddo.domain.notification.port.out.PriceAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PriceAlertRepositoryImpl implements PriceAlertRepository {

    private final PriceAlertJpaRepository jpaRepository;

    @Override
    public boolean existsByMemberIdAndAuctionId(Long memberId, Long auctionId) {
        return jpaRepository.existsByMemberIdAndAuctionId(memberId, auctionId);
    }

    @Override
    public List<PriceAlert> findByMemberIdOrderByIdDesc(Long memberId) {
        return jpaRepository.findByMemberIdOrderByIdDesc(memberId);
    }

    @Override
    public List<PriceAlert> findActiveByAuctionId(Long auctionId) {
        return jpaRepository.findActiveByAuctionId(auctionId);
    }

    @Override
    public Optional<PriceAlert> findByMemberIdAndAuctionId(Long memberId, Long auctionId) {
        return jpaRepository.findByMemberIdAndAuctionId(memberId, auctionId);
    }

    @Override
    public List<Long> findActiveAuctionIdsByMemberId(Long memberId) {
        return jpaRepository.findActiveAuctionIdsByMemberId(memberId);
    }

    @Override
    public PriceAlert save(PriceAlert priceAlert) {
        return jpaRepository.save(priceAlert);
    }

    @Override
    public Optional<PriceAlert> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(PriceAlert priceAlert) {
        jpaRepository.delete(priceAlert);
    }
}
