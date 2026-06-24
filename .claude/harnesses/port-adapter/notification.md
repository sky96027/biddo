# 알림/가격 알림 수정 가이드

## 관련 파일 체크리스트

수정 시 영향 범위를 반드시 확인할 것.

| 레이어 | 파일 | 역할 |
|--------|------|------|
| **domain** | `notification/service/NotificationService.java` | 알림 핵심 로직 |
| **domain** | `notification/service/PriceAlertService.java` | 가격 알림 핵심 로직 |
| **domain** | `notification/entity/Notification.java` | 알림 엔티티 |
| **domain** | `notification/entity/NotificationType.java` | 알림 유형 enum |
| **domain** | `notification/entity/PriceAlert.java` | 가격 알림 엔티티 |
| **domain** | `notification/port/out/NotificationRepository.java` | 알림 DB 포트 |
| **domain** | `notification/port/out/PriceAlertRepository.java` | 가격 알림 DB 포트 |
| **domain** | `notification/port/out/NotificationPushPort.java` | 실시간 푸시 포트 (SSE) |
| **infra** | `notification/NotificationRepositoryImpl.java` | NotificationRepository 구현 |
| **infra** | `notification/PriceAlertRepositoryImpl.java` | PriceAlertRepository 구현 |
| **infra** | `sse/NotificationSseAdapter.java` | SSE 푸시 어댑터 |
| **infra** | `kafka/consumer/BidEventConsumer.java` | 입찰 이벤트 → 알림 생성 |
| **infra** | `kafka/consumer/AuctionEventConsumer.java` | 경매 이벤트 → 알림 생성 |
| **infra** | `kafka/consumer/PriceAlertConsumer.java` | 가격 알림 트리거 |
| **infra** | `kafka/consumer/KeywordAlertConsumer.java` | 키워드 알림 트리거 |
| **api** | `notification/controller/NotificationController.java` | 알림 REST + SSE 엔드포인트 |
| **api** | `notification/controller/PriceAlertController.java` | 가격 알림 REST 엔드포인트 |
| **test** | `notification/service/NotificationServiceTest.java` | 알림 단위 테스트 |
| **test** | `notification/service/PriceAlertServiceTest.java` | 가격 알림 단위 테스트 |

## 수정 전 확인

- NotificationService와 PriceAlertService의 역할 차이를 확인할 것.
- SSE 연결(NotificationSseAdapter)과 알림 저장(NotificationRepository)은 별도 포트임을 인지할 것.
- Kafka Consumer 4개가 각각 다른 이벤트를 소비하여 알림을 생성함. 수정 시 영향 범위를 확인할 것.

## 금지 사항

- NotificationType enum 값을 임의로 추가/삭제 **금지**. 사용자에게 확인할 것.
- SSE 엔드포인트는 `ApiResponse`가 아닌 `SseEmitter` 직접 반환. 이 예외를 제거하지 말 것.
- Consumer 추가/수정 시 기존 Consumer와의 처리 순서 충돌 여부를 확인할 것.