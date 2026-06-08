# Issue Tracking

프로젝트 진행 중 발생한 주요 이슈와 해결 과정을 기록합니다.

---

## ISS-001: Testcontainers Docker API 버전 호환성 오류

| 항목 | 내용 |
|---|---|
| **발생일** | 2026-05-26 |
| **상태** | ✅ 해결 |
| **영향 범위** | 통합 테스트 전체 (Testcontainers 기반) |
| **환경** | Docker Desktop 4.63.0, Docker Engine 29.2.1 (API 1.53), Testcontainers 1.20.6, docker-java 3.4.1 |

### 개요

통합 테스트 실행 시 Testcontainers가 Docker Engine에 연결하지 못하고 `IllegalStateException: Could not find a valid Docker environment` 에러로 전체 실패하는 문제가 발생했다.

메인 애플리케이션은 Docker 컨테이너(PostgreSQL, Redis, Kafka)에 정상 연결되었기 때문에, Docker 자체의 문제가 아닌 **Testcontainers ↔ Docker Engine API 간 통신 문제**로 범위를 좁힐 수 있었다.

### 에러 로그

```
통합: 경매 전체 생명주기 > initializationError FAILED
    java.lang.IllegalStateException at DockerClientProviderStrategy.java:274
```

```
ERROR org.testcontainers.dockerclient.DockerClientProviderStrategy --
Could not find a valid Docker environment. Please check configuration.
Attempted configurations were:
    EnvironmentAndSystemPropertyClientProviderStrategy: failed with exception
        BadRequestException (Status 400: {"ID":"","Containers":0,"ContainersRunning":0,
        "ContainersPaused":0,"ContainersStopped":0,"Images":0,"Driver":"",
        "DriverStatus":null,...,"ServerVersion":"","Runtimes":null,...})
    NpipeSocketClientProviderStrategy: failed with exception
        BadRequestException (Status 400: {"ID":"","Containers":0,...})
As no valid configuration was found, execution cannot continue.
```

**핵심**: Docker API 응답이 Status **400**이며, 모든 필드가 **빈 값**(`ID:""`, `Containers:0`, `ServerVersion:""`)으로 반환되었다.

### 해결 과정

#### 1단계: 문제 분류

| 구분 | 연결 방식 | 결과 |
|---|---|---|
| 메인 앱 | `localhost:5432/6379/9092` 포트 직접 연결 | ✅ 정상 |
| Testcontainers | Docker Engine API (named pipe) 호출 | ❌ 400 에러 |

메인 앱은 이미 실행 중인 컨테이너의 **포트에 직접 연결**하므로 Docker API가 불필요하다.
Testcontainers는 새 컨테이너를 생성하기 위해 **Docker Engine API를 호출**하는 과정에서 실패했다.

#### 2단계: 연결 경로 시도 (named pipe → TCP)

Docker Desktop의 TCP 소켓(`tcp://localhost:2375`)을 활성화하고, 환경변수·시스템 프로퍼티·`testcontainers.properties` 등 다양한 방식으로 전달을 시도했으나, **docker-java 라이브러리가 설정을 반영하지 않거나** 반영해도 동일한 400 에러가 발생했다.

```bash
# 환경변수 전달 확인 — JVM 내부에서는 정상 인식됨
ENV DOCKER_HOST: tcp://localhost:2375
PROP DOCKER_HOST: tcp://localhost:2375

# 그러나 Testcontainers는 여전히 동일한 에러
EnvironmentAndSystemPropertyClientProviderStrategy: failed with exception BadRequestException (Status 400: ...)
NpipeSocketClientProviderStrategy: failed with exception BadRequestException (Status 400: ...)
```

#### 3단계: 근본 원인 특정

Docker API 엔드포인트에 **다양한 API 버전**으로 직접 요청하여 원인을 확정했다.

```bash
# Docker 29.2.1의 MinAPIVersion = 1.44

$ curl -s -w "\nHTTP: %{http_code}" http://localhost:2375/v1.43/info
HTTP: 400    # ← 1.44 미만 = 거부

$ curl -s -w "\nHTTP: %{http_code}" http://localhost:2375/v1.44/info
HTTP: 200    # ← 1.44 이상 = 정상

$ curl -s -w "\nHTTP: %{http_code}" http://localhost:2375/v9.99/info
HTTP: 400    # ← 존재하지 않는 버전 = 400 + 빈 응답 (Testcontainers 에러와 동일)
```

**근본 원인 확정:**
- **Docker Engine 29.0.0**에서 최소 API 버전이 **1.24 → 1.44**로 상향 (Breaking Change)
- Testcontainers 1.20.6에 내장된 docker-java(shaded)가 **API 1.32**로 요청
- Docker가 `1.32 < 1.44`이므로 **400 Bad Request** 반환
- 이는 [testcontainers-java #11210](https://github.com/testcontainers/testcontainers-java/issues/11210), [#11235](https://github.com/testcontainers/testcontainers-java/issues/11235)에 보고된 알려진 이슈

#### 4단계: 해결

`src/test/resources/docker-java.properties` 파일을 생성하여 docker-java가 사용하는 API 버전을 1.44로 강제 지정했다.

```properties
# biddo-api/src/test/resources/docker-java.properties
api.version=1.44
```

### 검토한 대안

| 방안 | 가능 여부 | 사유 |
|---|---|---|
| Testcontainers 2.x 업그레이드 | ❌ | Spring Boot 4.x 전용. Spring Boot 3.4.4와 호환되지 않음 |
| Testcontainers 1.21.3 업그레이드 | △ | Docker 29 호환 패치 포함이나 docker-java API 버전 자체는 동일. 단독으로는 해결 불가 |
| Docker Engine 다운그레이드 (29 → 28) | ❌ | 개발 환경 퇴보. 비현실적 |
| **docker-java.properties API 버전 지정** | **✅** | **프로젝트 내 배치 가능, 코드 변경 없음, 향후 제거 용이** |

### 결과

- 통합 테스트에서 Testcontainers가 Docker Engine에 정상 연결되어 PostgreSQL, Redis, Kafka 컨테이너를 생성·시작함
- `.docker-java.properties`(홈 디렉토리)가 아닌 프로젝트 내 `src/test/resources/`에 배치하여 팀원·CI 환경에서도 별도 설정 없이 동작
- Spring Boot 4.x + Testcontainers 2.x로 전환 시 `docker-java.properties` 제거 예정

### 향후 계획: Spring Boot 4.x 마이그레이션

현재 `docker-java.properties`는 임시 조치이며, 근본 해결은 **Spring Boot 4.x + Testcontainers 2.x** 전환이다.

#### 마이그레이션 경로

`Spring Boot 3.4.4 → 3.5.x → 4.0` (직접 4.0 점프는 비권장)

#### 주요 변경 사항

| 항목 | 현재 상태 | 변경 필요 사항 | 규모 |
|---|---|---|---|
| Java | 21 | 유지 | 없음 |
| Gradle | 9.2 | 유지 | 없음 |
| `@MockitoBean` | 이미 사용 중 | 유지 | 없음 |
| Testcontainers 2.x | 1.20.6 | import 경로 + artifact명 변경 (테스트만) | 소 |
| Spring Security 7 | SecurityFilterChain 확인 필요 | `authorizeHttpRequests()` 전환, CSRF 기본값 변경 | 소~중 |
| Jackson 3 | Kafka 이벤트, API 응답 등 JSON 직렬화 다수 사용 | `IOException` → `JacksonException` catch 변경, 직렬화 동작 검증 | 중 (사일런트 브레이킹) |

#### 전제 조건

- **통합 테스트 커버리지 확보 후 진행** — Jackson 3의 사일런트 브레이킹과 Security 기본값 변경은 런타임에서 발생하므로 테스트 없이는 감지 불가
- 3.5.x에서 deprecation 경고를 모두 제거한 뒤 4.0으로 전환 (3.x에서 deprecated된 API는 4.0에서 전량 삭제됨)

#### 참고 자료

- [Spring Boot 4.0 Migration Guide (공식)](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot 4 Breaking Changes 실전 분석](https://www.javacodegeeks.com/2026/05/spring-boot-4-migration-breaking-changes-new-defaultsand-what-actually-broke.html)
- [Spring Boot 4 대규모 마이그레이션 가이드](https://www.moderne.ai/blog/spring-boot-4x-migration-guide)

### 참고 자료

- [testcontainers-java #11210 — client version 1.32 is too old](https://github.com/testcontainers/testcontainers-java/issues/11210)
- [testcontainers-java #11235 — Docker Engine 29 incompatibility](https://github.com/testcontainers/testcontainers-java/issues/11235)
- [testcontainers-java #11212 — Docker 29.0.0 could not find a valid Docker environment](https://github.com/testcontainers/testcontainers-java/issues/11212)
- [Docker Engine 29 Release Notes — Breaking Changes](https://docs.docker.com/engine/release-notes/29.0/)

---

## ISS-002: 분산 락 내부 트랜잭션 경계 불일치

| 항목 | 내용 |
|---|---|
| **발생일** | 2026-05-08 |
| **상태** | ✅ 해결 |
| **영향 범위** | 입찰, 즉시 구매, 자동입찰 설정 (BidService) |
| **관련 커밋** | `b204f34` feat(bid): Redis 분산 락(Redisson) 적용 |

### 개요

동시 입찰 원자성 보장을 위해 Redis 분산 락(Redisson)을 도입했으나, 기존 `@Transactional` 구조와 충돌하여 **락 내부에서 트랜잭션이 의도대로 동작하지 않는 문제**가 발생했다.

### 문제 상황

BidService에 클래스 레벨 `@Transactional(readOnly = true)`가 선언된 상태에서, 분산 락을 적용하면 두 가지 문제가 발생했다.

**1) readOnly 트랜잭션에서 쓰기 시도**

```java
@Service
@Transactional(readOnly = true)  // 클래스 레벨
public class BidService {
    public Bid placeBid(...) {      // 이 메서드도 readOnly 상속
        auctionLockPort.executeWithLock(auctionId, () -> {
            // INSERT/UPDATE 실행 → readOnly 트랜잭션에서 쓰기 시도
        });
    }
}
```

**2) Spring 프록시 내부 호출 시 @Transactional 무시**

메서드 레벨에 `@Transactional`을 붙여도 해결되지 않았다. 분산 락 콜백 내부에서 `this.placeBidInternal()`을 호출하면 **프록시를 거치지 않으므로** `@Transactional`이 적용되지 않는다.

```java
public Bid placeBid(...) {
    auctionLockPort.executeWithLock(auctionId, () -> {
        this.placeBidInternal(...);  // 프록시 우회 → @Transactional 무시
    });
}

@Transactional  // 효과 없음 (프록시를 거치지 않는 내부 호출)
private Bid placeBidInternal(...) { ... }
```

### 해결

`TransactionTemplate`을 주입받아 락 내부에서 **프로그래밍 방식으로 트랜잭션 경계를 명시적으로 설정**했다.

```java
@Service
@RequiredArgsConstructor
public class BidService {  // 클래스 레벨 @Transactional 제거

    private final TransactionTemplate transactionTemplate;

    public Bid placeBid(Long auctionId, Long bidderId, Long bidAmount) {
        Bid[] result = new Bid[1];
        auctionLockPort.executeWithLock(auctionId, () ->
                result[0] = transactionTemplate.execute(status ->
                        placeBidInternal(auctionId, bidderId, bidAmount))
        );
        return result[0];
    }
}
```

이 구조에서 실행 순서는 다음과 같다:

```
1. Redis 분산 락 획득 (3초 대기, 5초 유지)
2. TransactionTemplate → 새 트랜잭션 시작
3. 입찰 로직 실행 (검증 → INSERT → UPDATE)
4. 트랜잭션 커밋
5. Redis 락 해제
```

### 왜 클래스 레벨 @Transactional로 해결할 수 없는가

단순히 클래스 레벨에 `@Transactional`(쓰기)을 걸고 읽기 메서드에만 `readOnly = true`를 붙이면 코드는 동작하지만, **락과 트랜잭션의 순서가 뒤집혀** 동시성 보장이 깨진다.

**클래스 레벨 @Transactional 적용 시:**

```
1. 트랜잭션 시작 (프록시가 placeBid 진입 시 열림)
2. Redis 락 획득
3. 비즈니스 로직 (SELECT → INSERT → UPDATE)
4. Redis 락 해제
5. 트랜잭션 커밋   ← 락 해제 후 커밋
```

4~5 사이에 다른 스레드가 락을 획득하면 **아직 커밋되지 않은 데이터**를 읽게 되어, 두 입찰자가 같은 현재가를 기준으로 입찰하는 정합성 문제가 발생한다.

**TransactionTemplate 적용 시 (현재 구현):**

```
1. Redis 락 획득
2. 트랜잭션 시작
3. 비즈니스 로직 (SELECT → INSERT → UPDATE)
4. 트랜잭션 커밋   ← 락 내부에서 커밋 완료
5. Redis 락 해제
```

반드시 `Lock → Tx → Commit → Unlock` 순서여야 하므로, 트랜잭션 경계를 락 내부에서 프로그래밍 방식으로 제어해야 한다.

### 검토한 대안

| 방안 | 가능 여부 | 사유 |
|---|---|---|
| 클래스 레벨 `@Transactional` + 메서드별 readOnly | ❌ | 트랜잭션이 락보다 먼저 열려 `Tx → Lock → Unlock → Commit` 순서가 되어 동시성 깨짐 |
| 메서드 레벨 `@Transactional` override | ❌ | 위와 동일한 순서 문제 + 프록시 내부 호출 시 어노테이션 무시됨 |
| `AopContext.currentProxy()` 자기 참조 | ❌ | 안티패턴. 코드 복잡도 증가, 테스트 어려움 |
| 락 로직을 별도 Bean으로 분리 | △ | 가능하나 서비스 분리 시 응집도 저하 |
| **TransactionTemplate 명시적 트랜잭션** | **✅** | **프록시 무관, Lock → Tx → Commit → Unlock 순서 보장** |

### 결과

- 분산 락 → 트랜잭션 → 비즈니스 로직 순서가 명확하게 보장됨
- `placeBid`, `buyNow`, `setAutoBid` 3개 메서드에 동일 패턴 적용
- 읽기 전용 메서드(`getBidHistory`)는 메서드 레벨 `@Transactional(readOnly = true)` 유지
- 통합 테스트(`ConcurrentBidIntegrationTest`)에서 10명 동시 입찰 정합성 검증 완료

---

## ISS-003: 비즈니스 검증 책임 분산 → 도메인 모델 통합

| 항목 | 내용 |
|---|---|
| **발생일** | 2026-04-03 ~ 2026-04-08 |
| **상태** | ✅ 해결 |
| **영향 범위** | Auction, Member, Review 엔티티 및 관련 DTO 전체 |
| **관련 커밋** | `0bdf09b` fix: 비즈니스 규칙 validation 보강, `7c5bba3` refactor: 도메인 모델로 이동 |

### 개요

초기 구현에서 비즈니스 규칙 검증이 **DTO의 Bean Validation 어노테이션에 분산**되어 있었다. 이로 인해 도메인 모델이 자기 불변식을 보호하지 못하고, 검증 누락·중복·불일치 문제가 발생했다.

### 문제 상황

**1) DTO에 비즈니스 규칙이 혼재**

```java
// AS-IS: DTO에 형식 검증과 비즈니스 규칙이 혼재
public class AuctionCreateRequest {
    @NotBlank           // 형식 검증
    private String title;

    @Min(1000)          // 비즈니스 규칙 (시작가 1,000원 이상)
    @Positive           // 비즈니스 규칙
    private Long startingPrice;
}
```

**2) 도메인 모델이 무방비 상태**

DTO 검증은 컨트롤러 경로(`@Valid`)에서만 작동한다. 그러나 엔티티에 데이터가 들어오는 경로는 컨트롤러만이 아니다:
- `AuctionService.endAuction()` → `ChatService.createRoom()` (서비스 간 내부 호출)
- `AuctionLifecycleScheduler` → `AuctionService.activateAuction()` (스케줄러)
- Kafka 컨슈머 → `BidService` (이벤트 기반 자동입찰)

이런 경로에서는 DTO를 거치지 않으므로, 비즈니스 규칙이 DTO에만 있으면 잘못된 값이 엔티티에 그대로 저장될 수 있었다.

**3) 검증 누락 발견**

PR #8(`fix/validation`)에서 다음 검증이 누락되어 있음을 발견했다:
- 경매 등록 기간 범위 (1시간~7일)
- 즉시구매가 > 시작가 제약
- 후기 작성 기한 (경매 종료 후 14일 이내)
- 입찰 히스토리 페이지네이션

### 해결

2단계에 걸쳐 검증 책임을 재배치했다.

**원칙:**
- **DTO**: 형식 검증만 (`@NotNull`, `@NotBlank`, `@Email`)
- **엔티티/모델**: 비즈니스 규칙 (값 범위, 상태 전이, 도메인 판단)
- **서비스**: DB 조회가 필요한 검증, 교차 엔티티 검증

**적용 예시 — Auction:**

```java
// TO-BE: 엔티티가 자기 불변식을 보호
public class Auction {
    private void validateStartingPrice(Long startingPrice) {
        if (startingPrice < 1000) {
            throw new BusinessException(AuctionErrorCode.INVALID_STARTING_PRICE);
        }
    }

    private void validateBuyNowPrice(Long buyNowPrice, Long startingPrice) {
        if (buyNowPrice != null && buyNowPrice <= startingPrice) {
            throw new BusinessException(AuctionErrorCode.BUY_NOW_PRICE_TOO_LOW);
        }
    }
}
```

### 결과

| 엔티티 | 추가된 검증 |
|---|---|
| `Auction` | `validateStartingPrice` (≥1,000), `validateBuyNowPrice` (> startingPrice), `updateImages` (1~10장), 경매 기간 (1시간~7일) |
| `Member` | `validateNickname` (2~50자), `validateIntroduction` (≤500자) |
| `Review` | `validateRating` (1~5), 작성 기한 (경매 종료 후 14일 이내) |
| `AuthService` | `validatePassword` (8~20자, 인코딩 전 원문 검증) |

- DTO에서 `@Min`, `@Size`, `@Positive`, `@Max` 등 비즈니스 규칙 어노테이션 전량 제거
- 어떤 경로로 데이터가 들어오든 엔티티가 불변식을 보호하는 구조 확보

---

## ISS-004: 멀티 모듈 JPA EntityScan 실패

| 항목 | 내용 |
|---|---|
| **발생일** | 2026-03-20 |
| **상태** | ✅ 해결 |
| **영향 범위** | 전체 엔티티 인식 (애플리케이션 기동 실패) |
| **관련 커밋** | `1548e60` fix(infra): JPA 멀티 모듈 EntityScan 추가 및 설정 보정 |

### 개요

멀티 모듈(`biddo-api`, `biddo-domain`, `biddo-infra`) 구조에서 `biddo-api`의 `BiddoApplication`이 `biddo-domain`에 위치한 엔티티(Auction, Member 등)를 인식하지 못해 **애플리케이션 기동에 실패**하는 문제가 발생했다.

### 문제 상황

Spring Boot의 `@SpringBootApplication`은 해당 클래스의 패키지(`com.biddo.api`)를 기준으로 컴포넌트를 스캔한다. `biddo-domain`의 엔티티는 `com.biddo.domain` 패키지에 있으므로 스캔 범위에 포함되지 않았다.

```
biddo-api/      com.biddo.api.BiddoApplication   ← 스캔 기점
biddo-domain/   com.biddo.domain.auction.model   ← 스캔 범위 밖
biddo-infra/    com.biddo.infra.config           ← 스캔 범위 밖
```

### 해결

`JpaConfig`에 `@EntityScan`과 `@EnableJpaRepositories`의 basePackages를 `com.biddo`로 지정하고, `BiddoApplication`의 `scanBasePackages`도 동일하게 설정했다.

```java
// biddo-infra/JpaConfig.java
@Configuration
@EnableJpaAuditing
@EntityScan(basePackages = "com.biddo")
@EnableJpaRepositories(basePackages = "com.biddo")
public class JpaConfig {}

// biddo-api/BiddoApplication.java
@SpringBootApplication(scanBasePackages = "com.biddo")
public class BiddoApplication { ... }
```

### 함께 해결한 문제

같은 커밋에서 다음 설정도 보정했다:

| 항목 | 변경 전 | 변경 후 | 사유 |
|---|---|---|---|
| `server.port` | 8080 | 9090 | 로컬 환경 포트 충돌 |
| `ddl-auto` | validate | update | 개발 단계에서 스키마 자동 생성 필요 |
| `defer-datasource-initialization` | 미설정 | true | `data.sql` 시드 데이터가 JPA 초기화 전에 실행되는 문제 |

---

## ISS-005: JPQL `:param IS NULL OR` 패턴 PostgreSQL 타입 추론 실패

| 항목 | 내용 |
|---|---|
| **발생일** | 2026-06-08 |
| **상태** | ✅ 해결 |
| **영향 범위** | 검색 DB fallback (`AuctionSearchFallback`) |
| **환경** | PostgreSQL 16, Hibernate 6.6.11, Spring Data JPA 3.4.4 |

### 개요

ES 장애 시 DB fallback으로 전환되는 검색 쿼리에서, 선택적 파라미터를 null로 전달하면 PostgreSQL이 파라미터 타입을 결정하지 못해 `INTERNAL_ERROR`가 발생하는 문제.

### 문제 상황

JPQL에서 동적 필터링을 위해 흔히 사용하는 패턴:

```java
@Query("""
    SELECT a FROM Auction a
    WHERE (:keyword IS NULL OR a.title LIKE CONCAT('%', :keyword, '%'))
    AND (:categoryId IS NULL OR a.category.id = :categoryId)
    AND (:endBefore IS NULL OR a.endTime <= :endBefore)
    ...
    """)
List<Auction> searchAuctions(@Param("keyword") String keyword, ...);
```

이 패턴은 **H2(테스트)에서는 정상 동작하지만 PostgreSQL에서는 실패**한다.

**에러 1단계**: `operator does not exist: character varying ~~ bytea`
- keyword가 null일 때 Hibernate가 LIKE 연산의 파라미터를 `bytea`로 전달
- CAST 추가로 해결 가능하지만 근본 해결 아님

**에러 2단계**: `could not determine data type of parameter $9`
- `endBefore`, `cursor` 등 null 파라미터의 타입을 PostgreSQL이 추론 불가
- `:param IS NULL` 구문은 타입 컨텍스트를 제공하지 않으므로 DB가 판단할 수 없음

### 근본 원인

| 항목 | 설명 |
|---|---|
| JPQL 한계 | JPQL은 정적 쿼리 → 모든 파라미터가 항상 바인딩됨 |
| PostgreSQL 엄격성 | H2와 달리 null 파라미터에도 명시적 타입을 요구 |
| 패턴 부적합 | `:param IS NULL OR` 패턴은 동적 조건 수가 많을수록 문제 발생 확률 증가 |

### 해결

`AuctionSearchFallback`을 `EntityManager` 기반 동적 쿼리로 교체. non-null 파라미터만 조건에 추가하여 타입 추론 문제를 원천 차단.

```java
StringBuilder jpql = new StringBuilder("SELECT a FROM Auction a ... WHERE a.status = 'ACTIVE'");
Map<String, Object> params = new HashMap<>();

if (condition.getKeyword() != null && !condition.getKeyword().isBlank()) {
    jpql.append(" AND a.title LIKE :keyword");
    params.put("keyword", "%" + condition.getKeyword() + "%");
}
if (condition.getMinPrice() != null) {
    jpql.append(" AND a.currentPrice >= :minPrice");
    params.put("minPrice", condition.getMinPrice());
}
// ... 나머지 조건도 동일하게 non-null일 때만 추가

TypedQuery<Auction> query = entityManager.createQuery(jpql.toString(), Auction.class);
params.forEach(query::setParameter);
```

### 함께 발견된 문제: SecurityConfig search permitAll 범위 과다

검색 fallback 수정 후 `/search/recent` API를 테스트하는 과정에서, 토큰 만료 시 403이 아닌 NPE(`userDetails is null`)가 발생하는 문제를 추가로 발견했다.

**원인**: SecurityConfig에서 `GET /api/v1/search/**`를 permitAll로 설정하여, 인증이 필요한 `/search/recent`까지 토큰 없이 컨트롤러에 도달.

```java
// 변경 전: /search 하위 모든 GET 허용
.requestMatchers(HttpMethod.GET, "/api/v1/search/**").permitAll()

// 변경 후: 검색 API만 허용
.requestMatchers(HttpMethod.GET, "/api/v1/search/auctions").permitAll()
```

**permitAll + 토큰 만료 조합의 위험성**:

| 상황 | permitAll | authenticated |
|---|---|---|
| 토큰 유효 | 정상 동작 | 정상 동작 |
| 토큰 만료/없음 | 컨트롤러 도달 → NPE | 403 반환 (명확한 에러) |

permitAll은 인증 실패를 숨기기 때문에, 와일드카드(`/**`) 사용 시 의도치 않은 경로까지 열릴 수 있다. 가능한 한 구체적인 경로를 지정해야 한다.

### 교훈

| 구분 | 내용 |
|---|---|
| `:param IS NULL OR` 패턴 | 파라미터 1~2개일 때만 안전. 다수의 선택적 필터에는 부적합 |
| 동적 쿼리 전략 | EntityManager 직접 빌드, Criteria API, QueryDSL 중 선택 |
| 테스트 DB 차이 | H2에서 통과해도 PostgreSQL에서 실패할 수 있으므로 Testcontainers로 실제 DB 테스트 필요 |
| permitAll 와일드카드 | `/**` 대신 구체적 경로 지정. 인증 필요 API가 허용 범위에 포함되지 않는지 확인 필요 |