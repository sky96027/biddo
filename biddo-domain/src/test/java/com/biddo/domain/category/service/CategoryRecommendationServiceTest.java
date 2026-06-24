package com.biddo.domain.category.service;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.bid.port.out.BidRepository;
import com.biddo.domain.category.entity.Category;
import com.biddo.domain.category.repository.CategoryRepository;
import com.biddo.domain.notification.port.out.PriceAlertRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CategoryRecommendationServiceTest {

    @InjectMocks
    private CategoryRecommendationService recommendationService;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private PriceAlertRepository priceAlertRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("입찰 이력 기반 카테고리 추천")
    void recommend_bidHistoryExists_returnsBidBasedCategories() {
        given(bidRepository.findTopCategoryIdsByBidderId(1L, 10)).willReturn(List.of(1L, 2L, 3L));
        given(priceAlertRepository.findActiveAuctionIdsByMemberId(1L)).willReturn(List.of());

        Category cat1 = createCategory(1L, "전자기기");
        Category cat2 = createCategory(2L, "패션/의류");
        Category cat3 = createCategory(3L, "가구/인테리어");
        given(categoryRepository.findAllById(List.of(1L, 2L, 3L))).willReturn(List.of(cat1, cat2, cat3));

        List<Category> result = recommendationService.recommend(1L);

        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("가격 알림 기반 카테고리 추천 추가")
    void recommend_priceAlertExists_returnsPriceAlertBasedCategories() {
        given(bidRepository.findTopCategoryIdsByBidderId(1L, 10)).willReturn(List.of(1L));
        given(priceAlertRepository.findActiveAuctionIdsByMemberId(1L)).willReturn(List.of(100L));

        Auction auction = createAuctionWithCategory(100L, 2L);
        given(auctionRepository.findById(100L)).willReturn(Optional.of(auction));

        Category cat1 = createCategory(1L, "전자기기");
        Category cat2 = createCategory(2L, "패션/의류");
        given(categoryRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(cat1, cat2));

        List<Category> result = recommendationService.recommend(1L);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("중복 카테고리 제거")
    void recommend_duplicateCategoryIds_returnsDeduplicatedList() {
        given(bidRepository.findTopCategoryIdsByBidderId(1L, 10)).willReturn(List.of(1L, 2L));
        given(priceAlertRepository.findActiveAuctionIdsByMemberId(1L)).willReturn(List.of(100L));

        Auction auction = createAuctionWithCategory(100L, 1L);
        given(auctionRepository.findById(100L)).willReturn(Optional.of(auction));

        Category cat1 = createCategory(1L, "전자기기");
        Category cat2 = createCategory(2L, "패션/의류");
        given(categoryRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(cat1, cat2));

        List<Category> result = recommendationService.recommend(1L);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("이력 없는 사용자 — 빈 목록 반환")
    void recommend_noHistoryExists_returnsEmptyList() {
        given(bidRepository.findTopCategoryIdsByBidderId(1L, 10)).willReturn(List.of());
        given(priceAlertRepository.findActiveAuctionIdsByMemberId(1L)).willReturn(List.of());

        List<Category> result = recommendationService.recommend(1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("최대 5개까지만 추천")
    void recommend_moreThanFiveCategories_returnsMaxFive() {
        given(bidRepository.findTopCategoryIdsByBidderId(1L, 10))
                .willReturn(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L));
        given(priceAlertRepository.findActiveAuctionIdsByMemberId(1L)).willReturn(List.of());

        List<Category> cats = List.of(
                createCategory(1L, "a"), createCategory(2L, "b"), createCategory(3L, "c"),
                createCategory(4L, "d"), createCategory(5L, "e"));
        given(categoryRepository.findAllById(List.of(1L, 2L, 3L, 4L, 5L))).willReturn(cats);

        List<Category> result = recommendationService.recommend(1L);

        assertThat(result).hasSize(5);
    }

    private Category createCategory(Long id, String name) {
        Category category = Category.builder().name(name).depth(0).sortOrder(0).build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private Auction createAuctionWithCategory(Long auctionId, Long categoryId) {
        Category category = createCategory(categoryId, "cat-" + categoryId);
        Auction auction = Auction.builder()
                .seller(null).category(category).title("test").description("test")
                .condition(com.biddo.domain.auction.entity.ItemCondition.GOOD)
                .startingPrice(10000L)
                .startTime(java.time.LocalDateTime.now().plusDays(1))
                .endTime(java.time.LocalDateTime.now().plusDays(2))
                .build();
        ReflectionTestUtils.setField(auction, "id", auctionId);
        return auction;
    }
}