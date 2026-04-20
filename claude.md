중고 상품 실시간 경매 플랫폼. 개발자 포트폴리오 시연 목적으로 제작. 상업적 거래를 중개하지 않음.

---

## 기술 스택

- **Backend**: Java 17+, Spring Boot 3.x, Spring Data JPA (Hibernate), Gradle
- **DB**: PostgreSQL (RDS), Redis (ElastiCache)
- **Messaging**: Apache Kafka (Amazon MSK)
- **Real-time**: WebSocket (입찰/채팅 양방향), SSE (알림/카운트다운 단방향)
- **Search**: Elasticsearch (Amazon OpenSearch)
- **Storage**: S3 + CloudFront + Lambda@Edge (이미지 리사이징)
- **Infra**: EC2 x2 + ALB, Route 53, GitHub Actions CI/CD, Docker
- **Monitoring**: Prometheus + Grafana + CloudWatch

---

## 프로젝트 구조 (멀티 모듈)

```jsx
biddo/
├── biddo-api/          # Controller, DTO, 인증/인가
│   └── com.biddo.api.{auction,bid,member,chat,notification,search,review,common}
├── biddo-domain/       # Entity, Service, Port 인터페이스
│   ├── auction/ [포트/어댑터] model, port/out, service, exception
│   ├── bid/     [포트/어댑터] model, port/out, service, exception
│   └── member,chat,notification,review,report [레이어드] entity, repository, service
├── biddo-infra/        # 외부 시스템 연동 + 포트 구현체
│   ├── auction/, bid/  # 포트 구현체 (RepositoryImpl, KafkaPublisher, RedisCache)
│   └── kafka, redis, elasticsearch, s3, websocket, sse
├── docs/               # 설계 문서 (Notion 마크다운 백업)
└── docker-compose.yml
```

**의존성**: `biddo-api → biddo-domain, biddo-infra` / `biddo-infra → biddo-domain`

**아키텍처 전략**: Bid/Auction은 포트/어댑터 (외부 의존 4~5개 → 테스트/교체 용이성). 나머지는 레이어드 (보일러플레이트 최소화).

---

## ERD 핵심 엔티티 (12개)

1. **Member** — email(UNIQUE), password(BCrypt), nickname(UNIQUE), trust_score(DECIMAL(2,1), 0.0~5.0), role(USER/ADMIN), ban_type(WARNING/SUSPEND/BAN), ban_reason, ban_end_date
2. **Category** — 계층형(parent_id self-ref, depth 0/1/2)
3. **Auction** — seller_id(FK→Member), status(PENDING/ACTIVE/ENDED/CANCELLED/SOLD), start_time, end_time, current_price, buy_now_price, winner_id
4. **Auction Image** — auction_id(FK), image_url(S3), sort_order
5. **Bid** — auction_id, bidder_id, bid_amount, bid_type(MANUAL/AUTO/BUY_NOW), is_winning
6. **Auto Bid** — auction_id, bidder_id, max_amount, is_active / UNIQUE(auction_id, bidder_id)
7. **Chat Room** — auction_id, seller_id, buyer_id, status(ACTIVE/CLOSED), seller_last_read_message_id, buyer_last_read_message_id
8. **Chat Message** — chat_room_id, sender_id, content, message_type(TEXT/IMAGE/SYSTEM), image_url (is_read 없음 → Chat Room의 last_read_message_id로 대체)
9. **Review** — auction_id, reviewer_id(구매자), reviewee_id(판매자), rating(1~5) / UNIQUE(auction_id, reviewer_id)
10. **Notification** — receiver_id, auction_id, type(BID/OUTBID/AUCTION_END/WON/PRICE_ALERT/KEYWORD_MATCH), is_read
11. **Keyword Alert** — member_id, keyword, category_id, max_price, is_active
12. **Report** — reporter_id, reported_id, auction_id, reason(NO_TRADE/FAKE_PRODUCT/PRICE_MANIPULATION/FRAUD/OTHER), status(PENDING/REVIEWED/RESOLVED/DISMISSED), admin_note

---

## Redis 데이터 구조

| Key | Type | TTL | 용도 |
| --- | --- | --- | --- |
| `auction:start:{auctionId}` | String | start_time까지 | TTL 만료 → PENDING→ACTIVE 전환 |
| `auction:{id}:current_price` | String | 경매 종료 시 삭제 | 현재 최고 입찰가 |
| `auction:{id}:bid_count` | String | 경매 종료 시 삭제 | 입찰 수 |
| `auction:{id}:bidders` | Set | 경매 종료 시 삭제 | 입찰 참여자 |
| `auction:{id}:top_bidder` | Hash | 경매 종료 시 삭제 | 최고 입찰자 정보 |
| `auction:lock:{auctionId}` | - | 5초 | 분산 락 (Redisson) |
| `auction:end:{auctionId}` | String | 경매 종료까지 | TTL 만료 → 경매 종료 이벤트 |
| `member:{id}:session` | String | 14d | JWT Refresh Token |
| `auction:popular` | Sorted Set | 1h | 인기 경매 랭킹 |
| `search:recent:{memberId}` | List | 30d | 최근 검색어 |

---

## Kafka 토픽

| 토픽 | 파티션 Key | Consumer |
| --- | --- | --- |
| `bid-events` | auctionId | Notification, WebSocket, Auto-Bid, ES Sync |
| `auction-events` | auctionId | Notification, ES Sync |
| `notification-events` | receiverId | Notification Service |
| `chat-events` | chatRoomId | WebSocket Handler |

**이벤트**: AUCTION_CREATED, AUCTION_ACTIVATED, AUCTION_UPDATED, AUCTION_ENDED, AUCTION_SOLD, AUCTION_CANCELLED, BID_PLACED, AUTO_BID_EXHAUSTED

---

## API 개요

- **Base URL**: `/api/v1`
- **인증**: JWT Bearer Token (Access 30분, Refresh 14일/Redis)
- **페이지네이션**: Cursor 기반 (No-Offset, totalElements 없음)
- **파일 업로드**: S3 Presigned URL
- **응답 포맷**: `ApiResponse<T>` — `{ success, data, error: { code, message } }`

### 주요 엔드포인트

**Auth**: signup, login, logout, refresh, password 변경

**Member**: 프로필 조회/수정, 판매/구매/입찰 목록

**Auction**: CRUD, 상세 조회, 입찰 히스토리, 카테고리 목록

**Bid**: 입찰, 즉시구매(buy-now), 자동입찰 설정/취소

**Search**: ES 기반 검색 (ES 장애 시 DB fallback — Resilience4j CircuitBreaker)

**Chat**: 채팅방 목록, 메시지 조회

**Notification**: 목록, 읽음 처리, SSE 구독 (Last-Event-ID 기반 재연결 복구)

**Review**: 작성/수정/삭제, 판매자 후기 목록

**Report**: 신고 접수, 내 신고 내역

**Admin**: 신고 관리, 경매 강제 삭제, 계정 제재(WARNING/SUSPEND/BAN), 제재 해제

### WebSocket

- `WS /ws/auction/{auctionId}` — NEW_BID, PRICE_UPDATE, AUCTION_ENDED, COUNTDOWN_EXTENDED
- `WS /ws/chat/{roomId}` — SEND_MESSAGE, NEW_MESSAGE, READ_RECEIPT

---

## 핵심 비즈니스 규칙

### 입찰

- 최소 증가 단위: 현재가 비율 기반 (0~9,999원: 10%, 1만~9.9만: 5%, 10만~99.9만: 3%, 100만+: 1%), 100원 단위 올림
- 본인 경매 입찰/자동입찰 금지
- 입찰 철회 불가

### 자동 입찰

- 경매당 1인 1설정 (UNIQUE 제약)
- 충돌 시: 금액순 → 선착순
- **연쇄 방지**: bid_type=AUTO 이벤트는 Auto-Bid Consumer가 무시, 수동 입찰 시 일괄 처리, 최대 10회 연쇄 제한

### 경매 종료

- Redis TTL: start_time 도달 시 PENDING→ACTIVE, end_time 도달 시 ACTIVE→ENDED + 1분 주기 보완 스케줄러
- 스나이핑 방지: 종료 10분 전 입찰 시 +10분 연장 (무제한)
- 유찰: bid_count=0이면 status=ENDED, winner_id=NULL, 채팅방 미생성

### 즉시 구매

- bid_type: BUY_NOW 기록 → status: SOLD → 자동입찰 전체 비활성화 → TTL 키 삭제 → 채팅방 생성

### 경매 상태 전이

- PENDING(등록) → ACTIVE(start_time 도달, Redis TTL) → ENDED/SOLD → 채팅방 생성
- PENDING → CANCELLED(취소)

### 경매 수정/취소

- status: PENDING일 때만 가능 (409 Conflict)
- PENDING 상태에서는 입찰 불가 → 별도 처리 불필요

### 동시성 처리

- Redis 분산 락 (Redisson): `auction:lock:{auctionId}`, 대기 3초, 유지 5초
- EC2 2대 + ALB 환경에서 원자성 보장

---

## 코딩 컨벤션

### 네이밍

- 클래스: PascalCase (`AuctionService`)
- 메서드: camelCase, 동사 시작 (`createAuction()`)
- 상수: UPPER_SNAKE_CASE (`MAX_BID_RETRY`)
- 패키지: `com.biddo.domain.bid`
- DTO: `*Request` / `*Response`
- 예외: `도메인명 + Exception` (`AuctionNotFoundException`)

### 예외 처리

- `ErrorCode` 인터페이스 + 도메인별 `enum implements ErrorCode`
- `BusinessException extends RuntimeException`
- `@RestControllerAdvice GlobalExceptionHandler`

### 도메인 모델 책임 원칙

- **엔티티/모델이 자기 불변식을 보호** (값 검증, 상태 전이, 도메인 판단, 단순 계산)
- **서비스는 예외 케이스만** (DB 조회 필요한 검증, 인코딩 전 원문 검증, 교차 엔티티 검증)
- **DTO는 형식 검증만** (`@NotNull`, `@NotBlank`, `@Email`). 비즈니스 규칙(`@Min`, `@Size`, `@Positive`) 금지

### 테스트

- 단위: JUnit 5 + Mockito (Service, 입찰/자동입찰 로직 필수)
- 통합: Testcontainers (PostgreSQL, Redis, Kafka)
- 부하: K6 (동시 입찰 필수)

### Git

- 브랜치: `main` → `develop` → `feature/{도메인}-{기능}`
- 커밋: `<type>(<scope>): <subject>` (feat, fix, docs, style, refactor, test, chore)

---

## 서비스 운영 정책

- **목적**: 개발자 포트폴리오 시연. 상업적 이익 없음.
- **결제**: 미구현 (법적 요건 회피). 채팅방은 기술 시연용.
- **면책 고지**: 회원가입, 경매 등록, 입찰, 채팅방, 푸터 5곳에 표시
- **이용약관**: 서비스 성격, 거래 책임 면제, 실거래 금지 권고, 서비스 중단 가능, 개인정보 안내 5개 조항
- **개인정보 처리방침**: 수집 항목(이메일/비밀번호/닉네임), 목적, 보유 기간, 암호화(BCrypt/HTTPS), 파기 절차

---

## 주요 아키텍처 결정

| 결정 | 선택 | 이유 |
| --- | --- | --- |
| 도메인 아키텍처 | Bid/Auction: 포트/어댑터, 나머지: 레이어드 | 복잡도 기반 선택적 적용, 보일러플레이트 최소화 |
| 인증 | JWT (Access+Refresh) | EC2 2대 Stateless, WebSocket 인증 용이 |
| 동시 입찰 | Redis 분산 락 | 다중 인스턴스 원자성, 인메모리 성능 |
| 경매 종료 | Redis TTL + 보완 스케줄러 | 실시간 정확성, 스나이핑 연장 연동 |
| 검색 동기화 | Kafka CDC | 실시간 반영, 장애 시 리플레이 |
| ES 장애 대응 | CircuitBreaker + DB fallback | 서비스 가용성 확보 |
| 실시간 통신 | WS(입찰/채팅) + SSE(알림) | 양방향/단방향 역할 분리 |
| Kafka 파티셔닝 | auctionId 기반 | 경매별 이벤트 순서 보장 |
| 이미지 처리 | Lambda@Edge on-demand | CloudFront 캐싱, 서버 부하 제거 |
| 알림 복구 | SSE Last-Event-ID | 재연결 시 미수신 알림 자동 재전송 |
| 읽음 확인 | last_read_message_id | 메시지별 is_read 대비 쿼리 효율 |

---

## Notion 설계 문서 링크
- [프로젝트 개요](docs/Biddo-중고_경매_시스템(Used_Auction_System).md)
- [ERD](docs/ERD.md)
- [API 명세서](docs/API_명세서.md)
- [비즈니스 규칙 정의서](docs/비즈니스_규칙_정의서.md)
- [시스템 아키텍처 & 시퀀스 다이어그램](docs/시스템_아키텍처&시퀀스_다이어그램.md)
- [프로젝트 구조 & 코딩 컨벤션](docs/프로젝트_구조&코딩_컨벤션.md)
- [요구사항 목록](docs/requirements.md)