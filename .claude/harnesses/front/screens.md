# 화면별 수정 가이드

> **프론트 라우트** = React Router 경로 (브라우저 URL)
> **백엔드 API** = 서버 엔드포인트 (Base: `/api/v1`)

화면 수정 시 해당 섹션의 파일 체크리스트, API, 실시간 연결 정보를 반드시 확인할 것.

---

## 1. 로그인 / 회원가입

| 구분 | 경로 |
|------|------|
| 프론트 라우트 | `/login`, `/register` |

### 파일 체크리스트

| 파일 | 역할 |
|------|------|
| `pages/LoginPage.tsx` | 로그인 폼 |
| `pages/RegisterPage.tsx` | 회원가입 폼 |
| `api/auth.ts` | `login`, `signup`, `logout`, `refresh` 함수 |
| `store/authStore.ts` | `accessToken`, `refreshToken`, 사용자 정보 저장 |

### 백엔드 API

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/v1/auth/signup` | 회원가입 — `{ email, password, nickname }` |
| POST | `/api/v1/auth/login` | 로그인 → `{ accessToken, refreshToken, expiresIn }` |
| POST | `/api/v1/auth/logout` | 로그아웃 — body: `{ refreshToken }` |
| POST | `/api/v1/auth/refresh` | 토큰 재발급 — body: `{ refreshToken }` |
| GET | `/api/v1/members/me` | 내 프로필 (로그인 직후 사용자 정보 조회) |

### 수정 전 확인

- 로그인 성공 시 `authStore`에 `accessToken` 저장 후 `GET /api/v1/members/me` 호출해 사용자 정보 추가 저장.
- 토큰 재발급은 Axios 응답 인터셉터에서 401 수신 시 자동 처리.
- 로그인/회원가입 성공 시 경매 목록(`/`)으로 리다이렉트.

---

## 2. 경매 목록 (`/`)

| 구분 | 경로 |
|------|------|
| 프론트 라우트 | `/` |

### 파일 체크리스트

| 파일 | 역할 |
|------|------|
| `pages/AuctionListPage.tsx` | 목록 + 검색 필터 |
| `api/auction.ts` | `searchAuctions`, `getPopularAuctions` 함수 |
| `components/auction/AuctionCard.tsx` | 경매 카드 컴포넌트 |

### 백엔드 API

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/v1/search/auctions` | 경매 검색 (ES, 장애 시 DB fallback) |
| GET | `/api/v1/auctions/popular` | 인기 경매 (Redis Sorted Set, 최대 10개) |
| GET | `/api/v1/categories` | 카테고리 목록 (필터용) |

### 검색 쿼리 파라미터 (`/api/v1/search/auctions`)

| 파라미터 | 설명 |
|----------|------|
| `keyword` | 검색어 |
| `categoryId` | 카테고리 필터 |
| `minPrice`, `maxPrice` | 가격 범위 |
| `endWithin` | 마감 임박 (`1h`, `24h`, `3d`) |
| `sort` | 정렬 (`BID_COUNT`, `END_TIME`, `PRICE`) |
| `cursor`, `size` | 커서 페이지네이션 |

### 수정 전 확인

- 경매 목록은 `/api/v1/auctions`가 아닌 **`/api/v1/search/auctions`** 사용.
- 페이지네이션은 커서 기반 — `totalElements` 없음.

---

## 3. 경매 등록 (`/auctions/create`)

| 구분 | 경로 |
|------|------|
| 프론트 라우트 | `/auctions/create` |

### 파일 체크리스트

| 파일 | 역할 |
|------|------|
| `pages/AuctionCreatePage.tsx` | 등록 폼 |
| `api/auction.ts` | `createAuction` 함수 |
| `api/upload.ts` | `getPresignedUrl`, S3 직접 업로드 함수 |

### 백엔드 API

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/v1/upload/presigned-url` | S3 Presigned URL 발급 — `{ fileName, contentType, purpose }` |
| POST | `/api/v1/auctions` | 경매 등록 |
| GET | `/api/v1/categories` | 카테고리 선택용 목록 |

### 경매 등록 Request Body

```typescript
{
  title: string;
  description: string;
  categoryId: number;
  condition: 'NEW' | 'LIKE_NEW' | 'GOOD' | 'FAIR' | 'POOR';
  startingPrice: number;
  buyNowPrice?: number;    // 선택
  startTime?: string;      // ISO 8601, 생략 시 즉시 시작
  endTime: string;         // ISO 8601
  imageUrls: string[];     // S3 업로드 완료 후 URL 목록
}
```

### 이미지 업로드 플로우 (Presigned URL 방식)

1. `POST /api/v1/upload/presigned-url` → `{ presignedUrl, fileUrl }` 수신
2. `PUT presignedUrl` — S3에 파일 직접 업로드 (서버 경유 없음, `Content-Type` 헤더 필수)
3. `fileUrl`을 `imageUrls[]`에 담아 경매 등록 API 호출

### 수정 전 확인

- 이미지를 서버에 직접 POST하지 않음. Presigned URL 방식 필수.
- 로그인한 사용자만 접근 가능 (`PrivateRoute` 적용).
- 등록 성공 시 응답의 `auctionId`로 `/auctions/{id}`로 이동.

---

## 4. 경매 상세 (`/auctions/:id`)

가장 복잡한 화면. STOMP WebSocket + SSE + 입찰 API가 모두 연동됨.

| 구분 | 경로 |
|------|------|
| 프론트 라우트 | `/auctions/:id` |

### 파일 체크리스트

| 파일 | 역할 |
|------|------|
| `pages/AuctionDetailPage.tsx` | 전체 화면 조합 |
| `api/auction.ts` | `getAuction`, `getAuctionBids` 함수 |
| `api/bid.ts` | `placeBid`, `buyNow`, `setAutoBid`, `cancelAutoBid` 함수 |
| `hooks/useStomp.ts` | STOMP 연결 + 토픽 구독 |
| `hooks/useSSE.ts` | SSE 연결/구독 |
| `components/auction/BidForm.tsx` | 수동 입찰 + 자동입찰 + 즉시구매 |
| `components/auction/BidHistory.tsx` | 입찰 내역 |
| `components/auction/CountdownTimer.tsx` | 남은 시간 표시 |

### 백엔드 API

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/v1/auctions/{auctionId}` | 경매 상세 (초기 데이터) |
| GET | `/api/v1/auctions/{auctionId}/bids` | 입찰 히스토리 |
| GET | `/api/v1/auctions/{auctionId}/similar` | 유사 상품 추천 (최대 6개) |
| POST | `/api/v1/auctions/{auctionId}/bids` | 수동 입찰 — `{ bidAmount }` |
| POST | `/api/v1/auctions/{auctionId}/buy-now` | 즉시 구매 |
| POST | `/api/v1/auctions/{auctionId}/auto-bids` | 자동 입찰 설정 — `{ maxAmount }` |
| DELETE | `/api/v1/auctions/{auctionId}/auto-bids` | 자동 입찰 취소 |

### 실시간 연결

| 프로토콜 | 연결 방식 | 수신 대상 |
|----------|-----------|----------|
| STOMP/SockJS | `/ws` 엔드포인트에 연결 후 `/topic/auction/{auctionId}` 구독 | 입찰 업데이트 |
| SSE | `GET /api/v1/auctions/{auctionId}/countdown` | 카운트다운 |

### STOMP 연결 방법 (`sockjs-client` + `@stomp/stompjs` 사용)

```typescript
// hooks/useStomp.ts
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const client = new Client({
  webSocketFactory: () => new SockJS('/ws'),
  onConnect: () => {
    client.subscribe(`/topic/auction/${auctionId}`, (frame) => {
      const data = JSON.parse(frame.body);
      // data.type: 'NEW_BID' | 'PRICE_UPDATE' | 'AUCTION_ENDED' | 'COUNTDOWN_EXTENDED'
    });
  },
});
client.activate();
// cleanup: client.deactivate()
```

### STOMP 수신 메시지

| type | 데이터 | 처리 |
|------|--------|------|
| `NEW_BID` | `{ bidder, amount, bidType, timestamp }` | 입찰 내역 목록 추가 |
| `PRICE_UPDATE` | `{ currentPrice, bidCount }` | 현재가·입찰수 상태 업데이트 |
| `AUCTION_ENDED` | `{ winnerId, finalPrice }` | 종료 UI로 전환 |
| `COUNTDOWN_EXTENDED` | `{ newEndTime, reason }` | 카운트다운 기준 시간 재설정 |

### 수정 전 확인

- WebSocket은 raw WebSocket이 아닌 **STOMP over SockJS**. `sockjs-client`, `@stomp/stompjs` 패키지 필요.
- 컴포넌트 언마운트 시 `client.deactivate()`, SSE `es.close()` 호출 필수 (메모리 누수 방지).
- 경매 상태(ENDED/SOLD/CANCELLED)에 따라 입찰 폼 비활성화.
- 본인 경매(`sellerId === 내 memberId`)이면 입찰 버튼 숨김.
- `buyNowPrice`가 null이면 즉시구매 버튼 숨김.

---

## 5. 알림 (Header 드롭다운)

별도 페이지 없이 `Header.tsx` 내 드롭다운으로 구현.

### 파일 체크리스트

| 파일 | 역할 |
|------|------|
| `components/layout/Header.tsx` | 알림 아이콘 + 드롭다운 |
| `api/notification.ts` | `getNotifications`, `markAsRead` 함수 |
| `hooks/useSSE.ts` | 실시간 알림 수신 |

### 백엔드 API

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/v1/notifications/subscribe` | SSE 알림 구독 (`text/event-stream`) |
| GET | `/api/v1/notifications` | 알림 목록 — `?isRead=false&cursor=&size=20` |
| PATCH | `/api/v1/notifications/{notificationId}/read` | 알림 읽음 처리 |

### SSE 연결

- `authStore.accessToken` 존재 시(로그인 후) 연결 시작.
- 로그아웃 시 `es.close()` 호출.
- `Last-Event-ID` 기반 재연결 복구 지원됨.
- SSE는 인증된 레이아웃 컴포넌트에서 관리할 것.

### 알림 타입

`BID`, `OUTBID`, `AUCTION_END`, `WON`, `PRICE_ALERT`, `KEYWORD_MATCH`