# 프론트엔드 개발 가이드

## 기술 스택

| 분류 | 기술 |
|------|------|
| 프레임워크 | React 18 + TypeScript |
| 빌드 | Vite |
| 라우팅 | React Router v6 |
| HTTP | Axios |
| 상태관리 | Zustand (전역 인증 상태), useState/useReducer (로컬) |
| 스타일 | Tailwind CSS |
| WebSocket | 브라우저 내장 `WebSocket` API |
| SSE | 브라우저 내장 `EventSource` API |

## 프로젝트 구조

```
front/
├── src/
│   ├── api/              # Axios 인스턴스 + 도메인별 API 함수
│   │   ├── client.ts     # Axios 인스턴스 (baseURL, 인터셉터)
│   │   ├── auth.ts
│   │   ├── auction.ts
│   │   ├── bid.ts
│   │   └── notification.ts
│   ├── hooks/            # 커스텀 훅
│   │   ├── useWebSocket.ts    # WebSocket 연결/구독
│   │   └── useSSE.ts          # SSE 연결/구독
│   ├── store/            # Zustand 스토어
│   │   └── authStore.ts  # 로그인 상태, accessToken, 사용자 정보
│   ├── pages/            # 라우트 단위 페이지 컴포넌트
│   │   ├── LoginPage.tsx
│   │   ├── RegisterPage.tsx
│   │   ├── AuctionListPage.tsx
│   │   ├── AuctionCreatePage.tsx
│   │   └── AuctionDetailPage.tsx
│   ├── components/       # 재사용 컴포넌트
│   │   ├── layout/
│   │   │   └── Header.tsx    # 네비게이션 + 알림 드롭다운
│   │   └── common/
│   │       ├── ApiErrorMessage.tsx
│   │       └── LoadingSpinner.tsx
│   ├── types/            # 공유 타입 정의
│   │   └── index.ts
│   ├── App.tsx           # 라우터 설정
│   └── main.tsx
├── index.html
├── vite.config.ts
├── tailwind.config.js
└── package.json
```

## 네이밍 규칙

| 대상 | 규칙 | 예시 |
|------|------|------|
| 컴포넌트 | PascalCase | `AuctionCard`, `BidForm` |
| 훅 | camelCase, `use` 접두사 | `useWebSocket`, `useAuctionDetail` |
| API 함수 | camelCase, 동사 시작 | `getAuctions`, `placeBid` |
| 타입/인터페이스 | PascalCase | `AuctionResponse`, `BidRequest` |
| 상수 | UPPER_SNAKE_CASE | `WS_BASE_URL` |
| 파일 | 컴포넌트는 PascalCase, 나머지는 camelCase | `AuctionCard.tsx`, `useWebSocket.ts` |

## API 클라이언트 패턴

```typescript
// src/api/client.ts
const client = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL });

client.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// 응답 인터셉터: 401 → 로그인 페이지 리다이렉트
client.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
```

```typescript
// API 함수 패턴 — data만 꺼내서 반환
export const getAuctions = async (params?: AuctionSearchParams) => {
  const res = await client.get<ApiResponse<CursorResponse<AuctionSummary>>>('/api/v1/auctions', { params });
  return res.data.data;
};
```

## WebSocket 훅 패턴

```typescript
// src/hooks/useWebSocket.ts
export const useWebSocket = (url: string, onMessage: (data: unknown) => void) => {
  useEffect(() => {
    const ws = new WebSocket(url);
    ws.onmessage = (e) => onMessage(JSON.parse(e.data));
    return () => ws.close();
  }, [url]);
};
```

## SSE 훅 패턴

```typescript
// src/hooks/useSSE.ts
export const useSSE = (url: string, onMessage: (data: unknown) => void) => {
  useEffect(() => {
    const es = new EventSource(url);
    es.onmessage = (e) => onMessage(JSON.parse(e.data));
    return () => es.close();
  }, [url]);
};
```

## 에러 처리

- API 에러: `error.response.data.error.message`에서 메시지 추출해 화면에 표시.
- `try/catch`로 감싸고 에러 상태(`useState`)로 관리할 것.
- 전역 에러(401 등)는 Axios 인터셉터에서 처리.

## 수정 전 확인

- WebSocket/SSE URL 변경 시 `screens.md`의 연결 정보를 확인할 것.
- 페이지 추가 시 `App.tsx` 라우터에 경로를 등록할 것.
- 인증이 필요한 페이지에 `PrivateRoute` 가드를 적용할 것.
- 타입 정의 변경 시 `types/index.ts`의 공유 타입을 먼저 확인할 것.

## 금지 사항

- `any` 타입 사용 **금지**. 타입이 불확실하면 `unknown` 후 타입 가드 적용.
- 컴포넌트 내부에서 직접 `axios` 호출 **금지**. 반드시 `src/api/` 함수 경유.
- 비즈니스 규칙(최소 입찰 단위, 자동입찰 연쇄 제한 등)을 프론트에서 임의 계산 **금지**. 서버 응답값 사용.
- CSS 파일 직접 작성 **금지**. Tailwind 클래스 사용.