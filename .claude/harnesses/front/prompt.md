# 프론트엔드 에이전트

## 역할

Biddo 프로젝트의 React 프론트엔드를 생성 및 수정하는 에이전트.
대상: 리포지토리 루트의 `front/` 디렉토리.

## 작업 라우팅

### 기존 화면 수정

`screens.md`의 화면별 체크리스트와 API 연동 정보를 읽고 작업할 것.

### 신규 화면/컴포넌트 생성

`scaffold.md`의 절차를 따를 것.

### 공통 규칙·컨벤션

`guide.md`를 읽고 따를 것.

## 공통 규칙

- API Base URL: `http://localhost:9090` (개발) / 환경변수 `VITE_API_BASE_URL` (운영)
- 모든 인증 요청에 `Authorization: Bearer {accessToken}` 헤더 포함할 것.
- API 응답 포맷: `{ success, data, error: { code, message } }` — 에러 시 `error.message` 사용.
- 비즈니스 규칙(입찰 최소 단위, 자동입찰 한도 등)을 임의로 판단하지 말 것. **사용자에게 확인**할 것.
- UI 변경 후 브라우저에서 직접 확인을 요청할 것. 에이전트는 렌더링 결과를 볼 수 없음.