package com.biddo.domain.auction.service;

import com.biddo.domain.auction.exception.AuctionErrorCode;
import com.biddo.domain.auction.exception.AuctionNotFoundException;
import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.model.AuctionStatus;
import com.biddo.domain.auction.model.ItemCondition;
import com.biddo.domain.auction.port.out.AuctionEventPublisher;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.category.entity.Category;
import com.biddo.domain.category.repository.CategoryRepository;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.exception.MemberErrorCode;
import com.biddo.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @InjectMocks
    private AuctionService auctionService;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AuctionEventPublisher auctionEventPublisher;

    private Member seller;
    private Category category;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @BeforeEach
    void setUp() {
        seller = Member.builder()
                .email("seller@test.com")
                .password("encoded")
                .nickname("seller")
                .build();
        setId(seller, 1L);

        category = Category.builder()
                .name("스마트폰")
                .depth(1)
                .sortOrder(1)
                .build();
        setId(category, 9L);

        startTime = LocalDateTime.now().plusHours(1);
        endTime = LocalDateTime.now().plusDays(3);
    }

    @Test
    @DisplayName("경매 생성 성공")
    void create_success() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(seller));
        given(categoryRepository.findById(9L)).willReturn(Optional.of(category));
        given(auctionRepository.save(any(Auction.class))).willAnswer(invocation -> {
            Auction auction = invocation.getArgument(0);
            setId(auction, 1L);
            return auction;
        });

        Auction result = auctionService.create(1L, 9L, "iPhone 15", "설명",
                ItemCondition.LIKE_NEW, 500000L, null, startTime, endTime, List.of("img1.jpg"));

        assertThat(result.getTitle()).isEqualTo("iPhone 15");
        assertThat(result.getStatus()).isEqualTo(AuctionStatus.PENDING);
        assertThat(result.getCurrentPrice()).isEqualTo(500000L);
        assertThat(result.getImages()).hasSize(1);
        verify(auctionEventPublisher).publishAuctionCreated(result);
    }

    @Test
    @DisplayName("경매 생성 실패 - 카테고리 없음")
    void create_categoryNotFound() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(seller));
        given(categoryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> auctionService.create(1L, 999L, "iPhone", "설명",
                ItemCondition.NEW, 500000L, null, startTime, endTime, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuctionErrorCode.CATEGORY_NOT_FOUND));
    }

    @Test
    @DisplayName("경매 수정 성공")
    void update_success() {
        Auction auction = createPendingAuction();
        given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
        given(categoryRepository.findById(9L)).willReturn(Optional.of(category));

        Auction result = auctionService.update(1L, 1L, 9L, "수정된 제목", "수정된 설명",
                ItemCondition.GOOD, 450000L, 800000L, endTime.plusDays(1), null);

        assertThat(result.getTitle()).isEqualTo("수정된 제목");
        assertThat(result.getStartingPrice()).isEqualTo(450000L);
        assertThat(result.getBuyNowPrice()).isEqualTo(800000L);
        verify(auctionEventPublisher).publishAuctionUpdated(result);
    }

    @Test
    @DisplayName("경매 수정 실패 - 판매자가 아님")
    void update_notSeller() {
        Auction auction = createPendingAuction();
        given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

        assertThatThrownBy(() -> auctionService.update(1L, 999L, 9L, "제목", "설명",
                ItemCondition.NEW, 500000L, null, endTime, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuctionErrorCode.NOT_AUCTION_SELLER));
    }

    @Test
    @DisplayName("경매 수정 실패 - PENDING 상태가 아님")
    void update_notPending() {
        Auction auction = createPendingAuction();
        auction.cancel();
        given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

        assertThatThrownBy(() -> auctionService.update(1L, 1L, 9L, "제목", "설명",
                ItemCondition.NEW, 500000L, null, endTime, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuctionErrorCode.AUCTION_NOT_PENDING));
    }

    @Test
    @DisplayName("경매 취소 성공")
    void cancel_success() {
        Auction auction = createPendingAuction();
        given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

        auctionService.cancel(1L, 1L);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        verify(auctionEventPublisher).publishAuctionCancelled(auction);
    }

    @Test
    @DisplayName("경매 취소 실패 - 판매자가 아님")
    void cancel_notSeller() {
        Auction auction = createPendingAuction();
        given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

        assertThatThrownBy(() -> auctionService.cancel(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuctionErrorCode.NOT_AUCTION_SELLER));
    }

    @Test
    @DisplayName("경매 생성 실패 - 회원 없음")
    void create_memberNotFound() {
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> auctionService.create(999L, 9L, "iPhone", "설명",
                ItemCondition.NEW, 500000L, null, startTime, endTime, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    @DisplayName("경매 생성 실패 - 종료시간이 시작시간 이전")
    void create_invalidTime() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(seller));
        given(categoryRepository.findById(9L)).willReturn(Optional.of(category));

        LocalDateTime invalidEndTime = startTime.minusHours(1);

        assertThatThrownBy(() -> auctionService.create(1L, 9L, "iPhone", "설명",
                ItemCondition.NEW, 500000L, null, startTime, invalidEndTime, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuctionErrorCode.INVALID_AUCTION_TIME));
    }

    @Test
    @DisplayName("경매 조회 실패 - 존재하지 않음")
    void findById_notFound() {
        given(auctionRepository.findByIdWithImages(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> auctionService.findById(999L))
                .isInstanceOf(AuctionNotFoundException.class);
    }

    @Test
    @DisplayName("관리자 경매 강제 취소 성공 - ACTIVE 경매")
    void forceCancel_active() {
        Auction auction = createPendingAuction();
        setField(auction, "status", AuctionStatus.ACTIVE);
        given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

        auctionService.forceCancel(1L);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        verify(auctionEventPublisher).publishAuctionCancelled(auction);
    }

    @Test
    @DisplayName("관리자 경매 강제 취소 실패 - 이미 취소됨")
    void forceCancel_alreadyCancelled() {
        Auction auction = createPendingAuction();
        auction.cancel();
        given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

        assertThatThrownBy(() -> auctionService.forceCancel(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuctionErrorCode.AUCTION_ALREADY_CANCELLED));
    }

    private Auction createPendingAuction() {
        Auction auction = Auction.builder()
                .seller(seller)
                .category(category)
                .title("테스트 경매")
                .description("테스트 설명")
                .condition(ItemCondition.LIKE_NEW)
                .startingPrice(500000L)
                .buyNowPrice(null)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        setId(auction, 1L);
        return auction;
    }

    private void setId(Object entity, Long id) {
        try {
            java.lang.reflect.Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object entity, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}