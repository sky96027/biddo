package com.biddo.infra.notification;

import com.biddo.domain.notification.model.PriceAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PriceAlertJpaRepository extends JpaRepository<PriceAlert, Long> {

    boolean existsByMemberIdAndAuctionId(Long memberId, Long auctionId);

    List<PriceAlert> findByMemberIdOrderByIdDesc(Long memberId);

    @Query("SELECT pa FROM PriceAlert pa WHERE pa.auctionId = :auctionId AND pa.isActive = true")
    List<PriceAlert> findActiveByAuctionId(@Param("auctionId") Long auctionId);

    Optional<PriceAlert> findByMemberIdAndAuctionId(Long memberId, Long auctionId);

    @Query("SELECT pa.auctionId FROM PriceAlert pa WHERE pa.member.id = :memberId AND pa.isActive = true")
    List<Long> findActiveAuctionIdsByMemberId(@Param("memberId") Long memberId);
}
