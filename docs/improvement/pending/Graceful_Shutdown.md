# Graceful Shutdown

> **상태**: 미결정
> **작성일**: 2025-06-22

---

## 현황

EC2 2대 + ALB 구성이 설계되어 있으나, 인스턴스 종료/재배포 시 안전한 종료 전략이 정의되어 있지 않다.

현재 종료 시 영향을 받는 컴포넌트:

| 컴포넌트 | 종료 시 영향 | 현재 대응 |
| --- | --- | --- |
| 분산 락 (`RedissonAuctionLock`) | lease time(5s) 내 미완료 콜백 유실 가능 | 없음 |
| WebSocket 연결 | 클라이언트 즉시 끊김, 입찰 현황 푸시 중단 | 없음 |
| SSE 연결 | 알림/카운트다운 스트리밍 중단 | 없음 |
| Kafka Consumer | 처리 중 메시지의 offset 미커밋 → 재처리 발생 가능 | 없음 |
| 스케줄러 | `AuctionLifecycleScheduler`, `TrustScoreScheduler` 실행 중 중단 | 없음 |
| 처리 중인 HTTP 요청 | 응답 없이 커넥션 종료 | 없음 |

## 미비 사항

### 1. Spring 종료 설정 미적용

`server.shutdown=graceful` 및 `spring.lifecycle.timeout-per-shutdown-phase` 설정이 없다. Spring Boot의 graceful shutdown을 활성화하면 새 요청 수락을 중단하고, 처리 중인 요청이 완료될 때까지 대기한 후 종료한다.

### 2. ALB 연계 미정의

ALB Health Check가 실패한 후 인스턴스로의 트래픽이 완전히 끊기기까지 시간이 소요된다. 애플리케이션이 ALB보다 먼저 종료되면 진행 중인 요청이 유실된다. ALB deregistration delay와 애플리케이션 종료 타이밍의 조율이 필요하다.

### 3. Kafka Consumer 종료 순서

Spring의 `SmartLifecycle`을 통해 Kafka Consumer를 HTTP 요청 처리보다 먼저 종료해야 한다. Consumer가 메시지를 처리하는 도중에 컨텍스트가 파괴되면 의존하는 서비스가 이미 소멸된 상태일 수 있다.

## 검토 방향

- `server.shutdown=graceful` + timeout 설정
- SIGTERM 수신 → Health Check 실패 응답 → ALB deregistration 대기 → 애플리케이션 종료 순서 설계
- Kafka Consumer, WebSocket, SSE의 종료 순서 정의 (`SmartLifecycle` phase 활용)
- 롤링 배포 시 최소 1대는 정상 상태를 유지하도록 GitHub Actions 배포 스크립트 설계