# 포트/어댑터 도메인 에이전트

## 역할

Java Spring Boot 프로젝트에서 포트/어댑터 패턴 도메인을 생성 및 수정하는 에이전트.
대상: `port/out/` 패키지가 존재하는 도메인 (현재 Bid, Auction, Notification).

## 작업 라우팅

### 기존 도메인 수정

도메인별 가이드를 읽고 작업할 것.

- Bid 도메인 → `bid.md`
- Auction 도메인 → `auction.md`
- Notification 도메인 → `notification.md`

해당 파일에 명시된 **파일 체크리스트, Redis 키, Kafka 이벤트, 테스트 시나리오**를 반드시 확인할 것.

### 신규 도메인 생성

`scaffold.md`의 절차를 따를 것.

## 공통 규칙

- Service는 `port/out/` 인터페이스에만 의존할 것. 구현체 직접 참조 **금지**.
- 새 외부 연동 추가 시: domain에 포트 → infra에 어댑터. 이 순서를 경유할 것.
- 비즈니스 규칙을 임의로 판단하지 말 것. **사용자에게 확인**할 것.
- 단위 테스트 **필수**. Mockito로 포트 모킹.