package com.biddo.api.integration;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.auction.entity.AuctionStatus;
import com.biddo.domain.bid.entity.AutoBid;
import com.biddo.domain.bid.entity.Bid;
import com.biddo.domain.bid.entity.BidType;
import com.biddo.domain.bid.port.out.AutoBidRepository;
import com.biddo.domain.bid.port.out.BidRepository;
import com.biddo.domain.bid.service.BidService;
import com.biddo.domain.category.entity.Category;
import com.biddo.domain.chat.entity.ChatRoom;
import com.biddo.domain.chat.repository.ChatMessageRepository;
import com.biddo.domain.chat.repository.ChatRoomRepository;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("통합: 즉시 구매 → 알림 → 채팅 E2E")
@Transactional
class BuyNowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private BidService bidService;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private AutoBidRepository autoBidRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private Member seller;
    private Category category;

    @BeforeEach
    void setUp() {
        seller = createMember("buynow-seller@test.com", "즉구판매자");
        category = createCategory("의류");
    }

    @Test
    @DisplayName("즉시 구매 시 SOLD 전환 + 자동입찰 전체 비활성화 + 채팅방 생성")
    void buyNow_자동입찰과수동입찰존재시즉시구매_SOLD전환및자동입찰비활성화및채팅방생성() {
        // given
        Auction auction = createActiveAuction(seller, category, 10_000L, 100_000L);
        Long auctionId = auction.getId();

        Member autoBidder1 = createMember("bn-auto1@test.com", "즉구자동1");
        Member autoBidder2 = createMember("bn-auto2@test.com", "즉구자동2");
        Member manualBidder = createMember("bn-manual@test.com", "즉구수동");
        Member buyer = createMember("bn-buyer@test.com", "즉구매자");

        // 자동입찰 2명 설정
        bidService.setAutoBid(auctionId, autoBidder1.getId(), 50_000L);
        bidService.setAutoBid(auctionId, autoBidder2.getId(), 60_000L);

        // 수동 입찰 1건
        bidService.placeBid(auctionId, manualBidder.getId(), 11_000L);

        // when: 즉시 구매
        Bid buyNowBid = bidService.buyNow(auctionId, buyer.getId());

        // then 1: 입찰 기록 확인
        assertThat(buyNowBid.getBidType()).isEqualTo(BidType.BUY_NOW);
        assertThat(buyNowBid.getBidAmount()).isEqualTo(100_000L);

        // then 2: 경매 상태 = SOLD, winner = buyer
        Auction sold = auctionRepository.findById(auctionId).orElseThrow();
        assertThat(sold.getStatus()).isEqualTo(AuctionStatus.SOLD);
        assertThat(sold.getWinner().getId()).isEqualTo(buyer.getId());
        assertThat(sold.getCurrentPrice()).isEqualTo(100_000L);

        // then 3: 자동입찰 전부 비활성화 (벌크 UPDATE 후 영속성 컨텍스트 초기화)
        entityManager.flush();
        entityManager.clear();
        AutoBid ab1 = autoBidRepository.findByAuctionIdAndBidderId(auctionId, autoBidder1.getId()).orElseThrow();
        AutoBid ab2 = autoBidRepository.findByAuctionIdAndBidderId(auctionId, autoBidder2.getId()).orElseThrow();
        assertThat(ab1.isActive()).isFalse();
        assertThat(ab2.isActive()).isFalse();

        // then 4: 채팅방 생성 확인 (seller + buyer) + 시스템 메시지
        assertThat(chatRoomRepository.existsByAuctionId(auctionId)).isTrue();
        List<ChatRoom> rooms = chatRoomRepository.findByMemberId(buyer.getId());
        assertThat(rooms).hasSize(1);
        ChatRoom room = rooms.get(0);
        assertThat(room.getSeller().getId()).isEqualTo(seller.getId());
        assertThat(room.getBuyer().getId()).isEqualTo(buyer.getId());

        // 시스템 메시지 존재 확인
        var messages = chatMessageRepository.findByRoomIdFirstPage(room.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(messages).isNotEmpty();
        assertThat(messages.get(0).getContent()).contains("포트폴리오");
    }

    @Test
    @DisplayName("즉시 구매 후 추가 입찰 불가")
    void placeBid_즉시구매완료된경매에입찰_throwsException() {
        // given
        Auction auction = createActiveAuction(seller, category, 10_000L, 50_000L);
        Long auctionId = auction.getId();

        Member buyer = createMember("bn-first@test.com", "선구매자");
        Member lateBidder = createMember("bn-late@test.com", "늦은입찰자");

        bidService.buyNow(auctionId, buyer.getId());

        // when & then: SOLD 상태에서 입찰 시도 → 실패
        assertThatThrownBy(() -> bidService.placeBid(auctionId, lateBidder.getId(), 11_000L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("즉시구매가 미설정 경매에서 즉시 구매 시도 시 실패")
    void buyNow_즉시구매가미설정경매_throwsException() {
        // given: buyNowPrice = null
        Auction auction = createActiveAuction(seller, category, 10_000L, null);

        Member buyer = createMember("bn-noprice@test.com", "가격없음구매자");

        // when & then
        assertThatThrownBy(() -> bidService.buyNow(auction.getId(), buyer.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("판매자 본인은 즉시 구매 불가")
    void buyNow_판매자본인이즉시구매시도_throwsException() {
        // given
        Auction auction = createActiveAuction(seller, category, 10_000L, 50_000L);

        // when & then
        assertThatThrownBy(() -> bidService.buyNow(auction.getId(), seller.getId()))
                .isInstanceOf(BusinessException.class);
    }
}