package com.biddo.api.integration;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.model.AuctionStatus;
import com.biddo.domain.auction.model.ItemCondition;
import com.biddo.domain.auction.service.AuctionService;
import com.biddo.domain.bid.service.BidService;
import com.biddo.domain.category.entity.Category;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("통합: 경매 경계 조건")
@Transactional
class AuctionEdgeCaseIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private BidService bidService;

    private Member seller;
    private Member bidder;
    private Category category;

    @BeforeEach
    void setUp() {
        seller = createMember("edge-seller@test.com", "경계판매자");
        bidder = createMember("edge-bidder@test.com", "경계입찰자");
        category = createCategory("테스트카테고리");
    }

    @Test
    @DisplayName("판매자 본인은 자기 경매에 입찰할 수 없다")
    void placeBid_판매자본인이입찰_throwsException() {
        // given
        Auction auction = createActiveAuction(seller, category, 10_000L, null);

        // when & then
        assertThatThrownBy(() -> bidService.placeBid(auction.getId(), seller.getId(), 11_000L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("PENDING 상태 경매에는 입찰할 수 없다")
    void placeBid_PENDING상태경매에입찰_throwsException() {
        // given: PENDING 상태 (아직 activate 안 함)
        Auction auction = auctionService.create(
                seller.getId(), category.getId(),
                "PENDING 경매", "아직 시작 안 됨",
                ItemCondition.GOOD, 10_000L, null,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(25),
                List.of("https://example.com/img1.jpg"));

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.PENDING);

        // when & then
        assertThatThrownBy(() -> bidService.placeBid(auction.getId(), bidder.getId(), 11_000L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("ACTIVE 상태 경매는 수정할 수 없다 (PENDING에서만 가능)")
    void update_ACTIVE상태경매수정시도_throwsException() {
        // given
        Auction auction = createActiveAuction(seller, category, 10_000L, null);

        // when & then
        assertThatThrownBy(() -> auctionService.update(
                auction.getId(), seller.getId(), category.getId(),
                "수정 시도", "ACTIVE 수정 불가",
                ItemCondition.GOOD, 10_000L, null,
                auction.getEndTime(),
                List.of("https://example.com/img1.jpg")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("ACTIVE 상태 경매는 취소할 수 없다 (PENDING에서만 가능)")
    void cancel_ACTIVE상태경매취소시도_throwsException() {
        // given
        Auction auction = createActiveAuction(seller, category, 10_000L, null);

        // when & then
        assertThatThrownBy(() -> auctionService.cancel(auction.getId(), seller.getId()))
                .isInstanceOf(BusinessException.class);
    }
}