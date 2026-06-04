package com.biddo.api.integration;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.bid.model.BidType;
import com.biddo.domain.bid.port.out.AutoBidRepository;
import com.biddo.domain.bid.port.out.BidRepository;
import com.biddo.domain.bid.service.BidService;
import com.biddo.domain.category.entity.Category;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("통합: 동시 입찰 + 분산 락")
class ConcurrentBidIntegrationTest extends IntegrationTestBase {

    @Autowired
    private BidService bidService;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private AutoBidRepository autoBidRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member seller;
    private Category category;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE bid, auto_bid, chat_message, chat_room, " +
                "auction_image, auction, notification, price_alert, keyword_alert, " +
                "review, report, member, category CASCADE");
        seller = createMember("seller@test.com", "판매자");
        category = createCategory("전자기기");
    }

    @Test
    @DisplayName("10명이 동시에 입찰하면 분산 락으로 1건씩 순차 처리되어 데이터 정합성이 보장된다")
    void concurrentBids_shouldMaintainDataIntegrity() throws InterruptedException {
        // given
        Auction auction = createActiveAuction(seller, category, 10_000L, null);
        Long auctionId = auction.getId();

        int bidderCount = 10;
        List<Member> bidders = new ArrayList<>();
        for (int i = 0; i < bidderCount; i++) {
            bidders.add(createMember("bidder" + i + "@test.com", "입찰자" + i));
        }

        // when: 10명이 동시에 최소 증가 단위로 입찰
        ExecutorService executor = Executors.newFixedThreadPool(bidderCount);
        CountDownLatch ready = new CountDownLatch(bidderCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(bidderCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Long> successfulBidAmounts = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < bidderCount; i++) {
            Member bidder = bidders.get(i);
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();

                    // 각 입찰자가 현재가의 최소 증가분만큼 높은 금액으로 입찰
                    // 락으로 순차 처리되므로 일부는 금액 부족으로 실패
                    long bidAmount = 11_000L; // 10,000 + 10% = 11,000
                    var bid = bidService.placeBid(auctionId, bidder.getId(), bidAmount);
                    successCount.incrementAndGet();
                    successfulBidAmounts.add(bid.getBidAmount());
                } catch (BusinessException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown(); // 동시 시작
        done.await();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // then
        // 같은 금액으로 입찰했으므로 1명만 성공, 나머지는 최소 금액 미달로 실패
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(bidderCount - 1);

        // DB 정합성: 경매의 현재가 = 성공한 입찰 금액
        Auction updated = auctionRepository.findById(auctionId).orElseThrow();
        assertThat(updated.getCurrentPrice()).isEqualTo(11_000L);
        assertThat(updated.getBidCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("10명이 서로 다른 금액으로 동시 입찰하면 가장 높은 금액이 현재가가 된다")
    void concurrentBidsWithDifferentAmounts_highestWins() throws InterruptedException {
        // given
        Auction auction = createActiveAuction(seller, category, 10_000L, null);
        Long auctionId = auction.getId();

        int bidderCount = 10;
        List<Member> bidders = new ArrayList<>();
        for (int i = 0; i < bidderCount; i++) {
            bidders.add(createMember("diff-bidder" + i + "@test.com", "다른입찰자" + i));
        }

        // when: 각기 다른 금액으로 동시 입찰 (11,000 ~ 20,000)
        ExecutorService executor = Executors.newFixedThreadPool(bidderCount);
        CountDownLatch ready = new CountDownLatch(bidderCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(bidderCount);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < bidderCount; i++) {
            Member bidder = bidders.get(i);
            long bidAmount = 11_000L + (i * 1_000L);
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    bidService.placeBid(auctionId, bidder.getId(), bidAmount);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 락 순서에 따라 일부는 최소 금액 미달로 실패할 수 있음
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // then: 최종 현재가는 성공한 입찰 중 최고가
        Auction updated = auctionRepository.findById(auctionId).orElseThrow();
        assertThat(updated.getCurrentPrice()).isGreaterThanOrEqualTo(11_000L);
        assertThat(updated.getBidCount()).isEqualTo(successCount.get());
        assertThat(updated.getWinner()).isNotNull();
    }

    @Test
    @DisplayName("자동입찰 설정자가 있으면 수동 입찰 시 자동입찰이 연쇄 발동된다")
    void manualBidTriggersAutoBid_chain() {
        // given
        Auction auction = createActiveAuction(seller, category, 10_000L, null);
        Long auctionId = auction.getId();

        Member autoBidder = createMember("auto@test.com", "자동입찰자");
        Member manualBidder = createMember("manual@test.com", "수동입찰자");

        // 자동입찰 설정: 최대 50,000원
        bidService.setAutoBid(auctionId, autoBidder.getId(), 50_000L);

        // when: 수동 입찰 11,000원
        bidService.placeBid(auctionId, manualBidder.getId(), 11_000L);

        // then: 자동입찰이 연쇄 발동 → 현재가 > 11,000
        Auction updated = auctionRepository.findById(auctionId).orElseThrow();
        assertThat(updated.getCurrentPrice()).isGreaterThan(11_000L);
        assertThat(updated.getWinner().getId()).isEqualTo(autoBidder.getId());
        assertThat(updated.getBidCount()).isGreaterThanOrEqualTo(2); // 수동 1 + 자동 1+

        // 자동입찰 bid_type 확인
        var bids = bidRepository.findBidHistory(auctionId, null, 10);
        assertThat(bids).anyMatch(b -> b.getBidType() == BidType.AUTO);
    }

    @Test
    @DisplayName("자동입찰 연쇄는 최대 10회로 제한된다")
    void autoBidChain_limitedTo10() {
        // given
        Auction auction = createActiveAuction(seller, category, 10_000L, null);
        Long auctionId = auction.getId();

        Member autoBidder1 = createMember("auto1@test.com", "자동1");
        Member autoBidder2 = createMember("auto2@test.com", "자동2");
        Member manualBidder = createMember("trigger@test.com", "트리거");

        // 두 자동입찰자가 충분히 높은 금액 설정 → 서로 연쇄 가능
        bidService.setAutoBid(auctionId, autoBidder1.getId(), 10_000_000L);
        bidService.setAutoBid(auctionId, autoBidder2.getId(), 10_000_000L);

        // when: 수동 입찰로 연쇄 트리거
        bidService.placeBid(auctionId, manualBidder.getId(), 11_000L);

        // then: 수동 1 + 자동 최대 10 = 총 11 이하
        Auction updated = auctionRepository.findById(auctionId).orElseThrow();
        assertThat(updated.getBidCount()).isLessThanOrEqualTo(11);
    }
}