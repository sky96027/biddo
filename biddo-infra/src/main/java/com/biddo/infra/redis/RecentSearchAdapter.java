package com.biddo.infra.redis;

import com.biddo.domain.search.port.RecentSearchPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RecentSearchAdapter implements RecentSearchPort {

    private final RecentSearchRepository recentSearchRepository;

    @Override
    public void save(Long memberId, String keyword) {
        recentSearchRepository.save(memberId, keyword);
    }

    @Override
    public List<String> findByMemberId(Long memberId) {
        return recentSearchRepository.findByMemberId(memberId);
    }

    @Override
    public void delete(Long memberId, String keyword) {
        recentSearchRepository.delete(memberId, keyword);
    }

    @Override
    public void deleteAll(Long memberId) {
        recentSearchRepository.deleteAll(memberId);
    }
}