package com.biddo.infra.elasticsearch;

import com.biddo.domain.search.dto.AuctionSearchCondition;
import com.biddo.domain.search.dto.AuctionSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuctionSearchAdapter: CircuitBreaker fallback")
class AuctionSearchAdapterTest {

    @InjectMocks
    private AuctionSearchAdapter auctionSearchAdapter;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private AuctionSearchFallback auctionSearchFallback;

    @Test
    @DisplayName("ES 장애 시 searchFallback이 DB 검색 결과를 반환한다")
    void searchFallback_returnsDbResults() throws Exception {
        // given
        AuctionSearchCondition condition = AuctionSearchCondition.builder()
                .keyword("맥북")
                .size(20)
                .build();

        List<AuctionSearchResult> fallbackResults = List.of(
                createSearchResult(1L, "맥북 프로 14인치"),
                createSearchResult(2L, "맥북 에어 M2")
        );
        given(auctionSearchFallback.search(condition)).willReturn(fallbackResults);

        // when: fallback 메서드 직접 호출 (CircuitBreaker가 장애 시 호출하는 메서드)
        var method = AuctionSearchAdapter.class.getDeclaredMethod(
                "searchFallback", AuctionSearchCondition.class, Throwable.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<AuctionSearchResult> result = (List<AuctionSearchResult>) method.invoke(
                auctionSearchAdapter, condition, new RuntimeException("ES connection refused"));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("맥북 프로 14인치");
        verify(auctionSearchFallback).search(condition);
    }

    @Test
    @DisplayName("ES 장애 시 findSimilarFallback이 카테고리 기반 DB 결과를 반환한다")
    void findSimilarFallback_returnsCategoryBasedResults() throws Exception {
        // given
        Long auctionId = 1L;
        int size = 5;

        List<AuctionSearchResult> fallbackResults = List.of(
                createSearchResult(2L, "같은 카테고리 상품 1"),
                createSearchResult(3L, "같은 카테고리 상품 2")
        );
        given(auctionSearchFallback.findSimilarByCategory(auctionId, size)).willReturn(fallbackResults);

        // when
        var method = AuctionSearchAdapter.class.getDeclaredMethod(
                "findSimilarFallback", Long.class, int.class, Throwable.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<AuctionSearchResult> result = (List<AuctionSearchResult>) method.invoke(
                auctionSearchAdapter, auctionId, size, new RuntimeException("ES timeout"));

        // then
        assertThat(result).hasSize(2);
        verify(auctionSearchFallback).findSimilarByCategory(auctionId, size);
    }

    private AuctionSearchResult createSearchResult(Long id, String title) {
        return AuctionSearchResult.builder()
                .auctionId(id)
                .title(title)
                .status("ACTIVE")
                .currentPrice(500_000L)
                .bidCount(3)
                .endTime(LocalDateTime.now().plusHours(12))
                .sellerNickname("판매자")
                .categoryName("전자기기")
                .build();
    }
}