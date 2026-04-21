package com.biddo.domain.search.port;

import java.util.List;

public interface RecentSearchPort {

    void save(Long memberId, String keyword);

    List<String> findByMemberId(Long memberId);

    void delete(Long memberId, String keyword);

    void deleteAll(Long memberId);
}