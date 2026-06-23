package com.biddo.domain.notification.service;

import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.service.MemberService;
import com.biddo.domain.notification.entity.PriceAlert;
import com.biddo.domain.notification.exception.NotificationErrorCode;
import com.biddo.domain.notification.repository.PriceAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceAlertService {

    private final PriceAlertRepository priceAlertRepository;
    private final MemberService memberService;

    @Transactional
    public PriceAlert create(Long memberId, Long auctionId, int thresholdPercent, Long currentPrice) {
        if (priceAlertRepository.existsByMemberIdAndAuctionId(memberId, auctionId)) {
            throw new BusinessException(NotificationErrorCode.PRICE_ALERT_DUPLICATE);
        }

        Member member = memberService.findById(memberId);

        PriceAlert alert = PriceAlert.builder()
                .member(member)
                .auctionId(auctionId)
                .thresholdPercent(thresholdPercent)
                .basePrice(currentPrice)
                .build();

        return priceAlertRepository.save(alert);
    }

    public List<PriceAlert> findByMemberId(Long memberId) {
        return priceAlertRepository.findByMemberIdOrderByIdDesc(memberId);
    }

    @Transactional
    public PriceAlert update(Long alertId, Long memberId, int thresholdPercent) {
        PriceAlert alert = findAlertById(alertId);
        validateOwner(alert, memberId);
        alert.updateThreshold(thresholdPercent);
        return alert;
    }

    @Transactional
    public void toggleActive(Long alertId, Long memberId) {
        PriceAlert alert = findAlertById(alertId);
        validateOwner(alert, memberId);
        if (alert.isActive()) {
            alert.deactivate();
        } else {
            alert.activate();
        }
    }

    @Transactional
    public void delete(Long alertId, Long memberId) {
        PriceAlert alert = findAlertById(alertId);
        validateOwner(alert, memberId);
        priceAlertRepository.delete(alert);
    }

    public List<PriceAlert> findActiveByAuctionId(Long auctionId) {
        return priceAlertRepository.findActiveByAuctionId(auctionId);
    }

    private PriceAlert findAlertById(Long alertId) {
        return priceAlertRepository.findById(alertId)
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.PRICE_ALERT_NOT_FOUND));
    }

    private void validateOwner(PriceAlert alert, Long memberId) {
        if (!alert.isOwner(memberId)) {
            throw new BusinessException(NotificationErrorCode.NOT_PRICE_ALERT_OWNER);
        }
    }
}