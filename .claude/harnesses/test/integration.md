# 통합 테스트 가이드

## 기본 구조

```java
class {Test}IntegrationTest extends IntegrationTestBase {

    @Autowired
    private {Domain}Service {domain}Service;

    @Autowired
    private {Domain}Repository {domain}Repository;
}
```

## Testcontainers 규칙

- `IntegrationTestBase` 추상 클래스를 **반드시 상속**할 것.
- `@Testcontainers` + `@Container` 직접 사용 **금지**. 클래스별 컨테이너 재생성으로 Spring 컨텍스트 캐시와 충돌.
- 싱글톤 컨테이너 패턴: `IntegrationTestBase`의 `static {}` 블록에서 PostgreSQL, Redis, Kafka 컨테이너를 1회 시작.

## 데이터 격리

- `@Transactional` 또는 테스트 후 `TRUNCATE CASCADE`로 데이터 격리할 것.
- 테스트 간 데이터 의존 **금지**. 각 테스트가 자체 데이터를 세팅할 것.

## 통합 테스트 대상 판단 기준

아래 조건 중 하나라도 해당하면 통합 테스트 대상. 해당하지 않으면 단위 테스트로 충분.

| 조건 | 이유 |
|------|------|
| 외부 시스템 2개 이상 연동 | 단위 테스트 모킹으로는 연동 정합성 검증 불가 |
| 상태 전이 + 후속 처리 | 전이 자체는 단위로 가능하나 후속 체인은 통합으로만 검증 |
| 동시성/분산 락 | 단일 스레드 단위 테스트로 재현 불가 |
| 이벤트 발행 → 소비 체인 | Producer-Consumer 간 직렬화/역직렬화 + 처리 순서 검증 |

조건 판단이 애매하면 **사용자에게 확인**할 것. 시나리오를 임의로 결정 **금지**.

## 금지 사항

- Mockito 모킹 **금지**. 실제 인프라로 검증하는 것이 통합 테스트의 목적.
- 테스트 실행 순서에 의존하는 코드 **금지**.
- `Thread.sleep()`으로 비동기 결과 대기 **금지**. `Awaitility` 등 폴링 도구를 사용할 것.