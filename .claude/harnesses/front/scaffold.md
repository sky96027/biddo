# 프론트엔드 생성 절차

## 초기 세팅 (최초 1회)

### 1단계: 프로젝트 생성

```bash
cd {프로젝트 루트}
npm create vite@latest front -- --template react-ts
cd front
```

### 2단계: 의존성 설치

```bash
npm install react-router-dom axios zustand
npm install sockjs-client @stomp/stompjs
npm install -D tailwindcss postcss autoprefixer @types/sockjs-client
npx tailwindcss init -p
```

### 3단계: 환경변수 설정

```
# front/.env.local
VITE_API_BASE_URL=http://localhost:9090
VITE_WS_URL=http://localhost:9090
```

### 4단계: 기본 구조 생성

`guide.md`의 프로젝트 구조를 따라 디렉토리와 파일을 생성할 것.
생성 순서: `types/` → `store/` → `api/` → `hooks/` → `components/` → `pages/` → `App.tsx`

---

## 신규 페이지 추가

### 1단계: 타입 정의 (`src/types/index.ts`)

해당 화면에서 사용할 응답 타입을 추가할 것.

```typescript
export interface AuctionDetail {
  auctionId: number;
  title: string;
  // ...
}
```

### 2단계: API 함수 (`src/api/{domain}.ts`)

```typescript
export const getAuction = async (auctionId: number): Promise<AuctionDetail> => {
  const res = await client.get<ApiResponse<AuctionDetail>>(`/api/v1/auctions/${auctionId}`);
  return res.data.data;
};
```

### 3단계: 페이지 컴포넌트 (`src/pages/{Name}Page.tsx`)

```typescript
const AuctionDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const [auction, setAuction] = useState<AuctionDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getAuction(Number(id))
      .then(setAuction)
      .catch((e) => setError(e.response?.data?.error?.message ?? '오류가 발생했습니다.'));
  }, [id]);

  // ...
};
```

### 4단계: 라우터 등록 (`src/App.tsx`)

```typescript
<Route path="/auctions/:id" element={<AuctionDetailPage />} />
```

인증이 필요한 페이지는 `PrivateRoute`로 감쌀 것:

```typescript
<Route path="/auctions/create" element={<PrivateRoute><AuctionCreatePage /></PrivateRoute>} />
```

---

## 신규 컴포넌트 추가

재사용 컴포넌트는 `src/components/{category}/` 하위에 생성.

```typescript
// 컴포넌트 기본 구조
interface Props {
  // props 타입 명시
}

const ComponentName = ({ prop }: Props) => {
  return (
    <div className="...">
      {/* Tailwind 클래스만 사용 */}
    </div>
  );
};

export default ComponentName;
```

---

## 신규 커스텀 훅 추가

```typescript
// src/hooks/use{Name}.ts
const use{Name} = (param: Type) => {
  const [state, setState] = useState<Type>(initial);

  useEffect(() => {
    // 사이드이펙트 (WebSocket, SSE, API 호출 등)
    return () => {
      // 정리 (연결 해제 등) — 반드시 구현
    };
  }, [param]);

  return state;
};
```

---

## 완료 후 체크리스트

- [ ] `App.tsx` 라우터에 경로 등록됨
- [ ] 인증 필요 페이지에 `PrivateRoute` 적용됨
- [ ] 타입을 `any` 없이 정의함
- [ ] API 함수가 `src/api/` 에 위치함 (컴포넌트 내부 axios 직접 호출 없음)
- [ ] useEffect 정리 함수(return)에서 WebSocket/SSE 연결 해제함
- [ ] 브라우저에서 직접 확인 요청