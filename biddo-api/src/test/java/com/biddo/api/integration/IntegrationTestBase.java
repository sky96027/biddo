package com.biddo.api.integration;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.model.AuctionStatus;
import com.biddo.domain.auction.model.ItemCondition;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.category.entity.Category;
import com.biddo.domain.category.repository.CategoryRepository;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.repository.MemberRepository;
import com.biddo.infra.elasticsearch.AuctionSearchAdapter;
import com.biddo.infra.elasticsearch.repository.AuctionDocumentRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;

@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("biddo_test")
            .withUsername("test")
            .withPassword("test");

    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    static {
        postgres.start();
        redis.start();
        kafka.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @MockitoBean
    protected AuctionSearchAdapter auctionSearchAdapter;

    @MockitoBean
    protected AuctionDocumentRepository auctionDocumentRepository;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected MemberRepository memberRepository;

    @Autowired
    protected CategoryRepository categoryRepository;

    @Autowired
    protected AuctionRepository auctionRepository;

    protected Member createMember(String email, String nickname) {
        return memberRepository.save(
                Member.builder()
                        .email(email)
                        .password("$2a$10$encodedPassword")
                        .nickname(nickname)
                        .build());
    }

    protected Category createCategory(String name) {
        return categoryRepository.save(
                Category.builder()
                        .name(name)
                        .depth(0)
                        .sortOrder(0)
                        .build());
    }

    protected Auction createActiveAuction(Member seller, Category category,
                                           Long startingPrice, Long buyNowPrice) {
        Auction auction = Auction.builder()
                .seller(seller)
                .category(category)
                .title("테스트 경매")
                .description("통합 테스트용 경매 상품")
                .condition(ItemCondition.GOOD)
                .startingPrice(startingPrice)
                .buyNowPrice(buyNowPrice)
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusHours(23))
                .build();
        auction.activate();
        return auctionRepository.save(auction);
    }

    protected Auction createSnipingAuction(Member seller, Category category,
                                            Long startingPrice) {
        Auction auction = Auction.builder()
                .seller(seller)
                .category(category)
                .title("스나이핑 테스트 경매")
                .description("종료 임박 테스트용")
                .condition(ItemCondition.GOOD)
                .startingPrice(startingPrice)
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusMinutes(5))
                .build();
        auction.activate();
        return auctionRepository.save(auction);
    }
}