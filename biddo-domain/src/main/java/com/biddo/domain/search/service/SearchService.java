package com.biddo.domain.search.service;

import com.biddo.domain.search.dto.AuctionSearchCondition;
import com.biddo.domain.search.dto.AuctionSearchResult;
import com.biddo.domain.search.port.AuctionSearchPort;
import com.biddo.domain.search.port.RecentSearchPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private final AuctionSearchPort auctionSearchPort;
    private final RecentSearchPort recentSearchPort;

    public List<AuctionSearchResult> search(AuctionSearchCondition condition, Long memberId) {
        if (memberId != null && condition.getKeyword() != null && !condition.getKeyword().isBlank()) {
            recentSearchPort.save(memberId, condition.getKeyword());
        }
        return auctionSearchPort.search(condition);
    }

    public List<String> getRecentSearches(Long memberId) {
        return recentSearchPort.findByMemberId(memberId);
    }

    public void deleteRecentSearch(Long memberId, String keyword) {
        recentSearchPort.delete(memberId, keyword);
    }

    public void deleteAllRecentSearches(Long memberId) {
        recentSearchPort.deleteAll(memberId);
    }

    public List<AuctionSearchResult> findSimilar(Long auctionId, int size) {
        return auctionSearchPort.findSimilar(auctionId, size);
    }
}