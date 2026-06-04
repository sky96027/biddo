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