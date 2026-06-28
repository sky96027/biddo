package com.biddo.domain.bid.service;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.auction.entity.AuctionStatus;
import com.biddo.domain.auction.entity.ItemCondition;
import com.biddo.domain.auction.port.out.AuctionLifecyclePort;
import com.biddo.domain.auction.service.AuctionService;
import com.biddo.domain.bid.exception.BidErrorCode;
import com.biddo.domain.bid.entity.AutoBid;
import com.biddo.domain.bid.entity.Bid;
import com.biddo.domain.bid.entity.BidType;
import com.biddo.domain.bid.port.out.AuctionLockPort;
import com.biddo.domain.bid.port.out.AutoBidRepository;
import com.biddo.domain.bid.port.out.BidEventPublisher;
import com.biddo.domain.bid.port.out.BidRepository;
import com.biddo.domain.category.entity.Category;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

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
    private MemberService memberService;

    @Mock
    private BidEventPublisher bidEventPublisher;

    @Mock
    private AuctionLifecyclePort auctionLifecyclePort;

    @Mock
    private AuctionService auctionService;

    @Mock
    private AuctionLockPort auctionLockPort;

    @Mock
    private TransactionTemplate transactionTemplate;

    private Member seller;
    private Member bidder;
    private Member bidder2;
    private Category category;
    private Auction auction;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(inv -> {
            Runnable action = inv.getArgument(1);
            action.run();
            return null;
        }).when(auctionLockPort).executeWithLock(anyLong(), any(Runnable.class));

        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });

        lenient().doAnswer(inv -> {
            Auction a = inv.getArgument(0);
            Long amount = inv.getArgument(1);
            Member b = inv.getArgument(2);
            a.applyBid(amount, b);
            return null;
        }).when(auctionService).applyBid(any(Auction.class), any(Long.class), any(Member.class));

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
        void placeBid_validBid_success() {
            long bidAmount = 515_000L;
            given(auctionService.findAuctionById(1L)).willReturn(auction);
            given(memberService.findById(2L)).willReturn(bidder);
            given(bidRepository.save(any(Bid.class))).willAnswer(inv -> {
                Bid bid = inv.getArgument(0);
                setId(bid, 1L);
                return bid;
            });

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
        void placeBid_notActive_throwsException() {
            Auction pendingAuction = createPendingAuction();
            given(auctionService.findAuctionById(1L)).willReturn(pendingAuction);
            given(memberService.findById(2L)).willReturn(bidder);

            assertThatThrownBy(() -> bidService.placeBid(1L, 2L, 515_000L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(BidErrorCode.AUCTION_NOT_ACTIVE));
        }

        @Test
        @DisplayName("입찰 실패 - 본인 경매")
        void placeBid_selfBid_throwsException() {
            given(auctionService.findAuctionById(1L)).willReturn(auction);
            given(memberService.findById(1L)).willReturn(seller);

            assertThatThrownBy(() -> bidService.placeBid(1L, 1L, 515_000L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(BidErrorCode.SELF_BID_NOT_ALLOWED));
        }

        @Test
        @DisplayName("입찰 실패 - 금액 부족")
        void placeBid_amountTooLow_throwsException() {
            given(auctionService.findAuctionById(1L)).willReturn(auction);
            given(memberService.findById(2L)).willReturn(bidder);

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
        void buyNow_validAuction_success() {
            Auction auctionWithBuyNow = createActiveAuction(500_000L, 1_000_000L);
            given(auctionService.findAuctionById(1L)).willReturn(auctionWithBuyNow);
            given(memberService.findById(2L)).willReturn(bidder);
            given(bidRepository.save(any(Bid.class))).willAnswer(inv -> {
                Bid bid = inv.getArgument(0);
                setId(bid, 1L);
                return bid;
            });


            Bid result = bidService.buyNow(1L, 2L);

            assertThat(result.getBidType()).isEqualTo(BidType.BUY_NOW);
            assertThat(result.getBidAmount()).isEqualTo(1_000_000L);
            verify(auctionService).completeBuyNow(auctionWithBuyNow, bidder);
            verify(bidEventPublisher).publishBidPlaced(result);
        }

        @Test
        @DisplayName("즉시 구매 실패 - 즉시구매가 미설정")
        void buyNow_noBuyNowPrice_throwsException() {
            given(auctionService.findAuctionById(1L)).willReturn(auction);
            given(memberService.findById(2L)).willReturn(bidder);

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
        @DisplayName("경쟁자 없음 - 최소 입찰가로 즉시 낙찰")
        void setAutoBid_noCompetitor_placesMinBid() {
            AutoBid bAutoBid = AutoBid.builder().auction(auction).bidder(bidder).maxAmount(800_000L).build();
            setId(bAutoBid, 1L);

            given(auctionService.findAuctionById(1L)).willReturn(auction);
            given(memberService.findById(2L)).willReturn(bidder);
            given(autoBidRepository.findByAuctionIdAndBidderId(1L, 2L))
                    .willReturn(Optional.empty())
                    .willReturn(Optional.of(bAutoBid));
            given(autoBidRepository.save(any(AutoBid.class))).willReturn(bAutoBid);
            given(autoBidRepository.findActiveByAuctionIdExcludingBidder(anyLong(), anyLong()))
                    .willReturn(Collections.emptyList());
            given(bidRepository.save(any(Bid.class))).willAnswer(inv -> {
                Bid bid = inv.getArgument(0);
                setId(bid, 99L);
                return bid;
            });

            bidService.setAutoBid(1L, 2L, 800_000L);

            // currentPrice=500_000 → minBid=515_000
            assertThat(auction.getCurrentPrice()).isEqualTo(515_000L);
            verify(bidEventPublisher).publishBidPlaced(any(Bid.class));
        }

        @Test
        @DisplayName("경쟁자보다 높은 한도 - 프록시 가격으로 낙찰")
        void setAutoBid_beatsCompetitor_winsAtProxyPrice() {
            // B(bidder, max=800K) vs A(bidder2, max=600K)
            AutoBid competitorAutoBid = AutoBid.builder()
                    .auction(auction).bidder(bidder2).maxAmount(600_000L).build();
            setId(competitorAutoBid, 2L);

            AutoBid bAutoBid = AutoBid.builder().auction(auction).bidder(bidder).maxAmount(800_000L).build();
            setId(bAutoBid, 1L);

            setField(auction, "winner", bidder2);

            given(auctionService.findAuctionById(1L)).willReturn(auction);
            given(memberService.findById(2L)).willReturn(bidder);
            given(autoBidRepository.findByAuctionIdAndBidderId(1L, 2L))
                    .willReturn(Optional.empty())
                    .willReturn(Optional.of(bAutoBid));
            given(autoBidRepository.save(any(AutoBid.class))).willReturn(bAutoBid);
            given(autoBidRepository.findActiveByAuctionIdExcludingBidder(1L, 2L))
                    .willReturn(List.of(competitorAutoBid));
            given(bidRepository.save(any(Bid.class))).willAnswer(inv -> {
                Bid bid = inv.getArgument(0);
                setId(bid, 99L);
                return bid;
            });

            bidService.setAutoBid(1L, 2L, 800_000L);

            // proxyPrice = min(600K + 18K, 800K) = 618K; B(bidder) 낙찰
            assertThat(auction.getCurrentPrice()).isEqualTo(618_000L);
            assertThat(competitorAutoBid.isActive()).isFalse();
        }

        @Test
        @DisplayName("경쟁자보다 낮은 한도 - 경쟁자가 프록시 가격으로 낙찰")
        void setAutoBid_loseToCompetitor_competitorWinsAtProxyPrice() {
            // B(bidder, max=600K) vs A(bidder2, max=900K)
            AutoBid competitorAutoBid = AutoBid.builder()
                    .auction(auction).bidder(bidder2).maxAmount(900_000L).build();
            setId(competitorAutoBid, 2L);

            AutoBid bAutoBid = AutoBid.builder().auction(auction).bidder(bidder).maxAmount(600_000L).build();
            setId(bAutoBid, 1L);

            setField(auction, "winner", bidder2);

            given(auctionService.findAuctionById(1L)).willReturn(auction);
            given(memberService.findById(2L)).willReturn(bidder);
            given(autoBidRepository.findByAuctionIdAndBidderId(1L, 2L))
                    .willReturn(Optional.empty())
                    .willReturn(Optional.of(bAutoBid));
            given(autoBidRepository.save(any(AutoBid.class))).willReturn(bAutoBid);
            given(autoBidRepository.findActiveByAuctionIdExcludingBidder(1L, 2L))
                    .willReturn(List.of(competitorAutoBid));
            given(bidRepository.save(any(Bid.class))).willAnswer(inv -> {
                Bid bid = inv.getArgument(0);
                setId(bid, 99L);
                return bid;
            });

            bidService.setAutoBid(1L, 2L, 600_000L);

            // proxyPrice = min(600K + 18K, 900K) = 618K; A(bidder2) 낙찰, B 자동 입찰 비활성화
            assertThat(auction.getCurrentPrice()).isEqualTo(618_000L);
            assertThat(bAutoBid.isActive()).isFalse();
        }

        @Test
        @DisplayName("이미 최고 입찰자인 경우 자동 입찰 등록 후 즉시 입찰 없음")
        void setAutoBid_alreadyWinning_doesNotPlaceBid() {
            setField(auction, "winner", bidder);

            given(auctionService.findAuctionById(1L)).willReturn(auction);
            given(memberService.findById(2L)).willReturn(bidder);
            given(autoBidRepository.findByAuctionIdAndBidderId(1L, 2L)).willReturn(Optional.empty());
            given(autoBidRepository.save(any(AutoBid.class))).willAnswer(inv -> {
                AutoBid ab = inv.getArgument(0);
                setId(ab, 1L);
                return ab;
            });

            AutoBid result = bidService.setAutoBid(1L, 2L, 800_000L);

            assertThat(result.isActive()).isTrue();
            verifyNoInteractions(bidRepository);
            verifyNoInteractions(bidEventPublisher);
        }

        @Test
        @DisplayName("자동 입찰 실패 - 최대 금액 부족")
        void setAutoBid_maxTooLow_throwsException() {
            given(auctionService.findAuctionById(1L)).willReturn(auction);
            given(memberService.findById(2L)).willReturn(bidder);

            assertThatThrownBy(() -> bidService.setAutoBid(1L, 2L, 510_000L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(BidErrorCode.AUTO_BID_MAX_TOO_LOW));
        }
    }

    @Nested
    @DisplayName("자동 입찰 취소")
    class CancelAutoBidTest {

        @Test
        @DisplayName("자동 입찰 취소 성공")
        void cancelAutoBid_existing_success() {
            AutoBid autoBid = AutoBid.builder()
                    .auction(auction)
                    .bidder(bidder)
                    .maxAmount(800_000L)
                    .build();
            setId(autoBid, 1L);

            given(autoBidRepository.findByAuctionIdAndBidderId(1L, 2L)).willReturn(Optional.of(autoBid));

            bidService.cancelAutoBid(1L, 2L);

            assertThat(autoBid.isActive()).isFalse();
        }

        @Test
        @DisplayName("자동 입찰 취소 실패 - 존재하지 않음")
        void cancelAutoBid_notFound_throwsException() {
            given(autoBidRepository.findByAuctionIdAndBidderId(1L, 2L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> bidService.cancelAutoBid(1L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(BidErrorCode.AUTO_BID_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("최소 입찰 증가 단위 계산")
    class MinIncrementTest {

        @Test
        @DisplayName("0~9,999원: 10%")
        void calculateMinIncrement_under10000_returns10Percent() {
            assertThat(BidService.calculateMinIncrement(5_000L)).isEqualTo(500L);
            assertThat(BidService.calculateMinIncrement(3_340L)).isEqualTo(400L);
        }

        @Test
        @DisplayName("10,000~99,999원: 5%")
        void calculateMinIncrement_10000to99999_returns5Percent() {
            assertThat(BidService.calculateMinIncrement(50_000L)).isEqualTo(2_500L);
            assertThat(BidService.calculateMinIncrement(33_000L)).isEqualTo(1_700L);
        }

        @Test
        @DisplayName("100,000~999,999원: 3%")
        void calculateMinIncrement_100000to999999_returns3Percent() {
            assertThat(BidService.calculateMinIncrement(300_000L)).isEqualTo(9_000L);
            assertThat(BidService.calculateMinIncrement(500_000L)).isEqualTo(15_000L);
        }

        @Test
        @DisplayName("1,000,000원 이상: 1%")
        void calculateMinIncrement_over1000000_returns1Percent() {
            assertThat(BidService.calculateMinIncrement(5_000_000L)).isEqualTo(50_000L);
            assertThat(BidService.calculateMinIncrement(1_230_000L)).isEqualTo(12_300L);
        }
    }

    @Nested
    @DisplayName("스나이핑 방지")
    class SnipingTest {

        @Test
        @DisplayName("종료 10분 전 입찰 시 연장")
        void placeBid_lastTenMinutes_extendsEndTime() {
            Auction snipingAuction = createActiveAuctionEndingSoon();
            given(auctionService.findAuctionById(1L)).willReturn(snipingAuction);
            given(memberService.findById(2L)).willReturn(bidder);
            given(bidRepository.save(any(Bid.class))).willAnswer(inv -> {
                Bid bid = inv.getArgument(0);
                setId(bid, 1L);
                return bid;
            });

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
        void placeBid_withActiveAutoBid_triggersAutoBid() {
            AutoBid autoBid = AutoBid.builder()
                    .auction(auction)
                    .bidder(bidder2)
                    .maxAmount(600_000L)
                    .build();
            setId(autoBid, 1L);

            given(auctionService.findAuctionById(1L)).willReturn(auction);
            given(memberService.findById(2L)).willReturn(bidder);
            given(bidRepository.save(any(Bid.class))).willAnswer(inv -> {
                Bid bid = inv.getArgument(0);
                setId(bid, System.nanoTime());
                return bid;
            });


            given(autoBidRepository.findActiveByAuctionIdExcludingBidder(anyLong(), anyLong()))
                    .willReturn(List.of(autoBid));

            bidService.placeBid(1L, 2L, 515_000L);

            // 수동 입찰 1건 + 프록시 자동 입찰 1건 = 총 2건
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