package com.biddo.domain.bid.service;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.model.AuctionStatus;
import com.biddo.domain.auction.model.ItemCondition;
import com.biddo.domain.auction.port.out.AuctionEventPublisher;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.bid.exception.BidErrorCode;
import com.biddo.domain.bid.model.AutoBid;
import com.biddo.domain.bid.model.Bid;
import com.biddo.domain.bid.model.BidType;
import com.biddo.domain.bid.port.out.AutoBidRepository;
import com.biddo.domain.bid.port.out.BidEventPublisher;
import com.biddo.domain.bid.port.out.BidRepository;
import com.biddo.domain.category.entity.Category;
import com.biddo.domain.chat.service.ChatService;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @InjectMocks
    private BidService bidService;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private AutoBidRepository autoBidRepository;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BidEventPublisher bidEventPublisher;

    @Mock
    private AuctionEventPublisher auctionEventPublisher;

    @Mock
    private ChatService chatService;

    private Member seller;
    private Member bidder;
    private Member bidder2;
    private Category category;
    private Auction auction;

    @BeforeEach
    void setUp() {
        seller = Member.builder()
                .email("seller@test.com")
                .password("encoded")
                .nickname("seller")
                .build();
        setId(seller, 1L);

        bidder = Member.builder()
                .email("bidder@test.com")
                .password("encoded")
                .nickname("bidder")
                .build();
        setId(bidder, 2L);

        bidder2 = Member.builder()
                .email("bidder2@test.com")
                .password("encoded")
                .nickname("bidder2")
                .build();
        setId(bidder2, 3L);

        category = Category.builder()
                .name("스마트폰")
                .depth(1)
                .sortOrder(1)
                .build();
        setId(category, 9L);

        auction = createActiveAuction(500_000L, null);
    }

    @Nested
    @DisplayName("수동 입찰")
    class PlaceBid {

        @Test
        @DisplayName("입찰 성공")
        void success() {
            long bidAmount = 515_000L;
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
            given(bidRepository.save(any(Bid.class))).willAnswer(inv -> {
                Bid bid = inv.getArgument(0);
                setId(bid, 1L);
                return bid;
            });
            given(auctionRepository.save(any(Auction.class))).willReturn(auction);
            given(autoBidRepository.findActiveByAuctionIdExcludingBidder(anyLong(), anyLong()))
                    .willReturn(Collections.emptyList());

            Bid result = bidService.placeBid(1L, 2L, bidAmount);

            assertThat(result.getBidAmount()).isEqualTo(bidAmount);
            assertThat(result.getBidType()).isEqualTo(BidType.MANUAL);
            assertThat(result.isWinning()).isTrue();
            assertThat(auction.getCurrentPrice()).isEqualTo(bidAmount);
            assertThat(auction.getBidCount()).isEqualTo(1);
            verify(bidEventPublisher).publishBidPlaced(result);
        }

        @Test
        @DisplayName("입찰 실패 - 경매 ACTIVE 아님")
        void fail_notActive() {
            Auction pendingAuction = createPendingAuction();
            given(auctionRepository.findById(1L)).willReturn(Optional.of(pendingAuction));
            given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));

            assertThatThrownBy(() -> bidService.placeBid(1L, 2L, 515_000L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(BidErrorCode.AUCTION_NOT_ACTIVE));
        }

        @Test
        @DisplayName("입찰 실패 - 본인 경매")
        void fail_selfBid() {
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            given(memberRepository.findById(1L)).willReturn(Optional.of(seller));

            assertThatThrownBy(() -> bidService.placeBid(1L, 1L, 515_000L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(BidErrorCode.SELF_BID_NOT_ALLOWED));
        }

        @Test
        @DisplayName("입찰 실패 - 금액 부족")
        void fail_amountTooLow() {
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));

            assertThatThrownBy(() -> bidService.placeBid(1L, 2L, 510_000L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(BidErrorCode.BID_AMOUNT_TOO_LOW));
        }
    }

    @Nested
    @DisplayName("즉시 구매")
    class BuyNow {

        @Test
        @DisplayName("즉시 구매 성공")
        void success() {
            Auction auctionWithBuyNow = createActiveAuction(500_000L, 1_000_000L);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auctionWithBuyNow));
            given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
            given(bidRepository.save(any(Bid.class))).willAnswer(inv -> {
                Bid bid = inv.getArgument(0);
                setId(bid, 1L);
                return bid;
            });
            given(auctionRepository.save(any(Auction.class))).willReturn(auctionWithBuyNow);

            Bid result = bidService.buyNow(1L, 2L);

            assertThat(result.getBidType()).isEqualTo(BidType.BUY_NOW);
            assertThat(result.getBidAmount()).isEqualTo(1_000_000L);
            assertThat(auctionWithBuyNow.getStatus()).isEqualTo(AuctionStatus.SOLD);
            verify(autoBidRepository).deactivateAllByAuctionId(1L);
            verify(chatService).createRoom(auctionWithBuyNow);
            verify(auctionEventPublisher).publishAuctionSold(auctionWithBuyNow);
        }

        @Test
        @DisplayName("즉시 구매 실패 - 즉시구매가 미설정")
        void fail_noBuyNowPrice() {
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));

            assertThatThrownBy(() -> bidService.buyNow(1L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(BidErrorCode.BUY_NOW_NOT_AVAILABLE));
        }
    }

    @Nested
    @DisplayName("자동 입찰")
    class SetAutoBidTest {

        @Test
        @DisplayName("자동 입찰 설정 성공")
        void success() {
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
            given(autoBidRepository.findByAuctionIdAndBidderId(1L, 2L)).willReturn(Optional.empty());
            given(autoBidRepository.save(any(AutoBid.class))).willAnswer(inv -> {
                AutoBid ab = inv.getArgument(0);
                setId(ab, 1L);
                return ab;
            });

            AutoBid result = bidService.setAutoBid(1L, 2L, 800_000L);

            assertThat(result.getMaxAmount()).isEqualTo(800_000L);
            assertThat(result.isActive()).isTrue();
        }

        @Test
        @DisplayName("자동 입찰 수정 성공 - 이미 설정 존재")
        void success_update() {
            AutoBid existingAutoBid = AutoBid.builder()
                    .auction(auction)
                    .bidder(bidder)
                    .maxAmount(600_000L)
                    .build();
            setId(existingAutoBid, 1L);

            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
            given(autoBidRepository.findByAuctionIdAndBidderId(1L, 2L)).willReturn(Optional.of(existingAutoBid));
            given(autoBidRepository.save(existingAutoBid)).willReturn(existingAutoBid);

            AutoBid result = bidService.setAutoBid(1L, 2L, 900_000L);

            assertThat(result.getMaxAmount()).isEqualTo(900_000L);
        }

        @Test
        @DisplayName("자동 입찰 실패 - 최대 금액 부족")
        void fail_maxTooLow() {
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));

            assertThatThrownBy(() -> bidService.setAutoBid(1L, 2L, 510_000L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(BidErrorCode.AUTO_BID_MAX_TOO_LOW));
        }
    }

    @Nested
    @DisplayName("최소 입찰 증가 단위 계산")
    class MinIncrementTest {

        @Test
        @DisplayName("0~9,999원: 10%")
        void tier1() {
            assertThat(BidService.calculateMinIncrement(5_000L)).isEqualTo(500L);
            assertThat(BidService.calculateMinIncrement(3_340L)).isEqualTo(400L);
        }

        @Test
        @DisplayName("10,000~99,999원: 5%")
        void tier2() {
            assertThat(BidService.calculateMinIncrement(50_000L)).isEqualTo(2_500L);
            assertThat(BidService.calculateMinIncrement(33_000L)).isEqualTo(1_700L);
        }

        @Test
        @DisplayName("100,000~999,999원: 3%")
        void tier3() {
            assertThat(BidService.calculateMinIncrement(300_000L)).isEqualTo(9_000L);
            assertThat(BidService.calculateMinIncrement(500_000L)).isEqualTo(15_000L);
        }

        @Test
        @DisplayName("1,000,000원 이상: 1%")
        void tier4() {
            assertThat(BidService.calculateMinIncrement(5_000_000L)).isEqualTo(50_000L);
            assertThat(BidService.calculateMinIncrement(1_230_000L)).isEqualTo(12_300L);
        }
    }

    @Nested
    @DisplayName("스나이핑 방지")
    class SnipingTest {

        @Test
        @DisplayName("종료 10분 전 입찰 시 연장")
        void extendOnSniping() {
            Auction snipingAuction = createActiveAuctionEndingSoon();
            given(auctionRepository.findById(1L)).willReturn(Optional.of(snipingAuction));
            given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
            given(bidRepository.save(any(Bid.class))).willAnswer(inv -> {
                Bid bid = inv.getArgument(0);
                setId(bid, 1L);
                return bid;
            });
            given(auctionRepository.save(any(Auction.class))).willReturn(snipingAuction);
            given(autoBidRepository.findActiveByAuctionIdExcludingBidder(anyLong(), anyLong()))
                    .willReturn(Collections.emptyList());

            LocalDateTime originalEndTime = snipingAuction.getEndTime();
            bidService.placeBid(1L, 2L, 515_000L);

            assertThat(snipingAuction.getEndTime()).isAfter(originalEndTime);
        }
    }

    @Nested
    @DisplayName("자동 입찰 트리거")
    class AutoBidTrigger {

        @Test
        @DisplayName("수동 입찰 시 자동 입찰 트리거")
        void triggersAutoBid() {
            AutoBid autoBid = AutoBid.builder()
                    .auction(auction)
                    .bidder(bidder2)
                    .maxAmount(600_000L)
                    .build();
            setId(autoBid, 1L);

            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
            given(bidRepository.save(any(Bid.class))).willAnswer(inv -> {
                Bid bid = inv.getArgument(0);
                setId(bid, System.nanoTime());
                return bid;
            });
            given(auctionRepository.save(any(Auction.class))).willReturn(auction);

            given(autoBidRepository.findActiveByAuctionIdExcludingBidder(anyLong(), anyLong()))
                    .willReturn(List.of(autoBid))
                    .willReturn(Collections.emptyList());

            bidService.placeBid(1L, 2L, 515_000L);

            verify(bidRepository, times(2)).save(any(Bid.class));
            verify(bidEventPublisher, times(2)).publishBidPlaced(any(Bid.class));
        }
    }

    private Auction createActiveAuction(Long startingPrice, Long buyNowPrice) {
        Auction auc = Auction.builder()
                .seller(seller)
                .category(category)
                .title("테스트 경매")
                .description("테스트 설명")
                .condition(ItemCondition.LIKE_NEW)
                .startingPrice(startingPrice)
                .buyNowPrice(buyNowPrice)
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusDays(3))
                .build();
        setId(auc, 1L);
        setField(auc, "status", AuctionStatus.ACTIVE);
        return auc;
    }

    private Auction createActiveAuctionEndingSoon() {
        Auction auc = Auction.builder()
                .seller(seller)
                .category(category)
                .title("종료 임박 경매")
                .description("테스트 설명")
                .condition(ItemCondition.LIKE_NEW)
                .startingPrice(500_000L)
                .buyNowPrice(null)
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusMinutes(5))
                .build();
        setId(auc, 1L);
        setField(auc, "status", AuctionStatus.ACTIVE);
        return auc;
    }

    private Auction createPendingAuction() {
        Auction auc = Auction.builder()
                .seller(seller)
                .category(category)
                .title("대기 경매")
                .description("테스트 설명")
                .condition(ItemCondition.LIKE_NEW)
                .startingPrice(500_000L)
                .buyNowPrice(null)
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusDays(3))
                .build();
        setId(auc, 1L);
        return auc;
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