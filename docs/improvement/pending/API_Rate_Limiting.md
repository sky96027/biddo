# API Rate Limiting

> **상태**: 미결정
> **작성일**: 2025-06-22

---

## 현황

모든 API 엔드포인트에 요청 빈도 제한이 없다. 인증된 사용자든 비인증 요청이든 무제한으로 호출 가능하다.

## 위험 시나리오

| 시나리오 | 영향 |
| --- | --- |
| 입찰 API 반복 호출 | 분산 락 경합 증가, Redis 부하 |
| 검색 API 반복 호출 | ES + DB 부하 (CircuitBreaker가 열리면 DB fallback으로 전이) |
| 로그인/회원가입 반복 호출 | Brute force 공격, JWT 발급 부하 |
| SSE/WebSocket 재연결 폭주 | 서버 커넥션 고갈 |

## 검토 방향

### 적용 방식

Redis 기반 sliding window rate limiting. EC2 2대 환경에서 인스턴스 간 공유 카운터가 필요하므로 로컬 카운터로는 부족하다.

구현 선택지:
- **Bucket4j + Redis**: Spring 통합 지원, `@RateLimiter` 어노테이션 방식
- **직접 구현**: Redis `INCR` + `EXPIRE`로 sliding window 구현 (의존성 최소화)

### 엔드포인트별 정책 예시

| 엔드포인트 | 제한 | 기준 |
| --- | --- | --- |
| `POST /api/v1/bids` | 분당 30회 | 사용자 ID |
| `GET /api/v1/auctions/search` | 분당 60회 | 사용자 ID 또는 IP |
| `POST /api/v1/auth/login` | 분당 10회 | IP |
| `POST /api/v1/members` | 시간당 5회 | IP |

### 응답

제한 초과 시 `429 Too Many Requests` + `Retry-After` 헤더 반환.

## 우선순위

포트폴리오 시연 환경에서는 악의적 트래픽 가능성이 낮으므로 구현 우선순위는 높지 않다. 다만 입찰 API처럼 비즈니스 로직상 빈도 제한이 의미 있는 엔드포인트부터 선택적으로 적용할 수 있다.