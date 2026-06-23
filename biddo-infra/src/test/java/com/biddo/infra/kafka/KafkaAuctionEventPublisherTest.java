package com.biddo.infra.kafka;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.model.AuctionStatus;
import com.biddo.domain.auction.model.ItemCondition;
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
class KafkaAuctionEventPublisherTest {

    @InjectMocks
    private KafkaAuctionEventPublisher publisher;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private Auction auction;

    @BeforeEach
    void setUp() {
        Member seller = Member.builder().email("seller@test.com").password("encoded").nickname("seller").build();
        setId(seller, 1L);

        Category category = Category.builder().name("스마트폰").depth(1).sortOrder(1).build();

        auction = Auction.builder()
                .seller(seller).category(category)
                .title("테스트 경매").description("설명")
                .condition(ItemCondition.LIKE_NEW)
                .startingPrice(500_000L).buyNowPrice(null)
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusDays(3))
                .build();
        setId(auction, 1L);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("트랜잭션 활성 상태 - Kafka 발행이 즉시 실행되지 않음")
    void publish_withinTransaction_defersUntilAfterCommit() {
        // given
        TransactionSynchronizationManager.initSynchronization();

        // when
        publisher.publishAuctionCreated(auction);

        // then
        verify(kafkaTemplate, never()).send(any(String.class), any(String.class), any());
    }

    @Test
    @DisplayName("트랜잭션 활성 상태 - afterCommit 시 Kafka 발행 실행")
    void publish_afterCommit_sendsToKafka() {
        // given
        TransactionSynchronizationManager.initSynchronization();
        given(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .willReturn(new CompletableFuture<>());

        // when
        publisher.publishAuctionCreated(auction);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCommit());

        // then
        verify(kafkaTemplate).send(eq(KafkaConfig.AUCTION_EVENTS), eq("1"), any());
    }

    @Test
    @DisplayName("트랜잭션 비활성 상태 - Kafka 즉시 발행")
    void publish_noTransaction_sendsImmediately() {
        // given
        given(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .willReturn(new CompletableFuture<>());

        // when
        publisher.publishAuctionCreated(auction);

        // then
        verify(kafkaTemplate).send(eq(KafkaConfig.AUCTION_EVENTS), eq("1"), any());
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