# Kafka 운영 안정성 개선

> **상태**: 미결정
> **배경**: Consumer 간 격리는 Kafka Consumer Group 구조상 이미 달성되어 있으나, 실패 처리와 모니터링이 부재

---

## 현황

`bid-events` 토픽을 5개 Consumer Group이 소비 중:

| Consumer Group | Consumer | 역할 |
| --- | --- | --- |
| `biddo-notification` | BidEventConsumer | 입찰 알림 발송 |
| `biddo-websocket` | BidWebSocketConsumer | 실시간 입찰 현황 푸시 |
| `search-sync` | AuctionSearchConsumer | ES 인덱스 동기화 |
| `popular-auction` | PopularAuctionConsumer | 인기 경매 집계 |
| `biddo-price-alert` | PriceAlertConsumer | 가격 알림 |

각 Consumer Group은 독립적인 offset을 가지므로 한 Group의 Lag이 다른 Group의 소비에 직접적인 영향은 없다. 그러나 다음 세 가지가 정의되어 있지 않다.

## 미비 사항

### 1. Consumer 실패 시 처리 정책 미정의

Consumer가 메시지 처리에 실패했을 때의 동작이 정의되어 있지 않다. 현재 예외 발생 시 Spring Kafka 기본 동작(최대 10회 재시도 후 로그만 남기고 스킵)에 의존하고 있으며, 실패한 메시지를 별도로 추적하거나 재처리하는 구조가 없다.

- 검색 동기화 실패 → ES와 DB 간 데이터 불일치 잔존
- 알림 실패 → 사용자에게 입찰 알림 누락
- WebSocket 실패 → 실시간 현황 갱신 누락 (일시적, 새로고침으로 복구 가능)

Consumer별 실패 영향도가 다르므로 일률적인 재시도 정책보다 Consumer별 정책 분리가 필요할 수 있다.

### 2. Lag 모니터링 부재

Consumer Group별 Lag을 수집하는 메트릭이 없다. 특정 Consumer가 지연되고 있는지, 정상 소비 중인지 확인할 방법이 없다.

### 3. 브로커 보존 설정 미명시

`docker-compose.yml`에 `log.retention.bytes`가 설정되어 있지 않아 Kafka 기본값(무제한)이 적용 중이다. 프로듀서 쓰기가 폭주할 경우 디스크가 가득 찰 수 있다.

현재 적용 중인 기본값:

| 설정 | 값 | 의미 |
| --- | --- | --- |
| `log.retention.hours` | 168 (7일) | 메시지 보존 기간 |
| `log.retention.bytes` | -1 (무제한) | 파티션당 최대 크기 |

## 검토 방향

- Consumer별 DLQ(Dead Letter Queue) 및 재시도 정책 정의
- Prometheus + kafka-exporter를 통한 Consumer Group별 Lag 모니터링
- `log.retention.bytes` 명시적 설정
- 실패 영향도에 따른 Consumer 우선순위 분류 (검색 동기화 > 알림 > WebSocket)