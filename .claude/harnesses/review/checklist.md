# 코드 리뷰 체크리스트

## 아키텍처 (architecture.md 기반)

### 모듈 의존성
- [ ] domain 모듈에서 Spring Web, Kafka, Redis 클라이언트 등 외부 프레임워크를 import하지 않는가?
- [ ] infra → domain 방향만 의존하는가? 역방향 의존이 없는가?

### 포트/어댑터 (Bid, Auction, Notification)
- [ ] Service가 `port/out/` 인터페이스에만 의존하는가? 구현체 직접 참조가 없는가?
- [ ] 새 외부 연동이 domain 포트 → infra 어댑터 순서로 추가되었는가?

### 레이어드 (Member, Chat, Review, Report)
- [ ] 불필요한 `port/out/` 패키지가 도입되지 않았는가?
- [ ] 다른 도메인 Repository를 직접 주입하지 않고 해당 도메인 Service를 경유하는가?

### ErrorCode
- [ ] `int getStatus()` 사용하는가? `HttpStatus` 직접 사용이 없는가?
- [ ] 기존 ErrorCode와 코드 중복이 없는가?

---

## 도메인 모델 책임

- [ ] 엔티티/모델이 자기 불변식을 보호하는가? (값 검증, 상태 전이 메서드)
- [ ] 서비스에서 엔티티가 해야 할 검증을 대신하고 있지 않는가?
- [ ] 엔티티에서 DB 조회가 필요한 검증을 하고 있지 않는가? (서비스 역할)

---

## DTO 검증

- [ ] Request DTO에 형식 검증만 사용하는가? (`@NotNull`, `@NotBlank`, `@Email`)
- [ ] 비즈니스 규칙 어노테이션(`@Min`, `@Size`, `@Positive`, `@Max`)이 DTO에 없는가?

---

## NULL 안전성

- [ ] Repository 조회 결과에 `orElseThrow()` 또는 적절한 null 처리가 있는가?
- [ ] Optional을 `get()`으로 직접 꺼내지 않는가?

---

## 성능

- [ ] Lazy 로딩 엔티티 접근 시 fetch join이 적용되어 있는가? (N+1 방지)
- [ ] 대량 데이터 조회에 Cursor 페이지네이션이 적용되어 있는가?
- [ ] Stream/Loop 내부에서 DB 조회가 반복되지 않는가?

---

## 보안

- [ ] JPQL/Native Query에 파라미터 바인딩을 사용하는가? (문자열 결합 금지)
- [ ] 본인 리소스 접근 검증이 있는가? (다른 사용자 리소스 수정/삭제 차단)
- [ ] 비밀번호, 토큰 등 민감 정보가 로그/응답에 노출되지 않는가?

---

## 테스트

- [ ] Service 단위 테스트가 존재하는가?
- [ ] 정상 경로와 예외 경로가 모두 테스트되는가?
- [ ] 포트/어댑터 도메인의 경우 포트를 Mockito로 모킹하는가?