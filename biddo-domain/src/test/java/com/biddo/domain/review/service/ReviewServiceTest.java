package com.biddo.domain.review.service;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.model.AuctionStatus;
import com.biddo.domain.auction.model.ItemCondition;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.category.entity.Category;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.review.entity.Review;
import com.biddo.domain.review.exception.ReviewErrorCode;
import com.biddo.domain.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @InjectMocks
    private ReviewService reviewService;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private AuctionRepository auctionRepository;

    private Member seller;
    private Member buyer;
    private Category category;
    private Auction endedAuction;

    @BeforeEach
    void setUp() {
        seller = Member.builder().email("seller@test.com").password("encoded").nickname("seller").build();
        setId(seller, 1L);

        buyer = Member.builder().email("buyer@test.com").password("encoded").nickname("buyer").build();
        setId(buyer, 2L);

        category = Category.builder().name("스마트폰").depth(1).sortOrder(1).build();
        setId(category, 9L);

        endedAuction = Auction.builder()
                .seller(seller).category(category)
                .title("테스트 경매").description("설명")
                .condition(ItemCondition.LIKE_NEW)
                .startingPrice(500_000L).buyNowPrice(null)
                .startTime(LocalDateTime.now().minusDays(4))
                .endTime(LocalDateTime.now().minusDays(1))
                .build();
        setId(endedAuction, 1L);
        setField(endedAuction, "status", AuctionStatus.ENDED);
        setField(endedAuction, "winner", buyer);
    }

    @Test
    @DisplayName("후기 작성 성공")
    void create_validInput_returnsSavedReview() {
        given(auctionRepository.findById(1L)).willReturn(Optional.of(endedAuction));
        given(reviewRepository.existsByAuctionIdAndReviewerId(1L, 2L)).willReturn(false);
        given(reviewRepository.save(any(Review.class))).willAnswer(inv -> {
            Review r = inv.getArgument(0);
            setId(r, 1L);
            return r;
        });

        Review result = reviewService.create(1L, 2L, 5, "좋은 거래였습니다");

        assertThat(result.getRating()).isEqualTo(5);
        assertThat(result.getContent()).isEqualTo("좋은 거래였습니다");
        assertThat(result.getReviewer().getId()).isEqualTo(2L);
        assertThat(result.getReviewee().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("후기 작성 실패 - 낙찰자 아님")
    void create_notWinner_throwsException() {
        given(auctionRepository.findById(1L)).willReturn(Optional.of(endedAuction));

        assertThatThrownBy(() -> reviewService.create(1L, 999L, 5, "내용"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ReviewErrorCode.NOT_WINNER));
    }

    @Test
    @DisplayName("후기 작성 실패 - 이미 작성됨")
    void create_alreadyReviewed_throwsException() {
        given(auctionRepository.findById(1L)).willReturn(Optional.of(endedAuction));
        given(reviewRepository.existsByAuctionIdAndReviewerId(1L, 2L)).willReturn(true);

        assertThatThrownBy(() -> reviewService.create(1L, 2L, 5, "내용"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ReviewErrorCode.ALREADY_REVIEWED));
    }

    @Test
    @DisplayName("후기 작성 실패 - 경매 미종료")
    void create_auctionNotEnded_throwsException() {
        Auction activeAuction = Auction.builder()
                .seller(seller).category(category)
                .title("진행 중 경매").description("설명")
                .condition(ItemCondition.LIKE_NEW)
                .startingPrice(500_000L).buyNowPrice(null)
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusDays(3))
                .build();
        setId(activeAuction, 2L);
        setField(activeAuction, "status", AuctionStatus.ACTIVE);

        given(auctionRepository.findById(2L)).willReturn(Optional.of(activeAuction));

        assertThatThrownBy(() -> reviewService.create(2L, 2L, 5, "내용"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ReviewErrorCode.AUCTION_NOT_ENDED));
    }

    @Test
    @DisplayName("후기 수정 성공")
    void update_validInput_returnsUpdatedReview() {
        Review review = Review.builder()
                .auction(endedAuction).reviewer(buyer).reviewee(seller)
                .rating(4).content("괜찮았습니다")
                .build();
        setId(review, 1L);

        given(reviewRepository.findById(1L)).willReturn(Optional.of(review));

        Review result = reviewService.update(1L, 2L, 5, "최고였습니다");

        assertThat(result.getRating()).isEqualTo(5);
        assertThat(result.getContent()).isEqualTo("최고였습니다");
    }

    @Test
    @DisplayName("후기 수정 실패 - 작성자 아님")
    void update_notReviewer_throwsException() {
        Review review = Review.builder()
                .auction(endedAuction).reviewer(buyer).reviewee(seller)
                .rating(4).content("내용")
                .build();
        setId(review, 1L);

        given(reviewRepository.findById(1L)).willReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.update(1L, 999L, 5, "수정"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ReviewErrorCode.NOT_REVIEWER));
    }

    @Test
    @DisplayName("후기 삭제 성공")
    void delete_validInput_deletesReview() {
        Review review = Review.builder()
                .auction(endedAuction).reviewer(buyer).reviewee(seller)
                .rating(4).content("내용")
                .build();
        setId(review, 1L);

        given(reviewRepository.findById(1L)).willReturn(Optional.of(review));

        reviewService.delete(1L, 2L);

        verify(reviewRepository).delete(review);
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