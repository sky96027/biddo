# Biddo - 중고 상품 실시간 경매 플랫폼

중고 상품을 실시간으로 경매할 수 있는 플랫폼입니다. 개발자 포트폴리오 시연 목적으로 제작되었으며, 상업적 거래를 중개하지 않습니다.

## 기술 스택

| 분류 | 기술 |
|------|------|
| Backend | Java 17+, Spring Boot 3.x, Spring Data JPA (Hibernate), Gradle |
| DB | PostgreSQL, Redis |
| Messaging | Apache Kafka |
| Real-time | WebSocket (입찰/채팅), SSE (알림/카운트다운) |
| Search | Elasticsearch |
| Storage | S3 + CloudFront + Lambda@Edge (이미지 리사이징) |
| Infra | EC2 x3 (App x2 + Infra x1), ALB, ECR, EventBridge Scheduler, GitHub Actions CI/CD, Docker |
| Monitoring | Prometheus + Grafana + Tempo (분산 추적) |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Logging | Logback (콘솔 + 로컬 파일, 일별 롤링) |

## 프로젝트 구조

```
biddo/
├── biddo-api/      # Controller, DTO, 인증/인가
├── biddo-domain/   # Entity, Service, Port 인터페이스
├── biddo-infra/    # 외부 시스템 연동 + 포트 구현체
├── docs/           # 설계 문서
└── docker-compose.yml
```

**의존성**: `biddo-api → biddo-domain, biddo-infra` / `biddo-infra → biddo-domain`

**아키텍처**: Bid/Auction은 포트/어댑터 패턴 (외부 의존 다수 → 테스트/교체 용이), 나머지는 레이어드 패턴 (보일러플레이트 최소화).

## 주요 기능

### 경매
- 경매 등록/수정/취소 (PENDING 상태에서만 수정/취소 가능)
- Redis TTL 기반 경매 상태 전환 (PENDING → ACTIVE → ENDED/SOLD) + 보완 스케줄러
- 생명주기 메트릭: TTL vs 스케줄러 경로별 처리 건수 Micrometer Counter (`auction.lifecycle.processed`)
- 스나이핑 방지: 종료 10분 전 입찰 시 +10분 연장

### 입찰
- 수동 입찰, 자동 입찰, 즉시 구매
- 최소 증가 단위: 현재가 비율 기반 (10%/5%/3%/1%), 100원 단위 올림
- Redis 분산 락 (Redisson): EC2 2대 환경에서 동시 입찰 원자성 보장
- 자동 입찰 연쇄 방지 (최대 10회 제한)

### 검색
- Elasticsearch 기반 전문 검색 (키워드, 카테고리, 가격, 마감 임박 필터)
- Nori 형태소 분석기: 한국어 복합어 분리 및 조사 제거로 띄어쓰기·어미 변형 무관 검색
- function_score 커스텀 스코어링: 입찰 수(log1p) + 마감 임박(gauss decay 3d) 가중치로 인기·마감 임박 상품 우선 노출
- Resilience4j CircuitBreaker: ES 장애 시 DB fallback
- 최근 검색어 관리 (Redis)

### 실시간 통신
- WebSocket: 입찰 실시간 알림, 채팅
- SSE: 알림 스트리밍, 경매 카운트다운

### 알림
- 입찰 알림 (BID/OUTBID/AUCTION_END/WON)
- 가격 상승 알림: 설정 비율 초과 시 Kafka 이벤트로 알림 생성
- 키워드 알림: 등록 키워드 매칭 경매 알림

### 추천
- 유사 상품 추천 (ES More Like This)
- 인기 경매 랭킹 (Redis Sorted Set)
- 카테고리 추천 (입찰 빈도 + 가격 알림 기반 개인화)

### 평판
- 거래 후기 (별점 1~5 + 텍스트)
- 신뢰도 점수: 후기 평균(50%) + 거래 완료율(25%) + 가입 기간(15%) - 신고 패널티(10%), 일 1회 배치 재계산

### 관리자
- 신고 관리 (PENDING → REVIEWED → RESOLVED/DISMISSED)
- 계정 제재 (WARNING/SUSPEND/BAN)
- 경매 강제 삭제

## 로컬 개발 환경 설정

### 사전 요구사항
- Java 17+
- Docker & Docker Compose

### 실행

```bash
# 1. 환경 변수 설정 (Docker Compose용)
cp .env.example .env

# 2. 인프라 실행 (PostgreSQL, Redis, Kafka, Elasticsearch)
docker-compose up -d

# 3. 애플리케이션 실행
./gradlew :biddo-api:bootRun
```

서버는 `http://localhost:9090` 에서 실행됩니다.

> `.env.example`의 기본값 그대로 사용하면 됩니다. EC2 배포 시에만 값을 수정합니다.

## 테스트

```bash
# 전체 테스트
./gradlew clean test

# 빌드 (테스트 포함)
./gradlew clean build
```

## API 개요

- **Base URL**: `/api/v1`
- **인증**: JWT Bearer Token (Access 30분, Refresh 14일/Redis)
- **페이지네이션**: Cursor 기반 (No-Offset)
- **응답 포맷**: `{ success, data, error: { code, message } }`

Swagger UI: `http://localhost:9090/swagger-ui/index.html`

상세 API 명세는 [docs/spec/API_명세서.md](docs/spec/API_명세서.md)를 참고하세요.

## 설계 문서

- [프로젝트 개요](docs/Biddo-중고_경매_시스템(Used_Auction_System).md)
- [AWS 배포 계획](docs/AWS_배포_계획.md)
- [ERD](docs/spec/ERD.md)
- [API 명세서](docs/spec/API_명세서.md)
- [비즈니스 규칙 정의서](docs/spec/비즈니스_규칙_정의서.md)
- [시스템 아키텍처 & 시퀀스 다이어그램](docs/spec/시스템_아키텍처&시퀀스_다이어그램.md)
- [프로젝트 구조 & 코딩 컨벤션](docs/spec/프로젝트_구조&코딩_컨벤션.md)
- [요구사항 목록](docs/spec/requirements.md)
- [이슈 트래킹](https://github.com/sky96027/biddo/issues)

## 성능 최적화

### DB 인덱스 검증 (PostgreSQL)
EXPLAIN ANALYZE로 주요 쿼리 실측 후 누락·불일치 인덱스를 수정.

| 수정 내용 | 개선 전 | 개선 후 |
|-----------|---------|---------|
| `idx_auction_status_start_time` 추가 — `findPendingAuctionsToActivate` | Seq Scan 12.5ms | Bitmap Index Scan 0.042ms (**298x**) |
| `idx_notification_receiver_id` ORDER BY 컬럼 일치 (`created_at` → `notification_id DESC`) | PK 역방향 스캔 61.7s (18M rows) | Index Scan 0.07ms (**880,000x**) |
| `idx_bid_auction_id` 교체 — Sort 제거 | 1.77ms (Sort 포함) | 0.127ms (**14x**) |

### Kafka 컨슈머 배치 최적화
검색 동기화·알림 컨슈머 개별 처리 → 배치 처리 전환, 단건 조회 → `findAllById` 일괄 조회.

### Redis 락 메트릭
Redisson 락 대기·보유 시간 Micrometer 측정 (`bid.lock.wait`, `bid.lock.hold`).

## Git 컨벤션

- **브랜치**: `main` → `develop` → `feature/{도메인}-{기능}`
- **커밋**: `<type>(<scope>): <subject>` (feat, fix, docs, style, refactor, test, chore)