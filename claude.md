중고 상품 실시간 경매 플랫폼. 개발자 포트폴리오 시연 목적으로 제작.

---

## 기술 스택

- **Backend**: Java 17+, Spring Boot 3.x, Spring Data JPA (Hibernate), Gradle
- **DB**: PostgreSQL, Redis (Lettuce + Redisson)
- **Messaging**: Apache Kafka
- **Real-time**: WebSocket (입찰/채팅), SSE (알림)
- **Search**: Elasticsearch
- **Storage**: S3 + CloudFront + Lambda@Edge
- **Infra**: EC2 x2 + ALB, GitHub Actions CI/CD, Docker

---

## 프로젝트 구조

```
biddo/
├── biddo-api/       # Controller, DTO, 인증/인가
├── biddo-domain/    # Entity, Service, Port 인터페이스
├── biddo-infra/     # 외부 시스템 연동 + 포트 구현체
└── docs/            # 설계 문서 (인간용)
```

**의존성**: `biddo-api → biddo-domain, biddo-infra` / `biddo-infra → biddo-domain`

아키텍처·모듈 규칙은 `.claude/rules/architecture.md` 참조할 것.

---

## 작업 지침

도메인 작업 시 해당 패턴의 harness를 먼저 읽을 것.

- `port/out/` 패키지가 존재하는 도메인 → `.claude/harnesses/port-adapter/prompt.md`
- 그 외 도메인 → `.claude/harnesses/layered/prompt.md`

harness 파일은 자유 접근. docs/와 달리 사용자 허가 불필요.

---

## docs/ 접근 정책

- `docs/` 디렉토리의 파일을 직접 읽는 것을 **금지**한다.
- 비즈니스 규칙, ERD, API 명세 등 상세 정보가 필요하면 **사용자에게 질문**할 것.
- 사용자가 명시적으로 읽기를 지시한 경우에만 허용.

---

## 코딩 컨벤션

### 네이밍

- 클래스: PascalCase (`AuctionService`)
- 메서드: camelCase, 동사 시작 (`createAuction()`)
- 상수: UPPER_SNAKE_CASE (`MAX_BID_RETRY`)
- 패키지: `com.biddo.{api|domain|infra}.{도메인}`
- DTO: `*Request` / `*Response`
- 예외: `도메인명 + Exception` (`AuctionNotFoundException`)

### 예외 처리

- `ErrorCode` 인터페이스 (`int getStatus()`, `String getCode()`, `String getMessage()`)
- 도메인별 `enum implements ErrorCode`
- `BusinessException extends RuntimeException`
- `@RestControllerAdvice GlobalExceptionHandler`

### 도메인 모델 책임

- **엔티티/모델**: 값 검증, 상태 전이, 도메인 판단, 단순 계산
- **서비스**: DB 조회 필요한 검증, 인코딩 전 원문 검증, 교차 엔티티 검증
- **DTO**: 형식 검증만 (`@NotNull`, `@NotBlank`, `@Email`). 비즈니스 규칙 어노테이션 금지

### 테스트

- 단위: JUnit 5 + Mockito
- 통합: Testcontainers (PostgreSQL, Redis, Kafka)
- 부하: K6

### Git

- 브랜치: `main` → `develop` → `feature/{도메인}-{기능}`
- 커밋: `<type>(<scope>): <subject>` (feat, fix, docs, style, refactor, test, chore)