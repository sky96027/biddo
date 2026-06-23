package com.biddo.infra.kafka;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.model.ItemCondition;
import com.biddo.domain.bid.model.Bid;
import com.biddo.domain.bid.model.BidType;
import com.biddo.domain.category.entity.Category;
import com.biddo.domain.member.entity.Member;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaBidEventPublisherTest {

    @InjectMocks
    private KafkaBidEventPublisher publisher;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private Bid bid;

    @BeforeEach
    void setUp() {
        Member seller = Member.builder().email("seller@test.com").password("encoded").nickname("seller").build();
        setId(seller, 1L);

        Member bidder = Member.builder().email("bidder@test.com").password("encoded").nickname("bidder").build();
        setId(bidder, 2L);

        Category category = Category.builder().name("스마트폰").depth(1).sortOrder(1).build();

        Auction auction = Auction.builder()
                .seller(seller).category(category)
                .title("테스트 경매").description("설명")
                .condition(ItemCondition.LIKE_NEW)
                .startingPrice(500_000L).buyNowPrice(null)
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusDays(3))
                .build();
        setId(auction, 1L);

        bid = Bid.builder()
                .auction(auction)
                .bidder(bidder)
                .bidAmount(600_000L)
                .bidType(BidType.MANUAL)
                .build();
        setId(bid, 10L);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("트랜잭션 활성 상태 - Kafka 발행이 즉시 실행되지 않음")
    void publishBidPlaced_withinTransaction_defersUntilAfterCommit() {
        // given
        TransactionSynchronizationManager.initSynchronization();

        // when
        publisher.publishBidPlaced(bid);

        // then
        verify(kafkaTemplate, never()).send(any(String.class), any(String.class), any());
    }

    @Test
    @DisplayName("트랜잭션 활성 상태 - afterCommit 시 Kafka 발행 실행")
    void publishBidPlaced_afterCommit_sendsToKafka() {
        // given
        TransactionSynchronizationManager.initSynchronization();
        given(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .willReturn(new CompletableFuture<>());

        // when
        publisher.publishBidPlaced(bid);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCommit());

        // then
        verify(kafkaTemplate).send(eq(KafkaConfig.BID_EVENTS), eq("1"), any());
    }

    @Test
    @DisplayName("트랜잭션 비활성 상태 - Kafka 즉시 발행")
    void publishBidPlaced_noTransaction_sendsImmediately() {
        // given
        given(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .willReturn(new CompletableFuture<>());

        // when
        publisher.publishBidPlaced(bid);

        // then
        verify(kafkaTemplate).send(eq(KafkaConfig.BID_EVENTS), eq("1"), any());
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
}