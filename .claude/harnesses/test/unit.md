# 단위 테스트 가이드

## 기본 구조

```java
@ExtendWith(MockitoExtension.class)
class {Domain}ServiceTest {

    @Mock
    private {Domain}Repository {domain}Repository;

    @InjectMocks
    private {Domain}Service {domain}Service;
}
```

## 포트/어댑터 도메인 (Bid, Auction, Notification)

- `port/out/` 인터페이스를 `@Mock`으로 모킹할 것.
- 구현체(RedissonAuctionLock, KafkaBidEventPublisher, NotificationSseAdapter 등)를 테스트에서 참조 **금지**.
- 모킹 대상 예시:
  - `BidRepository`, `AutoBidRepository`, `BidEventPublisher`, `AuctionLockPort`
  - `AuctionRepository`, `AuctionLifecyclePort`, `AuctionEventPublisher`
  - `NotificationRepository`, `PriceAlertRepository`, `NotificationPushPort`

## 레이어드 도메인 (Member, Chat 등)

- Spring Data JPA Repository를 `@Mock`으로 모킹할 것.
- `@SpringBootTest` 사용 **금지**. 순수 단위 테스트만.

## 검증 패턴

```java
// 예외 검증
assertThatThrownBy(() -> service.doSomething(invalidInput))
    .isInstanceOf(BusinessException.class)
    .hasMessageContaining("expected message");

// 메서드 호출 검증
verify(repository).save(any({Domain}.class));
verify(eventPublisher, never()).publish(any());
```

## 금지 사항

- `@SpringBootTest`, `@DataJpaTest` 등 스프링 컨텍스트 로딩 **금지**.
- 실제 DB, Redis, Kafka 연결 **금지**. 통합 테스트에서 할 것.
- 테스트 간 상태 공유 **금지**. 각 테스트는 독립적이어야 함.