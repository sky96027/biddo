package com.biddo.domain.search.service;

import com.biddo.domain.search.dto.AuctionSearchCondition;
import com.biddo.domain.search.dto.AuctionSearchResult;
import com.biddo.domain.search.port.AuctionSearchPort;
import com.biddo.domain.search.port.RecentSearchPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @InjectMocks
    private SearchService searchService;

    @Mock
    private AuctionSearchPort auctionSearchPort;

    @Mock
    private RecentSearchPort recentSearchPort;

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("키워드 검색 시 최근 검색어를 저장하고 검색 결과를 반환한다")
        void searchWithKeyword_savesRecentAndReturnsResults() {
            // given
            Long memberId = 1L;
            AuctionSearchCondition condition = AuctionSearchCondition.builder()
                    .keyword("맥북")
                    .size(20)
                    .build();

            List<AuctionSearchResult> expected = List.of(
                    createSearchResult(1L, "맥북 프로 14인치"),
                    createSearchResult(2L, "맥북 에어 M2")
            );
            given(auctionSearchPort.search(condition)).willReturn(expected);

            // when
            List<AuctionSearchResult> result = searchService.search(condition, memberId);

            // then
            assertThat(result).hasSize(2);
            verify(recentSearchPort).save(memberId, "맥북");
            verify(auctionSearchPort).search(condition);
        }

        @Test
        @DisplayName("비로그인 사용자는 최근 검색어를 저장하지 않는다")
        void searchWithoutLogin_doesNotSaveRecent() {
            // given
            AuctionSearchCondition condition = AuctionSearchCondition.builder()
                    .keyword("아이패드")
                    .size(20)
                    .build();

            given(auctionSearchPort.search(condition)).willReturn(List.of());

            // when
            searchService.search(condition, null);

            // then
            verify(recentSearchPort, never()).save(null, "아이패드");
            verify(auctionSearchPort).search(condition);
        }

        @Test
        @DisplayName("키워드 없이 검색하면 최근 검색어를 저장하지 않는다")
        void searchWithoutKeyword_doesNotSaveRecent() {
            // given
            Long memberId = 1L;
            AuctionSearchCondition condition = AuctionSearchCondition.builder()
                    .categoryId(5L)
                    .size(20)
                    .build();

            given(auctionSearchPort.search(condition)).willReturn(List.of());

            // when
            searchService.search(condition, memberId);

            // then
            verify(recentSearchPort, never()).save(memberId, null);
            verify(auctionSearchPort).search(condition);
        }

        @Test
        @DisplayName("빈 키워드로 검색하면 최근 검색어를 저장하지 않는다")
        void searchWithBlankKeyword_doesNotSaveRecent() {
            // given
            Long memberId = 1L;
            AuctionSearchCondition condition = AuctionSearchCondition.builder()
                    .keyword("   ")
                    .size(20)
                    .build();

            given(auctionSearchPort.search(condition)).willReturn(List.of());

            // when
            searchService.search(condition, memberId);

            // then
            verify(recentSearchPort, never()).save(memberId, "   ");
            verify(auctionSearchPort).search(condition);
        }
    }

    @Nested
    @DisplayName("recentSearches")
    class RecentSearches {

        @Test
        @DisplayName("최근 검색어 목록을 반환한다")
        void getRecentSearches_hasSearches_returnsResult() {
            // given
            Long memberId = 1L;
            given(recentSearchPort.findByMemberId(memberId))
                    .willReturn(List.of("맥북", "아이패드", "에어팟"));

            // when
            List<String> result = searchService.getRecentSearches(memberId);

            // then
            assertThat(result).containsExactly("맥북", "아이패드", "에어팟");
        }

        @Test
        @DisplayName("특정 최근 검색어를 삭제한다")
        void deleteRecentSearch_validKeyword_success() {
            // given
            Long memberId = 1L;

            // when
            searchService.deleteRecentSearch(memberId, "맥북");

            // then
            verify(recentSearchPort).delete(memberId, "맥북");
        }

        @Test
        @DisplayName("전체 최근 검색어를 삭제한다")
        void deleteAllRecentSearches_validMember_success() {
            // given
            Long memberId = 1L;

            // when
            searchService.deleteAllRecentSearches(memberId);

            // then
            verify(recentSearchPort).deleteAll(memberId);
        }
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
