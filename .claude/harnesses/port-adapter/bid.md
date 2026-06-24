# 입찰/자동입찰 수정 가이드

## 관련 파일 체크리스트

수정 시 영향 범위를 반드시 확인할 것.

| 레이어 | 파일 | 역할 |
|--------|------|------|
| **domain** | `bid/service/BidService.java` | 입찰 핵심 로직 |
| **domain** | `bid/entity/Bid.java` | 입찰 엔티티 |
| **domain** | `bid/entity/AutoBid.java` | 자동입찰 엔티티 |
| **domain** | `bid/entity/BidType.java` | MANUAL / AUTO / BUY_NOW |
| **domain** | `bid/port/out/BidRepository.java` | DB 포트 |
| **domain** | `bid/port/out/AutoBidRepository.java` | 자동입찰 DB 포트 |
| **domain** | `bid/port/out/BidEventPublisher.java` | Kafka 발행 포트 |
| **domain** | `bid/port/out/AuctionLockPort.java` | 분산 락 포트 |
| **infra** | `bid/BidRepositoryImpl.java` | BidRepository 구현 |
| **infra** | `bid/AutoBidRepositoryImpl.java` | AutoBidRepository 구현 |
| **infra** | `kafka/KafkaBidEventPublisher.java` | Kafka 발행 어댑터 |
| **infra** | `redis/RedissonAuctionLock.java` | Redisson 분산 락 어댑터 |
| **infra** | `kafka/consumer/BidEventConsumer.java` | 입찰 이벤트 소비 |
| **infra** | `kafka/consumer/BidWebSocketConsumer.java` | 입찰 → WebSocket 전파 |
| **api** | `bid/controller/BidController.java` | REST 엔드포인트 |
| **test** | `bid/service/BidServiceTest.java` | 단위 테스트 |

## 관련 Redis 키

- `auction:lock:{auctionId}` — 분산 락
- `auction:{id}:current_price` — 현재 최고가
- `auction:{id}:bid_count` — 입찰 수
- `auction:{id}:bidders` — 입찰 참여자 Set
- `auction:{id}:top_bidder` — 최고 입찰자 Hash

## 관련 Kafka 이벤트

- **발행**: `bid-events` 토픽 (파티션 키: auctionId)
- **이벤트 타입**: `BID_PLACED`, `AUTO_BID_EXHAUSTED`
- **소비자**: BidEventConsumer, BidWebSocketConsumer, PopularAuctionConsumer, PriceAlertConsumer

## 수정 전 확인

- BidService 수정 시 **분산 락 처리 흐름을 먼저 확인**할 것.
- 자동입찰 연쇄 방지 로직이 존재함. 수정 전 기존 방어 코드를 확인할 것.
- bid_type(MANUAL, AUTO, BUY_NOW) 분기를 확인할 것. 타입별 처리 경로가 다름.
- BidEventConsumer와 BidWebSocketConsumer의 역할 차이를 확인할 것.

## 금지 사항

- 비즈니스 규칙(최소 증가 단위, 연쇄 제한 횟수 등)을 임의로 판단 **금지**. 사용자에게 확인할 것.
- 분산 락 타임아웃, 대기 시간을 근거 없이 변경 **금지**.
- bid_type=AUTO 이벤트 처리 흐름 변경 시 연쇄 방지 영향을 사용자에게 보고할 것.
- Consumer 추가/수정 시 기존 Consumer와의 처리 순서 충돌 여부를 확인할 것.