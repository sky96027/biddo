# 아키텍처 규칙

## 모듈 의존성

- `biddo-domain`은 외부 프레임워크(Spring Web, Kafka, Redis 클라이언트 등)에 의존 **금지**.
- `biddo-infra` → `biddo-domain` 방향만 허용. 역방향 의존 **금지**.
- `biddo-api` → `biddo-domain`, `biddo-infra` 의존 허용.

## 모듈화 전략

- 레이어별 모듈화(api, domain, infra) 채택. 도메인별 모듈화 아님.
- domain ↔ infra 경계의 컴파일 타임 강제가 목적.
- 크로스 도메인 참조(Member, Auction)가 많아 도메인 경계 모듈화의 실효성이 낮음.

## 포트/어댑터 (Bid, Auction)

- `biddo-domain` 내 `port/out/` 인터페이스 정의 → `biddo-infra`에서 구현.
- Service는 포트 인터페이스에만 의존할 것. 구현체 직접 참조 **금지**.
- 새 외부 시스템 연동 시: domain에 포트 추가 → infra에 어댑터 구현. 이 순서를 지킬 것.

## 레이어드 (Member, Chat, Notification, Review, Report)

- `entity/`, `repository/`, `service/`, `exception/` 구조.
- Spring Data JPA Repository 직접 상속 허용.
- 단순 도메인에 포트/어댑터 도입 **금지**. 보일러플레이트만 증가.

## ErrorCode

- `int getStatus()` 사용. `HttpStatus` 사용 **금지** (domain의 spring-web 의존 제거 목적).

## 새 클래스 배치 기준

| 클래스 유형 | 모듈 | 경로 |
|------------|------|------|
| 엔티티/모델 | domain | `com.biddo.domain.{도메인}.model/` 또는 `entity/` |
| 포트 인터페이스 | domain | `com.biddo.domain.{도메인}.port.out/` |
| 서비스 | domain | `com.biddo.domain.{도메인}.service/` |
| ErrorCode enum | domain | `com.biddo.domain.{도메인}.exception/` |
| JPA Repository | infra | `com.biddo.infra.{도메인}/` |
| 포트 구현체 | infra | `com.biddo.infra.{도메인}/` 또는 `com.biddo.infra.{기술}/` |
| Kafka Consumer | infra | `com.biddo.infra.kafka.consumer/` |
| Kafka Event DTO | infra | `com.biddo.infra.kafka.event/` |
| Redis 어댑터 | infra | `com.biddo.infra.redis/` |
| Controller | api | `com.biddo.api.{도메인}.controller/` |
| Request/Response DTO | api | `com.biddo.api.{도메인}.dto.request/` 또는 `response/` |

## 배치 판단이 애매한 경우

- infra 기술 전용 클래스(Config, Listener 등) → `com.biddo.infra.{기술}/`에 배치.
- 여러 도메인이 공유하는 클래스 → `common/` 패키지에 배치.
- 판단이 불확실하면 **사용자에게 확인**할 것.