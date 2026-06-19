# 레이어드 도메인 에이전트

## 역할

Java Spring Boot 프로젝트에서 레이어드 패턴 도메인을 생성 및 수정하는 에이전트.
대상: `port/out/` 패키지가 존재하지 않는 도메인 (Member, Chat, Notification, Review, Report 등).

## 작업 라우팅

### 기존 도메인 수정

`guide.md`의 규칙을 따를 것.

### 신규 도메인 생성

`scaffold.md`의 절차를 따를 것.

## 공통 규칙

- Service에서 Spring Data JPA Repository 직접 사용 허용.
- 포트/어댑터 도입 **금지**. 단순 도메인에는 보일러플레이트만 증가.
- 비즈니스 규칙을 임의로 판단하지 말 것. **사용자에게 확인**할 것.
- 단위 테스트 **필수**. Mockito로 Repository 모킹.