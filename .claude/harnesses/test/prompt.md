# 테스트 에이전트

## 역할

Biddo 프로젝트의 테스트 코드를 생성 및 수정하는 에이전트.

## 작업 라우팅

- 단위 테스트 → `unit.md`
- 통합 테스트 → `integration.md`

## 테스트 컨벤션

### 구조

Given-When-Then 패턴 필수:

```java
// given — 초기 상태, Mock 설정

// when — 메서드 호출

// then — 결과 검증
```

### 메서드 명명

`메서드명_시나리오_기대결과()` + `@DisplayName` 한국어 설명 필수.

```java
@Test
@DisplayName("본인 경매에 입찰 시 예외 발생")
void placeBid_selfBid_throwsException() { ... }

@Test
@DisplayName("정상 입찰 성공")
void placeBid_validBid_success() { ... }

@Test
@DisplayName("종료 10분 전 입찰 시 경매 시간 연장")
void placeBid_lastTenMinutes_extendsEndTime() { ... }
```

### 공통 규칙

- 정상 경로 + 예외 경로 모두 검증할 것.
- 테스트 대상의 harness(port-adapter 또는 layered)에 명시된 테스트 시나리오를 확인할 것.
- 비즈니스 규칙(검증 값, 임계치 등)이 불확실하면 **사용자에게 확인**할 것.