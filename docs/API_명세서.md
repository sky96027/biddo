# API 명세 개요

- **Base URL**: `/api/v1`
- **인증**: JWT Bearer Token (공개 API 제외)
- **페이지네이션**: Cursor 기반 (No-Offset)
- **파일 업로드**: S3 Presigned URL 방식
- **에러 코드**: 초기에는 HTTP 상태코드만 사용

---

# 인증 (Auth)

## JWT 정책

| 항목 | 값 | 설명 |
| --- | --- | --- |
| Access Token 만료 | 30분 | 짧은 수명으로 보안 강화 |
| Refresh Token 만료 | 14일 | Redis에 저장, 재발급 가능 |
| Token 저장 | Authorization Header | `Bearer {accessToken}` |
| Refresh 저장 | Redis | `member:{id}:session` |
| Token 재발급 | Refresh Token으로 요청 | Access Token 만료 시 자동 갱신 |

## JWT Bearer Token을 사용하는 이유

이 시스템은 EC2 2대 + ALB 구성으로 운영되며, 입찰/채팅 등 실시간 기능이 핵심입니다. 이 환경에서 JWT를 선택한 구체적인 이유는 다음과 같습니다.

**1. 다중 서버 환경 (EC2 2대 + ALB)**

JWT는 토큰 자체에 사용자 정보가 담겨 있어서, 요청이 EC2 1번으로 가든 2번으로 가든 서버가 독립적으로 검증할 수 있습니다. 세션 동기화를 위한 별도 인프라가 필요하지 않습니다.

**2. WebSocket 인증 용이**

입찰/채팅에서 WebSocket 연결 시 JWT를 연결 파라미터로 전달하면 바로 인증이 됩니다. 세션 방식은 WebSocket Handshake 시 쿠키 전달과 세션 조회가 필요해 처리가 복잡해집니다.

**3. Stateless → 입찰 동시 요청 처리에 유리**

Access Token 검증은 서명(암호화) 확인만으로 완료되어 DB/Redis 조회가 필요 없습니다. 입찰처럼 짧은 시간에 요청이 몰리는 상황에서 인증 계층의 병목을 줄여줍니다. Refresh Token만 Redis에 저장하여 보안과 성능을 모두 확보합니다.

## 다른 인증 방식과의 비교

| 인증 방식 | 동작 방식 | 다중 서버 (EC2 2대) | WebSocket 호환 | 성능 (동시 입찰) | 이 시스템에 부적합한 이유 |
| --- | --- | --- | --- | --- | --- |
| Session (Cookie) | 서버 메모리에 세션 저장, 쿠키로 식별 | ❌ 서버간 세션 공유 필요 (Sticky Session 또는 Redis 세션 저장소 필요) | ❌ 쿠키 전달 및 세션 조회 복잡 | ❌ 매 요청마다 세션 저장소 조회 필요 | EC2 2대에서 ALB가 요청을 분산하면, 다른 EC2로 간 요청은 세션이 없어 인증 실패. Redis 세션 저장소를 추가하면 해결되지만, 모든 요청에 Redis 조회가 발생하여 입찰 동시 요청 시 병목이 됨. |
| JWT (Bearer Token) | 토큰 자체에 정보 포함, 서명으로 검증 | ✅ 어느 서버든 독립 검증 가능 | ✅ 연결 시 토큰만 전달하면 증명 완료 | ✅ 서명 검증만으로 완료, 외부 조회 없음 | — (현재 선택된 방식) |
| OAuth 2.0 (Google/Kakao) | 외부 제공자에게 인증 위임 | ✅ Stateless | ✅ 토큰 기반 | ⚠️ 외부 API 통신 지연 발생 가능 | 소셜 로그인 추가는 향후 고려 가능하지만, 기본 인증으로는 직접 JWT 발급이 더 간결하고 외부 의존성이 없음. 초기에는 불필요. |
| API Key | 고정 키를 헤더에 포함 | ✅ Stateless | ✅ 가능 | ✅ 빠름 | 사용자별 권한 관리가 불가능. 경매 시스템은 "누가 입찰했는지"를 정확히 식별해야 하므로 사용자 인증이 필수. API Key는 서버 간 통신용으로만 적합. |

---

# 페이지네이션 정책

Cursor 기반 페이지네이션을 사용합니다. Offset 방식 대비 대량 데이터에서 성능이 우수합니다.

### 요청 파라미터

| 파라미터 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| cursor | String | null | 다음 페이지 커서 (첫 페이지는 null) |
| size | int | 20 | 페이지당 항목 수 (max: 100) |

### 응답 형식

```json
{
  "success": true,
  "data": {
    "content": [ ... ],
    "nextCursor": "eyJpZCI6MTAwfQ==",
    "hasNext": true
  }
}
```

> COUNT 쿼리를 피하고 Cursor 페이지네이션의 성능 이점을 최대화합니다. 총 개수가 필요한 UI가 있다면 별도 집계 API를 구현합니다.
>

---

# 파일 업로드 정책

Presigned URL 방식으로 클라이언트가 S3에 직접 업로드합니다. 서버 부하를 줄이고 대용량 파일에 적합합니다.

### 플로우

1. 클라이언트가 서버에 Presigned URL 요청
2. 서버가 S3 Presigned URL 발급 후 반환
3. 클라이언트가 Presigned URL로 S3에 직접 업로드
4. 업로드 완료 후 서버에 파일 URL 전달

---

# 회원 API (Member)

## `POST /api/v1/auth/signup` — 회원가입

| 구분 | 내용 |
| --- | --- |
| 인증 | 불필요 |
| Request Body | `{ email, password, nickname }` |
| Response | `{ memberId, email, nickname }` |
| 에러 | 400 이메일 중복 / 400 닉네임 중복 / 400 유효성 실패 |

## `POST /api/v1/auth/login` — 로그인

| 구분 | 내용 |
| --- | --- |
| 인증 | 불필요 |
| Request Body | `{ email, password }` |
| Response | `{ accessToken, refreshToken, expiresIn }` |
| 에러 | 401 이메일/비밀번호 불일치 |

## `POST /api/v1/auth/logout` — 로그아웃

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Request Body | `{ refreshToken }` |
| Response | 204 No Content |
| 처리 | Redis `member:{id}:session` 키 삭제 → Refresh Token 무효화 |
| 에러 | 401 유효하지 않은 토큰 |

## `POST /api/v1/auth/refresh` — 토큰 재발급

| 구분 | 내용 |
| --- | --- |
| 인증 | Refresh Token |
| Request Body | `{ refreshToken }` |
| Response | `{ accessToken, refreshToken, expiresIn }` |
| 에러 | 401 만료된 Refresh Token |

## `PUT /api/v1/auth/password` — 비밀번호 변경

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Request Body | `{ currentPassword, newPassword }` |
| Response | 200 OK |
| 에러 | 400 현재 비밀번호 불일치 / 400 유효성 실패 (새 비밀번호 조건 미충족) |

## `GET /api/v1/members/me` — 내 프로필 조회

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Response | `{ memberId, email, nickname, profileImageUrl, introduction, trustScore }` |

## `PUT /api/v1/members/me` — 프로필 수정

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Request Body | `{ nickname, introduction, profileImageUrl }` |
| Response | 수정된 프로필 정보 |

## `GET /api/v1/members/me/selling` — 내 판매 목록

| 구분 | 내용 |  |  |
| --- | --- | --- | --- |
| 인증 | 필요 |  |  |
| Query | `?status={ACTIVE | ENDED | SOLD}&cursor=&size=20` |
| Response | 페이지네이션 응답 (경매 목록) |  |  |

## `GET /api/v1/members/me/purchases` — 내 구매 목록

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Query | `?cursor=&size=20` |
| Response | 페이지네이션 응답 (낙찰 상품 목록) |

## `GET /api/v1/members/me/bids` — 내 입찰 중 목록

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Query | `?cursor=&size=20` |
| Response | 페이지네이션 응답 (입찰 중 경매 목록 + 내 입찰 순위) |

---

# 경매 API (Auction)

## `POST /api/v1/auctions` — 경매 등록

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Request Body | `{ title, description, categoryId, condition, startingPrice, buyNowPrice, startTime, endTime, imageUrls[] }` |
| Response | `{ auctionId, title, status, startTime, endTime }` |
| 비고 | startTime 미입력 시 서버 현재 시간으로 자동 설정 (즉시 시작). 미래 시점 설정 시 start_time 이전까지 수정/취소 가능 |
| 에러 | 400 유효성 실패 / 404 카테고리 없음 |

## `PUT /api/v1/auctions/{auctionId}` — 경매 수정

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 (판매자 본인만) |
| Request Body | `{ title, description, categoryId, condition, startingPrice, buyNowPrice, endTime, imageUrls[] }` |
| Response | `{ auctionId, title, status, startTime, endTime }` |
| 에러 | 403 판매자 아님 / 409 이미 시작된 경매 (start_time 이후) / 404 경매 없음 |

## `DELETE /api/v1/auctions/{auctionId}` — 경매 취소

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 (판매자 본인만) |
| Response | 204 No Content |
| 처리 | `status: CANCELLED` 변경, `AUCTION_CANCELLED` Kafka 이벤트 발행 (start_time 이전이므로 입찰/자동입찰 없음) |
| 에러 | 403 판매자 아님 / 409 이미 시작된 경매 취소 불가 / 404 경매 없음 |

> Wishlist(관심 등록) API — 미구현 보류. 향후 `POST/DELETE /api/v1/auctions/{auctionId}/wishlist` 형태로 추가 예정.
>

## `GET /api/v1/members/{memberId}` — 판매자 프로필 조회

| 구분 | 내용 |
| --- | --- |
| 인증 | 불필요 |
| Response | `{ memberId, nickname, profileImageUrl, introduction, trustScore, averageRating, reviewCount, completedTradeCount }` |

## `GET /api/v1/auctions/popular` — 인기 경매 랭킹

| 구분 | 내용 |
| --- | --- |
| 인증 | 불필요 |
| Query | `?size=10` (최대 10) |
| Response | `[ { auctionId, title, status, currentPrice, bidCount, thumbnailUrl, endTime } ]` |
| 비고 | Redis Sorted Set 기반 입찰 수 인기순 정렬. ACTIVE 경매만 반환. |

## `GET /api/v1/auctions/{auctionId}/similar` — 유사 상품 추천

| 구분 | 내용 |
| --- | --- |
| 인증 | 불필요 |
| Query | `?size=6` (최대 6) |
| Response | `[ { auctionId, title, status, currentPrice, bidCount, thumbnailUrl, endTime, sellerNickname, categoryName } ]` |
| 비고 | ES More Like This(title/description) 기반 유사도 분석. ES 장애 시 같은 카테고리 인기순 DB fallback. ACTIVE 경매만 반환. |

## `GET /api/v1/auctions/{auctionId}` — 경매 상세 조회

| 구분 | 내용 |
| --- | --- |
| 인증 | 불필요 |
| Response | 경매 상세 정보 + 이미지 + 판매자 정보 + 입찰 건수 + 최고 입찰자 |

## `GET /api/v1/auctions/{auctionId}/bids` — 입찰 히스토리

| 구분 | 내용 |
| --- | --- |
| 인증 | 불필요 |
| Query | `?cursor=&size=20` |
| Response | 페이지네이션 응답 (입찰 내역: 입찰자 닉네임, 금액, 시간, 타입) |

## `GET /api/v1/categories` — 카테고리 목록

| 구분 | 내용 |
| --- | --- |
| 인증 | 불필요 |
| Response | 계층형 카테고리 트리 |

## `GET /api/v1/categories/recommendations` — 카테고리 추천

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Response | `[ { id, name, depth, sortOrder, children } ]` (최대 5개) |
| 비고 | 입찰 빈도 + 가격 알림 경매 카테고리 기반 개인화 추천. 중복 제거. 이력 없으면 빈 배열 반환. |

## `GET /api/v1/auctions/{auctionId}/countdown` — 카운트다운 SSE

| 구분 | 내용 |
| --- | --- |
| 인증 | 불필요 |
| Response | `text/event-stream` (SSE 스트림) |
| 비고 | 경매 종료까지 남은 시간을 실시간 스트리밍. 스나이핑 연장 시 자동 반영. |

---

# 입찰 API (Bid)

## `POST /api/v1/auctions/{auctionId}/bids` — 입찰

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Request Body | `{ bidAmount }` |
| Response | `{ bidId, auctionId, bidAmount, bidType, isWinning, createdAt }` |
| 에러 | 400 최소 금액 미달 / 403 본인 경매 입찰 / 409 이미 종료 / 409 동시 입찰 충돌 |

## `POST /api/v1/auctions/{auctionId}/bids/buy-now` — 즉시 구매

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Response | `{ bidId, auctionId, bidAmount, bidType: "BUY_NOW" }` |
| 에러 | 400 즉시구매 미설정 / 409 이미 종료 |

## `POST /api/v1/auctions/{auctionId}/auto-bids` — 자동 입찰 설정

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Request Body | `{ maxAmount }` |
| Response | `{ autoBidId, auctionId, maxAmount, isActive }` |
| 에러 | 400 최대가 초과 / 409 이미 설정됨 |

## `DELETE /api/v1/auctions/{auctionId}/auto-bids` — 자동 입찰 취소

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Response | 204 No Content |

---

# 검색 API (Search)

## `GET /api/v1/search/auctions` — 경매 검색

| 구분 | 내용 |
| --- | --- |
| 인증 | 불필요 (로그인 시 최근 검색어 자동 저장) |
| Query | `?keyword=&categoryId=&minPrice=&maxPrice=&endWithin={1h,24h,3d}&sort={BID_COUNT,END_TIME,PRICE}&cursor=&size=20` |
| Response | 페이지네이션 응답 (Elasticsearch 기반, ES 장애 시 DB fallback — Resilience4j CircuitBreaker) |

## `GET /api/v1/search/recent` — 최근 검색어 조회

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Response | `["키워드1", "키워드2", ...]` (최대 10개, 최신순) |

## `DELETE /api/v1/search/recent/{keyword}` — 최근 검색어 개별 삭제

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Response | 204 No Content |

## `DELETE /api/v1/search/recent` — 최근 검색어 전체 삭제

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Response | 204 No Content |

---

# 키워드 알림 API (Keyword Alert)

## `POST /api/v1/keyword-alerts` — 키워드 알림 등록

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Request Body | `{ keyword, categoryId(선택), maxPrice(선택) }` |
| Response | `{ alertId, keyword, categoryId, maxPrice, isActive }` |

## `GET /api/v1/keyword-alerts` — 내 키워드 알림 목록

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Response | 키워드 알림 목록 |

## `PUT /api/v1/keyword-alerts/{alertId}` — 키워드 알림 수정

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Request Body | `{ categoryId, maxPrice }` |
| Response | `{ alertId, keyword, categoryId, maxPrice, isActive }` |

## `PATCH /api/v1/keyword-alerts/{alertId}/toggle` — 활성/비활성 토글

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Response | 200 OK |

## `DELETE /api/v1/keyword-alerts/{alertId}` — 키워드 알림 삭제

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Response | 204 No Content |

---

# 채팅 API (Chat)

## `GET /api/v1/chat/rooms` — 내 채팅방 목록

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Response | 채팅방 목록 (auction 정보 + 상대방 + 마지막 메시지 + 안읽 수) |

## `GET /api/v1/chat/rooms/{roomId}/messages` — 메시지 조회

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Query | `?cursor=&size=50` |
| Response | 페이지네이션 응답 (메시지 목록) |

## `POST /api/v1/upload/presigned-url` — 이미지 업로드 URL 발급 *(미구현)*

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Request Body | `{ fileName, contentType, purpose }` |
| Response | `{ presignedUrl, fileUrl, expiresIn }` |

---

# 알림 API (Notification)

## `GET /api/v1/notifications` — 알림 목록

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Query | `?isRead=false&cursor=&size=20` |
| Response | 페이지네이션 응답 (알림 목록) |

## `PATCH /api/v1/notifications/{notificationId}/read` — 알림 읽음 처리

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Response | 200 OK |

## `GET /api/v1/notifications/subscribe` — SSE 알림 구독 *(미구현)*

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Response | `text/event-stream` (SSE 스트림) |
| 비고 | Last-Event-ID 기반 재연결 복구 지원 예정 |

---

# 가격 알림 API (Price Alert)

## `POST /api/v1/price-alerts` — 가격 알림 등록

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Request Body | `{ auctionId, thresholdPercent }` |
| Response | `{ id, auctionId, thresholdPercent, basePrice, isActive }` |
| 에러 | 404 경매 없음 / 409 이미 등록됨 |

## `GET /api/v1/price-alerts` — 내 가격 알림 목록

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Response | `[ { id, auctionId, thresholdPercent, basePrice, isActive } ]` |

## `PUT /api/v1/price-alerts/{alertId}` — 가격 알림 수정

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 (등록자 본인만) |
| Request Body | `{ thresholdPercent }` |
| Response | `{ id, auctionId, thresholdPercent, basePrice, isActive }` |
| 에러 | 404 알림 없음 / 403 소유자 아님 |

## `PATCH /api/v1/price-alerts/{alertId}/toggle` — 활성/비활성 토글

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 (등록자 본인만) |
| Response | 200 OK |
| 에러 | 404 알림 없음 / 403 소유자 아님 |

## `DELETE /api/v1/price-alerts/{alertId}` — 가격 알림 삭제

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 (등록자 본인만) |
| Response | 204 No Content |
| 에러 | 404 알림 없음 / 403 소유자 아님 |

---

# 후기 API (Review)

## `POST /api/v1/auctions/{auctionId}/reviews` — 후기 작성

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 (낙찰자만) |
| Request Body | `{ rating, content }` |
| Response | `{ reviewId, rating, content, createdAt }` |
| 에러 | 403 낙찰자 아님 / 409 이미 작성됨 |

## `PUT /api/v1/reviews/{reviewId}` — 후기 수정

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 (작성자만) |
| Request Body | `{ rating, content }` |
| Response | 수정된 후기 정보 |

## `DELETE /api/v1/reviews/{reviewId}` — 후기 삭제

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 (작성자만) |
| Response | 204 No Content |

## `GET /api/v1/members/{memberId}/reviews` — 판매자 후기 목록

| 구분 | 내용 |
| --- | --- |
| 인증 | 불필요 |
| Query | `?cursor=&size=20` |
| Response | 페이지네이션 응답 (후기 + 평균 별점 + 총 후기 수) |

---

# 신고 API (Report)

## `POST /api/v1/reports` — 신고 접수

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Request Body | `{ reportedId, auctionId(optional), reason, description }` |
| Response | `{ reportId, status: "PENDING", createdAt }` |
| 에러 | 400 본인 신고 불가 / 404 대상자 없음 |

## `GET /api/v1/reports/me` — 내 신고 내역

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 |
| Query | `?cursor=&size=20` |
| Response | 페이지네이션 응답 (신고 목록 + 상태) |

---

# 관리자 API (Admin)

> 모든 관리자 API는 `role: ADMIN` 권한이 필요합니다.
>

## `GET /api/v1/admin/reports` — 신고 목록 조회

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 (ADMIN) |
| Query | `?status={PENDING|REVIEWED|RESOLVED|DISMISSED}&cursor=&size=20` |
| Response | 페이지네이션 응답 (신고 목록 + 신고자/피신고자 정보) |

## `PATCH /api/v1/admin/reports/{reportId}` — 신고 상태 변경

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 (ADMIN) |
| Request Body | `{ status, adminNote }` |
| Response | 수정된 신고 정보 |

## `DELETE /api/v1/admin/auctions/{auctionId}` — 경매 강제 삭제

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 (ADMIN) |
| Response | 204 No Content |
| 처리 | `status: CANCELLED` 변경, 모든 자동입찰 비활성화, 참여자 알림, Kafka 이벤트 발행 |

## `PATCH /api/v1/admin/members/{memberId}/ban` — 계정 제재

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 (ADMIN) |
| Request Body | `{ banType, reason, durationDays }` |
| Response | 제재 정보 |
| 비고 | banType: WARNING(경고), SUSPEND(일시정지), BAN(영구정지) |

## `DELETE /api/v1/admin/members/{memberId}/ban` — 제재 해제

| 구분 | 내용 |
| --- | --- |
| 인증 | 필요 (ADMIN) |
| Response | 해제된 회원 정보 (banType, banReason, banEndDate가 null) |

---

# WebSocket 엔드포인트

## `WS /ws/auction/{auctionId}` — 경매 실시간 구독

| 방향 | 이벤트 | 데이터 |
| --- | --- | --- |
| Server → Client | NEW_BID | `{ bidder, amount, bidType, timestamp }` |
| Server → Client | PRICE_UPDATE | `{ currentPrice, bidCount }` |
| Server → Client | AUCTION_ENDED | `{ winnerId, finalPrice }` |
| Server → Client | COUNTDOWN_EXTENDED | `{ newEndTime, reason }` |

## `WS /ws/chat/{roomId}` — 채팅 실시간

| 방향 | 이벤트 | 데이터 |
| --- | --- | --- |
| Client → Server | SEND_MESSAGE | `{ content, messageType, imageUrl }` |
| Server → Client | NEW_MESSAGE | `{ messageId, sender, content, messageType, timestamp }` |
| Server → Client | READ_RECEIPT | `{ messageId, readBy }` |