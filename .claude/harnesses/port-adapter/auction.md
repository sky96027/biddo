# 경매 생명주기 수정 가이드

## 관련 파일 체크리스트

수정 시 영향 범위를 반드시 확인할 것.

| 레이어 | 파일 | 역할 |
|--------|------|------|
| **domain** | `auction/service/AuctionService.java` | 경매 핵심 로직 |
| **domain** | `auction/entity/Auction.java` | 경매 엔티티 (상태 전이 포함) |
| **domain** | `auction/entity/AuctionStatus.java` | PENDING / ACTIVE / ENDED / CANCELLED / SOLD |
| **domain** | `auction/entity/AuctionImage.java` | 경매 이미지 |
| **domain** | `auction/port/out/AuctionRepository.java` | DB 포트 |
| **domain** | `auction/port/out/AuctionLifecyclePort.java` | Redis TTL 스케줄링 포트 |
| **domain** | `auction/port/out/AuctionEventPublisher.java` | Kafka 발행 포트 |
| **infra** | `auction/AuctionRepositoryImpl.java` | AuctionRepository 구현 |
| **infra** | `auction/AuctionJpaRepository.java` | Spring Data JPA |
| **infra** | `redis/RedisAuctionLifecycle.java` | TTL 기반 시작/종료 스케줄링 어댑터 |
| **infra** | `redis/RedisKeyExpirationListener.java` | TTL 만료 이벤트 수신 |
| **infra** | `kafka/KafkaAuctionEventPublisher.java` | Kafka 발행 어댑터 |
| **infra** | `kafka/consumer/AuctionEventConsumer.java` | 경매 이벤트 소비 |
| **infra** | `kafka/consumer/AuctionWebSocketConsumer.java` | 경매 → WebSocket 전파 |
| **infra** | `kafka/consumer/AuctionSearchConsumer.java` | 경매 → ES 동기화 |
| **infra** | `sse/AuctionSseService.java` | 카운트다운 SSE |
| **api** | `auction/controller/AuctionController.java` | REST 엔드포인트 |
| **test** | `auction/service/AuctionServiceTest.java` | 단위 테스트 |

## 관련 Redis 키

- `auction:start:{auctionId}` — TTL 만료 시 PENDING → ACTIVE
- `auction:end:{auctionId}` — TTL 만료 시 ACTIVE → ENDED
- `auction:{id}:current_price` — 현재 최고가
- `auction:{id}:bid_count` — 입찰 수
- `auction:popular` — 인기 경매 Sorted Set

## 관련 Kafka 이벤트

- **발행**: `auction-events` 토픽 (파티션 키: auctionId)
- **이벤트 타입**: `AUCTION_CREATED`, `AUCTION_ACTIVATED`, `AUCTION_UPDATED`, `AUCTION_ENDED`, `AUCTION_SOLD`, `AUCTION_CANCELLED`
- **소비자**: AuctionEventConsumer, AuctionWebSocketConsumer, AuctionSearchConsumer, KeywordAlertConsumer

## 상태 전이 관련 컴포넌트

상태 전이를 변경하면 아래 컴포넌트가 모두 영향받을 수 있음. 확인할 것.

| 전이 | 트리거 | 후속 컴포넌트 |
|------|--------|--------------|
| PENDING → ACTIVE | RedisKeyExpirationListener | AuctionEventConsumer, AuctionSearchConsumer, KeywordAlertConsumer |
| ACTIVE → ENDED | RedisKeyExpirationListener | AuctionEventConsumer, AuctionWebSocketConsumer, AuctionSearchConsumer |
| ACTIVE → SOLD | AuctionService | AuctionEventConsumer, AuctionWebSocketConsumer, AuctionSearchConsumer |
| PENDING → CANCELLED | AuctionService | AuctionSearchConsumer |

## 수정 전 확인

- 상태 전이 수정 시 **허용된 전이 경로를 Auction 모델에서 먼저 확인**할 것.
- Redis TTL 관련 수정 시 RedisAuctionLifecycle + RedisKeyExpirationListener를 함께 확인할 것.
- 경매 종료 처리 수정 시 낙찰자 결정, 채팅방 생성, 알림 발송 후속 흐름을 확인할 것.
- AuctionSearchConsumer 수정 시 ES 인덱스 정합성 및 CircuitBreaker 설정도 확인할 것.

## 금지 사항

- 상태 전이 규칙을 임의로 판단 **금지**. 사용자에게 확인할 것.
- 스나이핑 방지 연장 시간, 스케줄러 주기를 근거 없이 변경 **금지**.
- 경매 종료 시 후속 처리 순서 변경 시 사용자에게 보고할 것.
- Consumer 추가/수정 시 기존 Consumer와의 처리 순서 충돌 여부를 확인할 것.