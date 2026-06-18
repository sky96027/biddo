# 레이어드 도메인 생성 절차

## 전제 조건

- 외부 시스템 의존이 단순한(DB 중심) 도메인에 적용할 것.
- 외부 의존이 3개 이상이면 포트/어댑터 검토. `port-adapter/scaffold.md` 참조.
- 생성 전 **사용자에게 엔티티 필드, 비즈니스 규칙을 확인**할 것.

## 생성 순서

domain 먼저, api 다음.

### 1단계: domain 엔티티

```
biddo-domain/src/main/java/com/biddo/domain/{도메인}/
├── entity/
│   └── {Domain}.java              # @Entity, BaseTimeEntity 상속
├── exception/
│   ├── {Domain}ErrorCode.java     # enum implements ErrorCode
│   └── {Domain}NotFoundException.java  # extends BusinessException
```

- 엔티티는 자기 불변식을 보호할 것.
- ErrorCode는 `int getStatus()` 사용. HttpStatus **금지**.

### 2단계: domain 리포지토리

```
biddo-domain/src/main/java/com/biddo/domain/{도메인}/
├── repository/
│   └── {Domain}Repository.java    # JpaRepository<{Domain}, Long> 상속
```

- Spring Data JPA Repository 직접 상속.
- 커스텀 쿼리는 `@Query` 또는 메서드 네이밍으로 정의.

### 3단계: domain 서비스

```
biddo-domain/src/main/java/com/biddo/domain/{도메인}/
├── service/
│   └── {Domain}Service.java
```

- `@Service`, `@Transactional`, `@RequiredArgsConstructor`.
- 읽기 전용 메서드에 `@Transactional(readOnly = true)`.

### 4단계: api 레이어

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

- DTO에는 형식 검증만. 비즈니스 규칙 어노테이션 **금지**.
- 응답은 `ApiResponse<T>`로 래핑.

### 5단계: 테스트

```
biddo-domain/src/test/java/com/biddo/domain/{도메인}/
├── service/
│   └── {Domain}ServiceTest.java
```

- 단위 테스트 **필수**. Mockito로 Repository 모킹.

## 완료 후

- 생성된 파일 목록을 사용자에게 보고할 것.
- infra 모듈에 도메인 전용 디렉토리(`infra/{도메인}/`)를 생성하지 않았는지 확인할 것.
- Kafka Consumer, SSE 등 기술 연동이 필요하면 기술별 디렉토리(`infra/kafka/consumer/`, `infra/sse/` 등)에 배치할 것. 포트 인터페이스 없이 직접 구현.