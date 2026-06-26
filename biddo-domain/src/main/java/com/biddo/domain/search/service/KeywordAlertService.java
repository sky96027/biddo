package com.biddo.domain.search.service;

import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.service.MemberService;
import com.biddo.domain.search.entity.KeywordAlert;
import com.biddo.domain.search.exception.SearchErrorCode;
import com.biddo.domain.search.repository.KeywordAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KeywordAlertService {

    private final KeywordAlertRepository keywordAlertRepository;
    private final MemberService memberService;

    @Transactional
    public KeywordAlert create(Long memberId, String keyword, Long categoryId, Long maxPrice) {
        if (keywordAlertRepository.existsByMemberIdAndKeyword(memberId, keyword)) {
            throw new BusinessException(SearchErrorCode.KEYWORD_ALERT_DUPLICATE);
        }

        Member member = memberService.findById(memberId);

        KeywordAlert alert = KeywordAlert.builder()
                .member(member)
                .keyword(keyword)
                .categoryId(categoryId)
                .maxPrice(maxPrice)
                .build();

        return keywordAlertRepository.save(alert);
    }

    public List<KeywordAlert> findByMemberId(Long memberId) {
        return keywordAlertRepository.findByMemberIdOrderByIdDesc(memberId);
    }

    @Transactional
    public KeywordAlert update(Long alertId, Long memberId, Long categoryId, Long maxPrice) {
        KeywordAlert alert = findAlertById(alertId);
        validateOwner(alert, memberId);
        alert.update(categoryId, maxPrice);
        return alert;
    }

    @Transactional
    public void toggleActive(Long alertId, Long memberId) {
        KeywordAlert alert = findAlertById(alertId);
        validateOwner(alert, memberId);
        if (alert.isActive()) {
            alert.deactivate();
        } else {
            alert.activate();
        }
    }

    @Transactional
    public void delete(Long alertId, Long memberId) {
        KeywordAlert alert = findAlertById(alertId);
        validateOwner(alert, memberId);
        keywordAlertRepository.delete(alert);
    }

    public List<KeywordAlert> findAllActive() {
        return keywordAlertRepository.findAllActive();
    }

    private KeywordAlert findAlertById(Long alertId) {
        return keywordAlertRepository.findById(alertId)
                .orElseThrow(() -> new BusinessException(SearchErrorCode.KEYWORD_ALERT_NOT_FOUND));
    }

    private void validateOwner(KeywordAlert alert, Long memberId) {
        if (!alert.isOwner(memberId)) {
            throw new BusinessException(SearchErrorCode.NOT_KEYWORD_ALERT_OWNER);
        }
    }
}