# 레이어드 도메인 수정 가이드

## 패키지 구조

```
biddo-domain/src/main/java/com/biddo/domain/{도메인}/
├── entity/         # JPA Entity
├── repository/     # Spring Data JPA Repository
├── service/        # 비즈니스 로직
├── exception/      # ErrorCode enum, 커스텀 Exception
└── dto/            # 도메인 내부 DTO (선택)

biddo-api/src/main/java/com/biddo/api/{도메인}/
├── controller/     # REST Controller
├── dto/
│   ├── request/    # 요청 DTO
│   └── response/   # 응답 DTO
└── mapper/         # Entity ↔ DTO 변환 (선택)
```

## 수정 전 확인

- 엔티티 필드 변경 시 관련 Repository 쿼리 메서드가 깨지지 않는지 확인할 것.
- 다른 도메인에서 해당 엔티티를 FK로 참조하는지 확인할 것 (특히 Member, Auction).
- ErrorCode 추가 시 기존 코드와 중복되지 않는지 확인할 것.

## 엔티티 규칙

- `BaseTimeEntity` 상속할 것 (createdAt, updatedAt 자동 관리).
- 엔티티가 자기 불변식을 보호할 것 (값 검증, 상태 전이).
- `@Column(nullable = false)` 등 제약은 엔티티에서 명시할 것.

## 서비스 규칙

- `@Service`, `@Transactional`, `@RequiredArgsConstructor`.
- 읽기 전용 메서드에 `@Transactional(readOnly = true)`.
- Lazy 로딩 엔티티 접근 시 fetch join 필요 여부를 확인할 것.

## 테스트

- 단위 테스트 **필수**. Mockito로 Repository 모킹.
- 테스트 시나리오:
  - [ ] 정상 CRUD
  - [ ] 존재하지 않는 엔티티 조회 시 예외
  - [ ] 유효하지 않은 상태 전이 시 예외 (상태가 있는 경우)
  - [ ] 권한 검증 (본인 리소스만 수정/삭제)

## 금지 사항

- 레이어드 도메인에 `port/out/` 패키지 도입 **금지**.
- 비즈니스 규칙을 임의로 판단 **금지**. 사용자에게 확인할 것.
- 다른 도메인 Repository를 Service에서 직접 주입하지 말 것. 해당 도메인 Service를 경유할 것.