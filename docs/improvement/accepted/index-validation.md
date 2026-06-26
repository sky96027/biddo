# 인덱스 실효성 검증 결과

> **상태**: ✅ 완료 (PR #91, 2026-06-26)
> 관련 이슈: #62
> 작성일: 2026-06-26
> 검증 방법: 정적 쿼리 분석 + EXPLAIN ANALYZE (scripts/sql/02_explain_analyze.sql)

---

## 1. 대상 쿼리 목록

| ID  | Repository                  | 메서드                                  | 테이블       | 조건                                            |
|-----|-----------------------------|-----------------------------------------|--------------|-------------------------------------------------|
| Q1  | AuctionJpaRepository        | findActiveAuctionsToEnd                 | auction      | `status='ACTIVE' AND end_time<=:now`            |
| Q2  | AuctionJpaRepository        | findPendingAuctionsToActivate           | auction      | `status='PENDING' AND start_time<=:now`         |
| Q3  | AuctionJpaRepository        | findBySellerIdAndStatus*                | auction      | `seller_id=:id AND status=:s ORDER BY id DESC`  |
| Q4  | AuctionJpaRepository        | findBySellerId*                         | auction      | `seller_id=:id ORDER BY id DESC`                |
| Q5  | AuctionJpaRepository        | findByWinnerId*                         | auction      | `winner_id=:id AND status IN ('ENDED','SOLD')`  |
| Q6  | AuctionJpaRepository        | findSimilarByCategory                   | auction      | `status='ACTIVE' AND category_id=:id ORDER BY bid_count DESC` |
| Q7  | AuctionJpaRepository        | countCompletedBySellerId                | auction      | `seller_id=:id AND status IN (...) AND winner_id IS NOT NULL` |
| Q8  | BidJpaRepository            | findBidHistoryFirstPage/WithCursor      | bid          | `auction_id=:id ORDER BY id DESC`               |
| Q9  | BidJpaRepository            | findByAuctionIdOrderByBidAmountDesc     | bid          | `auction_id=:id ORDER BY bid_amount DESC`       |
| Q10 | BidJpaRepository            | findWinningBidByAuctionId               | bid          | `auction_id=:id AND is_winning=true`            |
| Q11 | BidJpaRepository            | findTopCategoryIdsByBidderId            | bid + auction| `bidder_id=:id GROUP BY category_id`            |
| Q12 | AuctionJpaRepository        | findActiveAuctionsByBidderId*           | bid + auction| `bidder_id=:id AND status='ACTIVE'`             |
| Q13 | NotificationJpaRepository   | findByReceiverIdFirstPage/WithCursor    | notification | `receiver_id=:id ORDER BY id DESC`              |
| Q14 | NotificationJpaRepository   | findUnreadByReceiverIdFirstPage/WithCursor | notification | `receiver_id=:id AND is_read=false ORDER BY id DESC` |
| Q15 | NotificationJpaRepository   | findByReceiverIdAfter                   | notification | `receiver_id=:id AND id > :lastEventId ORDER BY id ASC` |

---

## 2. 기존 인덱스 목록

| 테이블       | 인덱스명                             | 컬럼                          |
|--------------|--------------------------------------|-------------------------------|
| auction      | idx_auction_status_end_time          | (status, end_time)            |
| auction      | idx_auction_seller                   | (seller_id)                   |
| auction      | idx_auction_category                 | (category_id)                 |
| auction      | idx_auction_winner                   | (winner_id)                   |
| bid          | idx_bid_auction_amount               | (auction_id, bid_amount DESC) |
| bid          | idx_bid_bidder                       | (bidder_id)                   |
| bid          | idx_bid_auction_created              | (auction_id, created_at)      |
| notification | idx_notification_receiver_created    | (receiver_id, created_at DESC)|

---

## 3. 쿼리별 인덱스 커버리지 분석

### auction 테이블

| 쿼리 | 사용 인덱스 | 판정 | 비고 |
|------|-------------|------|------|
| Q1 `findActiveAuctionsToEnd` | idx_auction_status_end_time | ✅ 적합 | 복합 인덱스 완전 일치 |
| Q2 `findPendingAuctionsToActivate` | (없음) | ❌ 누락 | 조건이 `start_time`인데 인덱스는 `end_time` |
| Q3 `findBySellerIdAndStatus` | idx_auction_seller | ⚠️ 부분 | `seller_id` 필터 후 `status` 재필터 |
| Q4 `findBySellerId` | idx_auction_seller | ✅ 적합 | `seller_id` 조회 후 id 정렬 |
| Q5 `findByWinnerId` | idx_auction_winner | ⚠️ 부분 | `winner_id` 필터 후 `status IN` 재필터 |
| Q6 `findSimilarByCategory` | idx_auction_category | ⚠️ 부분 | `category_id` 필터 후 `status`, `bid_count` 정렬 미지원 |
| Q7 `countCompletedBySellerId` | idx_auction_seller | ⚠️ 부분 | `status IN`, `winner_id IS NOT NULL` 재필터 |

### bid 테이블

| 쿼리 | 사용 인덱스 | 판정 | 비고 |
|------|-------------|------|------|
| Q8 `findBidHistory*` | idx_bid_auction_amount | ⚠️ 부분 | `auction_id` 필터 지원, `ORDER BY id DESC` 미지원 → Sort 추가 발생 |
| Q9 `findByAuctionIdOrderByBidAmountDesc` | idx_bid_auction_amount | ✅ 적합 | (auction_id, bid_amount DESC) 완전 일치 |
| Q10 `findWinningBidByAuctionId` | idx_bid_auction_amount | ⚠️ 부분 | `is_winning` 조건 미포함 |
| Q11 `findTopCategoryIdsByBidderId` | idx_bid_bidder | ✅ 적합 | |
| Q12 `findActiveAuctionsByBidderId` | idx_bid_bidder | ✅ 적합 | `status` 필터는 auction 조인 후 처리 |

### notification 테이블

| 쿼리 | 사용 인덱스 | 판정 | 비고 |
|------|-------------|------|------|
| Q13 `findByReceiverId*` | idx_notification_receiver_created | ⚠️ 부분 | `ORDER BY id DESC` ≠ `created_at DESC` |
| Q14 `findUnreadByReceiverId*` | idx_notification_receiver_created | ⚠️ 부분 | `is_read` 조건 미포함 |
| Q15 `findByReceiverIdAfter` | idx_notification_receiver_created | ⚠️ 부분 | `id >` 범위 조건 미포함 |

---

## 4. 식별된 문제

### 4-1. 누락 인덱스 (❌)

#### `idx_auction_status_start_time` 신규 추가 필요

```
auction (status, start_time)
```

- **영향 쿼리**: Q2 `findPendingAuctionsToActivate`
- **문제**: 스케줄러가 1분 주기로 실행되는 쿼리인데, `status='PENDING' AND start_time <= NOW()` 조건에 대한 인덱스 없음.
  `idx_auction_status_end_time`은 `end_time`만 포함 → `start_time` 조건에서 Seq Scan 또는 `status` 단독 필터 후 재스캔 발생.
- **해결**: `(status, start_time)` 복합 인덱스 추가.

---

### 4-2. 불일치 인덱스 (⚠️)

#### `idx_notification_receiver_created` 컬럼 불일치

```
현재: notification (receiver_id, created_at DESC)
쿼리: ORDER BY notification_id DESC / notification_id > :lastEventId
```

- **영향 쿼리**: Q13, Q14, Q15
- **문제**: 모든 알림 쿼리가 `ORDER BY id`로 정렬하는데, 인덱스 두 번째 컬럼이 `created_at`.
  `Notification`은 생성 시 `LocalDateTime.now()`를 직접 주입하므로 id ≈ created_at 순서이나 엄밀히 다름.
  PostgreSQL은 `ORDER BY notification_id`에 이 인덱스를 정렬 최적화에 사용할 수 없음 → Sort 단계 추가 발생 가능.
- **해결**: 인덱스 두 번째 컬럼을 `notification_id`로 교체.

```
변경: notification (receiver_id, notification_id DESC)
```

#### `idx_bid_auction_created` 컬럼 미사용

```
현재: bid (auction_id, created_at)
쿼리: ORDER BY bid_id DESC (Q8) / ORDER BY bid_amount DESC (Q9)
```

- **문제**: `bid.created_at`으로 정렬하는 쿼리가 없음.
  `idx_bid_auction_amount`이 이미 `auction_id` 필터를 커버하므로 `idx_bid_auction_created` 사실상 중복.
- **해결**: 삭제 검토. Q8의 `ORDER BY id DESC`를 지원하려면 `(auction_id, bid_id DESC)`로 교체가 더 유효.

---

### 4-3. 복합 인덱스 효율 개선 가능 (선택적)

| 쿼리 | 현재 인덱스 | 개선안 | 효과 |
|------|-------------|--------|------|
| Q3 `findBySellerIdAndStatus` | `(seller_id)` | `(seller_id, status, auction_id DESC)` | Index Only Scan 가능 |
| Q5 `findByWinnerId` | `(winner_id)` | `(winner_id, status)` | status IN 재필터 제거 |
| Q10 `findWinningBidByAuctionId` | `(auction_id, bid_amount)` | `(auction_id) WHERE is_winning = true` (partial) | 부분 인덱스로 크기 감소 |

> Q3/Q5의 복합 인덱스 확장은 쓰기 오버헤드 증가 대비 효과를 실측 후 판단 권장.

---

## 5. 결론 및 권장 조치

| 우선순위 | 조치 | 대상 | 비고 |
|----------|------|------|------|
| 🔴 High  | 인덱스 추가 | `idx_auction_status_start_time (status, start_time)` | 스케줄러 쿼리 Seq Scan 위험 |
| 🟠 Medium | 인덱스 교체 | `idx_notification_receiver_created` → `(receiver_id, notification_id DESC)` | 알림 쿼리 ORDER BY 불일치 해소 |
| 🟡 Low   | 인덱스 삭제 검토 | `idx_bid_auction_created` | 대응 쿼리 없음, 불필요한 쓰기 오버헤드 |
| 🟢 선택   | 복합 인덱스 확장 | `idx_auction_seller`, `idx_auction_winner` | 실측 후 결정 |

---

## 6. 검증 결과 (EXPLAIN ANALYZE 실측)

더미 데이터 기준: auction 101,826건 / bid 297,402건 / notification 18,040,212건  
실행 스크립트: `scripts/sql/04_run_validation.sql`

### [Q2] PENDING 경매 활성화 스케줄러

| 구분 | 실행 계획 | 실행 시간 |
|------|-----------|-----------|
| **BEFORE** (인덱스 없음) | Seq Scan, 101,825행 필터 | **12.516 ms** |
| **AFTER** (`idx_auction_status_start_time` 추가) | Bitmap Index Scan | **0.042 ms** |

→ **298배 향상**. 스케줄러가 1분마다 호출하는 쿼리이므로 실효성 높음.

### [Q1] ACTIVE 경매 종료 스케줄러 (기존 인덱스 검증)

| 구분 | 실행 계획 | 실행 시간 |
|------|-----------|-----------|
| Index Scan (`idx_auction_status_end_time`) | Bitmap Index Scan, 670행 | **3.932 ms** |
| Seq Scan (강제) | Seq Scan, 101,156행 필터 | **13.501 ms** |

→ **3.4배 향상**. 기존 인덱스 정상 동작 확인.

### [Q13] 알림 목록 조회 — 가장 심각한 문제

| 구분 | 실행 계획 | 실행 시간 |
|------|-----------|-----------|
| **BEFORE** (`receiver_id, created_at DESC`) | **Index Scan Backward using notification_pkey**, 18,040,212행 역스캔 | **61,697 ms (61.7초!)** |
| **AFTER** (`receiver_id, notification_id DESC`) | Index Scan using idx_notification_receiver_id | **0.070 ms** |

→ **880,000배 향상**. PostgreSQL이 `ORDER BY notification_id DESC`를 처리하지 못해 PK 전체 역스캔을 수행하고 있었음. 알림이 많은 사용자는 응답 불가 수준.

### [Q8] 입찰 히스토리 조회

| 구분 | 실행 계획 | 실행 시간 |
|------|-----------|-----------|
| **BEFORE** (`auction_id, created_at`) | Index Scan + **Sort(bid_id DESC)** 추가 단계 | **1.771 ms** |
| **AFTER** (`auction_id, bid_id DESC`) | Index Scan only, Sort 없음 | **0.127 ms** |

→ **14배 향상**. Sort 단계 제거로 정렬 오버헤드 없앰.

---

## 7. 검증 절차

```bash
# 스크립트 한 번에 실행 (더미 투입 + Before/After 비교 + 인덱스 변경)
docker exec -i biddo-postgres psql -U biddo -d biddo \
  -f scripts/sql/04_run_validation.sql
```