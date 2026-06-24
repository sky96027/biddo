package com.biddo.api.integration;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.auction.entity.AuctionStatus;
import com.biddo.domain.auction.entity.ItemCondition;
import com.biddo.domain.auction.service.AuctionService;
import com.biddo.domain.bid.service.BidService;
import com.biddo.domain.category.entity.Category;
import com.biddo.domain.chat.repository.ChatRoomRepository;
import com.biddo.domain.member.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("통합: 경매 전체 생명주기")
@Transactional
class AuctionLifecycleIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private BidService bidService;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    private Member seller;
    private Category category;

    @BeforeEach
    void setUp() {
        seller = createMember("lifecycle-seller@test.com", "생명주기판매자");
        category = createCategory("생활용품");
    }

    @Test
    @DisplayName("PENDING → ACTIVE → 입찰 → ENDED → 낙찰자 결정 + 채팅방 생성")
    void auctionLifecycle_입찰있는경매종료_낙찰자결정및채팅방생성() {
        // given: PENDING 상태로 경매 생성
        Auction auction = auctionService.create(
                seller.getId(), category.getId(),
                "생명주기 테스트 상품", "전체 플로우 테스트",
                ItemCondition.LIKE_NEW, 10_000L, null,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(2),
                List.of("https://example.com/img1.jpg"));

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.PENDING);

        // when: PENDING → ACTIVE 전환
        auctionService.activateAuction(auction.getId());

        Auction activated = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(activated.getStatus()).isEqualTo(AuctionStatus.ACTIVE);

        // when: 입찰 3건
        Member bidder1 = createMember("lc-bidder1@test.com", "생명주기입찰자1");
        Member bidder2 = createMember("lc-bidder2@test.com", "생명주기입찰자2");
        Member bidder3 = createMember("lc-bidder3@test.com", "생명주기입찰자3");

        bidService.placeBid(auction.getId(), bidder1.getId(), 11_000L);
        bidService.placeBid(auction.getId(), bidder2.getId(), 12_100L);
        bidService.placeBid(auction.getId(), bidder3.getId(), 13_000L);

        Auction afterBids = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(afterBids.getCurrentPrice()).isEqualTo(13_000L);
        assertThat(afterBids.getBidCount()).isEqualTo(3);
        assertThat(afterBids.getWinner().getId()).isEqualTo(bidder3.getId());

        // when: ACTIVE → ENDED 전환
        auctionService.endAuction(auction.getId());

        // then: 낙찰자 확인 + 채팅방 생성
        Auction ended = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(ended.getStatus()).isEqualTo(AuctionStatus.ENDED);
        assertThat(ended.getWinner().getId()).isEqualTo(bidder3.getId());
        assertThat(chatRoomRepository.existsByAuctionId(auction.getId())).isTrue();
    }

    @Test
    @DisplayName("입찰 없이 종료되면 유찰 처리 (winner=null, 채팅방 미생성)")
    void endAuction_입찰없이종료_유찰처리되고채팅방미생성() {
        // given
        Auction auction = auctionService.create(
                seller.getId(), category.getId(),
                "유찰 테스트 상품", "입찰 없는 경매",
                ItemCondition.GOOD, 50_000L, null,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1),
                List.of("https://example.com/img1.jpg"));

        auctionService.activateAuction(auction.getId());

        // when: 입찰 없이 종료
        auctionService.endAuction(auction.getId());

        // then: 유찰
        Auction ended = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(ended.getStatus()).isEqualTo(AuctionStatus.ENDED);
        assertThat(ended.getWinner()).isNull();
        assertThat(ended.getBidCount()).isZero();
        assertThat(chatRoomRepository.existsByAuctionId(auction.getId())).isFalse();
    }

    @Test
    @DisplayName("종료 10분 전 입찰 시 endTime이 +10분 연장된다 (스나이핑 방지)")
    void placeBid_종료10분전입찰_endTime이10분연장() {
        // given: 종료 5분 전 경매
        Auction auction = createSnipingAuction(seller, category, 10_000L);
        LocalDateTime originalEndTime = auction.getEndTime();

        Member bidder = createMember("sniper@test.com", "스나이퍼");

        // when: 종료 임박 상태에서 입찰
        bidService.placeBid(auction.getId(), bidder.getId(), 11_000L);

        // then: endTime이 연장됨
        Auction updated = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(updated.getEndTime()).isAfter(originalEndTime);
    }

    @Test
    @DisplayName("PENDING 상태에서 취소하면 CANCELLED 전환")
    void cancel_PENDING상태에서취소_CANCELLED로전환() {
        // given
        Auction auction = auctionService.create(
                seller.getId(), category.getId(),
                "취소 테스트", "PENDING 취소",
                ItemCondition.NEW, 10_000L, null,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(25),
                List.of("https://example.com/img1.jpg"));

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.PENDING);

        // when
        auctionService.cancel(auction.getId(), seller.getId());

        // then
        Auction cancelled = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
    }
}