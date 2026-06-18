# 포트/어댑터 도메인 생성 절차

## 전제 조건

- 외부 시스템 의존이 3개 이상이거나 비즈니스 규칙이 복잡한 도메인에만 적용할 것.
- 단순 도메인에는 레이어드 패턴을 사용할 것. `layered/scaffold.md` 참조.
- 생성 전 **사용자에게 엔티티 필드, 포트 목록, 비즈니스 규칙을 확인**할 것.

## 생성 순서

아래 순서를 반드시 지킬 것. domain 먼저, infra 다음, api 마지막.

### 1단계: domain 모델

```
biddo-domain/src/main/java/com/biddo/domain/{도메인}/
├── model/
│   └── {Domain}.java              # 엔티티 (@Entity, BaseTimeEntity 상속)
├── exception/
│   ├── {Domain}ErrorCode.java     # enum implements ErrorCode
│   └── {Domain}NotFoundException.java  # extends BusinessException
```

- 엔티티는 자기 불변식을 보호할 것 (값 검증, 상태 전이 메서드).
- ErrorCode는 `int getStatus()` 사용. HttpStatus **금지**.

### 2단계: domain 포트

```
biddo-domain/src/main/java/com/biddo/domain/{도메인}/
├── port/out/
│   └── {Domain}Repository.java    # 순수 Java 인터페이스
```

- Spring 어노테이션 **금지**. 순수 Java 인터페이스만.
- 외부 시스템별로 포트를 분리할 것 (DB 포트, 이벤트 포트, 캐시 포트 등).

### 3단계: domain 서비스

```
biddo-domain/src/main/java/com/biddo/domain/{도메인}/
├── service/
│   └── {Domain}Service.java       # 포트 인터페이스에만 의존
```

- `@Service`, `@Transactional`, `@RequiredArgsConstructor`.
- 읽기 전용 메서드에 `@Transactional(readOnly = true)`.
- 구현체 직접 참조 **금지**.

### 4단계: infra 어댑터

```
biddo-infra/src/main/java/com/biddo/infra/{도메인}/
├── {Domain}JpaRepository.java     # JpaRepository 상속
├── {Domain}RepositoryImpl.java    # 포트 구현체 (@Repository)
```

- JpaRepository는 내부용. 포트 구현체가 위임하는 구조.
- Kafka, Redis 어댑터가 필요하면 `com.biddo.infra.kafka/`, `com.biddo.infra.redis/`에 배치.

### 5단계: api 레이어

```
biddo-api/src/main/java/com/biddo/api/{도메인}/
├── controller/
│   └── {Domain}Controller.java
├── dto/
│   ├── request/
│   │   ├── {Domain}CreateRequest.java
│   │   └── {Domain}UpdateRequest.java
│   └── response/
│       └── {Domain}Response.java
```

- DTO에는 형식 검증만 (`@NotNull`, `@NotBlank`). 비즈니스 규칙 어노테이션 **금지**.
- 응답은 `ApiResponse<T>`로 래핑.

### 6단계: 테스트

```
biddo-domain/src/test/java/com/biddo/domain/{도메인}/
├── service/
│   └── {Domain}ServiceTest.java   # Mockito로 포트 모킹
```

- 단위 테스트 **필수**. 포트 모킹으로 domain 독립 검증.
- 통합 테스트는 필요 시 Testcontainers로 작성.

## 완료 후

- 생성된 파일 목록을 사용자에게 보고할 것.
- 누락된 포트나 어댑터가 없는지 확인할 것.