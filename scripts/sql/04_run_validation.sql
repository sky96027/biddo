-- ============================================================
-- 실효성 검증 실행 스크립트 (Before / After)
-- 실행: psql -U biddo -d biddo -f scripts/sql/04_run_validation.sql
-- ============================================================

-- =========================================================
-- STEP 1: auction 더미 데이터 투입 (100,000건)
-- =========================================================
\echo ''
\echo '========================================================='
\echo 'STEP 1: auction 더미 데이터 100,000건 투입'
\echo '========================================================='

INSERT INTO auction (
    seller_id, category_id, title, description, item_condition,
    starting_price, current_price, buy_now_price,
    bid_count, view_count, status, winner_id,
    start_time, end_time, created_at, updated_at
)
SELECT
    m.member_id,
    c.category_id,
    '더미상품_' || g.i,
    '검증용 더미 데이터',
    (ARRAY['NEW','LIKE_NEW','GOOD','FAIR','POOR'])[(g.i % 5) + 1],
    (1 + (random() * 99)::int) * 10000,
    (1 + (random() * 99)::int) * 10000,
    CASE WHEN g.i % 3 = 0 THEN (200 + (random() * 800)::int) * 10000 ELSE NULL END,
    (random() * 30)::int,
    (random() * 200)::int,
    CASE (g.i % 100)
        WHEN 0 THEN 'ACTIVE' WHEN 1 THEN 'ACTIVE' WHEN 2 THEN 'ACTIVE'
        WHEN 3 THEN 'ACTIVE' WHEN 4 THEN 'ACTIVE' WHEN 5 THEN 'ACTIVE'
        WHEN 6 THEN 'ACTIVE' WHEN 7 THEN 'ACTIVE' WHEN 8 THEN 'ACTIVE'
        WHEN 9 THEN 'ACTIVE' WHEN 10 THEN 'ACTIVE' WHEN 11 THEN 'ACTIVE'
        WHEN 12 THEN 'ACTIVE' WHEN 13 THEN 'ACTIVE' WHEN 14 THEN 'ACTIVE'
        WHEN 15 THEN 'ACTIVE' WHEN 16 THEN 'ACTIVE' WHEN 17 THEN 'ACTIVE'
        WHEN 18 THEN 'ACTIVE' WHEN 19 THEN 'ACTIVE' WHEN 20 THEN 'ACTIVE'
        WHEN 21 THEN 'ACTIVE' WHEN 22 THEN 'ACTIVE' WHEN 23 THEN 'ACTIVE'
        WHEN 24 THEN 'ACTIVE' WHEN 25 THEN 'ACTIVE' WHEN 26 THEN 'ACTIVE'
        WHEN 27 THEN 'ACTIVE' WHEN 28 THEN 'ACTIVE' WHEN 29 THEN 'ACTIVE'
        WHEN 30 THEN 'ACTIVE' WHEN 31 THEN 'ACTIVE' WHEN 32 THEN 'ACTIVE'
        WHEN 33 THEN 'ACTIVE' WHEN 34 THEN 'ACTIVE' WHEN 35 THEN 'ACTIVE'
        WHEN 36 THEN 'ACTIVE' WHEN 37 THEN 'ACTIVE' WHEN 38 THEN 'ACTIVE'
        WHEN 39 THEN 'ACTIVE'
        WHEN 40 THEN 'ENDED' WHEN 41 THEN 'ENDED' WHEN 42 THEN 'ENDED'
        WHEN 43 THEN 'ENDED' WHEN 44 THEN 'ENDED' WHEN 45 THEN 'ENDED'
        WHEN 46 THEN 'ENDED' WHEN 47 THEN 'ENDED' WHEN 48 THEN 'ENDED'
        WHEN 49 THEN 'ENDED' WHEN 50 THEN 'ENDED' WHEN 51 THEN 'ENDED'
        WHEN 52 THEN 'ENDED' WHEN 53 THEN 'ENDED' WHEN 54 THEN 'ENDED'
        WHEN 55 THEN 'ENDED' WHEN 56 THEN 'ENDED' WHEN 57 THEN 'ENDED'
        WHEN 58 THEN 'ENDED' WHEN 59 THEN 'ENDED' WHEN 60 THEN 'ENDED'
        WHEN 61 THEN 'ENDED' WHEN 62 THEN 'ENDED' WHEN 63 THEN 'ENDED'
        WHEN 64 THEN 'ENDED' WHEN 65 THEN 'ENDED' WHEN 66 THEN 'ENDED'
        WHEN 67 THEN 'ENDED' WHEN 68 THEN 'ENDED'
        WHEN 69 THEN 'SOLD'  WHEN 70 THEN 'SOLD'  WHEN 71 THEN 'SOLD'
        WHEN 72 THEN 'SOLD'  WHEN 73 THEN 'SOLD'  WHEN 74 THEN 'SOLD'
        WHEN 75 THEN 'SOLD'  WHEN 76 THEN 'SOLD'  WHEN 77 THEN 'SOLD'
        WHEN 78 THEN 'SOLD'
        WHEN 79 THEN 'PENDING' WHEN 80 THEN 'PENDING' WHEN 81 THEN 'PENDING'
        WHEN 82 THEN 'PENDING' WHEN 83 THEN 'PENDING' WHEN 84 THEN 'PENDING'
        WHEN 85 THEN 'PENDING' WHEN 86 THEN 'PENDING' WHEN 87 THEN 'PENDING'
        WHEN 88 THEN 'PENDING' WHEN 89 THEN 'PENDING' WHEN 90 THEN 'PENDING'
        WHEN 91 THEN 'PENDING' WHEN 92 THEN 'PENDING' WHEN 93 THEN 'PENDING'
        ELSE 'CANCELLED'
    END,
    NULL,
    CASE (g.i % 100)
        WHEN 79 THEN NOW() + (random() * INTERVAL '3 days')  -- PENDING: 미래 시작
        WHEN 80 THEN NOW() + (random() * INTERVAL '3 days')
        WHEN 81 THEN NOW() + (random() * INTERVAL '3 days')
        WHEN 82 THEN NOW() + (random() * INTERVAL '3 days')
        WHEN 83 THEN NOW() + (random() * INTERVAL '3 days')
        WHEN 84 THEN NOW() + (random() * INTERVAL '3 days')
        WHEN 85 THEN NOW() + (random() * INTERVAL '3 days')
        WHEN 86 THEN NOW() + (random() * INTERVAL '3 days')
        WHEN 87 THEN NOW() + (random() * INTERVAL '3 days')
        WHEN 88 THEN NOW() + (random() * INTERVAL '3 days')
        WHEN 89 THEN NOW() + (random() * INTERVAL '3 days')
        WHEN 90 THEN NOW() + (random() * INTERVAL '3 days')
        WHEN 91 THEN NOW() + (random() * INTERVAL '3 days')
        WHEN 92 THEN NOW() + (random() * INTERVAL '3 days')
        WHEN 93 THEN NOW() + (random() * INTERVAL '3 days')
        ELSE NOW() - (random() * INTERVAL '30 days')         -- 나머지: 과거 시작
    END,
    CASE (g.i % 100)
        WHEN 0 THEN NOW() + (random() * INTERVAL '5 days')   -- ACTIVE: 미래 종료
        WHEN 1 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 2 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 3 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 4 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 5 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 6 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 7 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 8 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 9 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 10 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 11 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 12 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 13 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 14 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 15 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 16 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 17 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 18 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 19 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 20 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 21 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 22 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 23 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 24 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 25 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 26 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 27 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 28 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 29 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 30 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 31 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 32 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 33 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 34 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 35 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 36 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 37 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 38 THEN NOW() + (random() * INTERVAL '5 days')
        WHEN 39 THEN NOW() + (random() * INTERVAL '5 days')
        ELSE NOW() - (random() * INTERVAL '20 days')         -- 종료/취소: 과거 종료
    END,
    NOW() - (random() * INTERVAL '60 days'),
    NOW() - (random() * INTERVAL '5 days')
FROM generate_series(1, 100000) AS g(i)
JOIN LATERAL (
    SELECT member_id FROM member ORDER BY member_id OFFSET (g.i % (SELECT count(*) FROM member)::int) LIMIT 1
) m ON true
JOIN LATERAL (
    SELECT category_id FROM category ORDER BY category_id OFFSET (g.i % (SELECT count(*) FROM category)::int) LIMIT 1
) c ON true;

ANALYZE auction;

SELECT 'auction 총 건수:' AS label, count(*) FROM auction
UNION ALL SELECT 'status 분포:', NULL;
SELECT status, count(*) FROM auction GROUP BY status ORDER BY count(*) DESC;

-- =========================================================
-- STEP 2: 검증 대상 인덱스 상태 확인
-- =========================================================
\echo ''
\echo '========================================================='
\echo 'STEP 2: 현재 인덱스 목록'
\echo '========================================================='
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename IN ('auction','bid','notification')
  AND indexname NOT LIKE '%pkey'
ORDER BY tablename, indexname;

-- =========================================================
-- STEP 3: [Q2] PENDING 경매 활성화 - 핵심 누락 인덱스 검증
--   기존: idx_auction_status_end_time (status, end_time)
--   누락: start_time 조건에 대한 인덱스 없음
-- =========================================================
\echo ''
\echo '========================================================='
\echo 'STEP 3: [Q2] PENDING 경매 활성화 - start_time 인덱스 누락 검증'
\echo '========================================================='

\echo ''
\echo '[BEFORE] 인덱스 없는 상태 (enable_indexscan=off 로 강제 Seq Scan)'
SET enable_indexscan = off;
SET enable_bitmapscan = off;
EXPLAIN (ANALYZE, FORMAT TEXT)
SELECT auction_id FROM auction WHERE status = 'PENDING' AND start_time <= NOW();
RESET enable_indexscan;
RESET enable_bitmapscan;

\echo ''
\echo '>>> idx_auction_status_start_time 생성 중...'
CREATE INDEX IF NOT EXISTS idx_auction_status_start_time
    ON auction (status, start_time);
ANALYZE auction;

\echo '[AFTER] 인덱스 추가 후'
EXPLAIN (ANALYZE, FORMAT TEXT)
SELECT auction_id FROM auction WHERE status = 'PENDING' AND start_time <= NOW();

-- =========================================================
-- STEP 4: [Q1] ACTIVE 경매 종료 - 기존 인덱스 유효성 확인
--   idx_auction_status_end_time (status, end_time) 이미 있음
-- =========================================================
\echo ''
\echo '========================================================='
\echo 'STEP 4: [Q1] ACTIVE 경매 종료 - idx_auction_status_end_time 유효성'
\echo '========================================================='

\echo '[INDEX SCAN 상태]'
EXPLAIN (ANALYZE, FORMAT TEXT)
SELECT auction_id FROM auction WHERE status = 'ACTIVE' AND end_time <= NOW();

\echo '[SEQ SCAN 강제]'
SET enable_indexscan = off;
SET enable_bitmapscan = off;
EXPLAIN (ANALYZE, FORMAT TEXT)
SELECT auction_id FROM auction WHERE status = 'ACTIVE' AND end_time <= NOW();
RESET enable_indexscan;
RESET enable_bitmapscan;

-- =========================================================
-- STEP 5: [Q13] 알림 목록 - 인덱스 컬럼 불일치 검증
--   기존: idx_notification_receiver_created (receiver_id, created_at DESC)
--   쿼리: ORDER BY notification_id DESC
-- =========================================================
\echo ''
\echo '========================================================='
\echo 'STEP 5: [Q13] 알림 목록 - notification 인덱스 컬럼 불일치 검증'
\echo '========================================================='

ANALYZE notification;

\echo '[BEFORE] 기존 인덱스 (receiver_id, created_at DESC) - ORDER BY notification_id DESC'
EXPLAIN (ANALYZE, FORMAT TEXT)
SELECT notification_id, is_read FROM notification
WHERE receiver_id = (SELECT member_id FROM member ORDER BY member_id LIMIT 1 OFFSET 100)
ORDER BY notification_id DESC
LIMIT 20;

\echo ''
\echo '>>> 기존 인덱스 삭제 후 (receiver_id, notification_id DESC) 로 교체...'
DROP INDEX IF EXISTS idx_notification_receiver_created;
CREATE INDEX idx_notification_receiver_id
    ON notification (receiver_id, notification_id DESC);
ANALYZE notification;

\echo '[AFTER] 교체된 인덱스 (receiver_id, notification_id DESC)'
EXPLAIN (ANALYZE, FORMAT TEXT)
SELECT notification_id, is_read FROM notification
WHERE receiver_id = (SELECT member_id FROM member ORDER BY member_id LIMIT 1 OFFSET 100)
ORDER BY notification_id DESC
LIMIT 20;

-- 미읽 알림도 확인
\echo ''
\echo '[Q14] 미읽 알림 (is_read=false) - AFTER'
EXPLAIN (ANALYZE, FORMAT TEXT)
SELECT notification_id, is_read FROM notification
WHERE receiver_id = (SELECT member_id FROM member ORDER BY member_id LIMIT 1 OFFSET 100)
  AND is_read = false
ORDER BY notification_id DESC
LIMIT 20;

-- =========================================================
-- STEP 6: [Q8] 입찰 히스토리 - idx_bid_auction_created 유효성
--   기존: (auction_id, created_at) → ORDER BY bid_id 에 Sort 발생 여부
--   신규: (auction_id, bid_id DESC) → Sort 제거 여부
-- =========================================================
\echo ''
\echo '========================================================='
\echo 'STEP 6: [Q8] 입찰 히스토리 - bid 인덱스 교체 효과'
\echo '========================================================='

ANALYZE bid;

\echo '[BEFORE] 기존 idx_bid_auction_created (auction_id, created_at) - ORDER BY bid_id DESC'
EXPLAIN (ANALYZE, FORMAT TEXT)
SELECT bid_id, bid_amount FROM bid
WHERE auction_id = (SELECT auction_id FROM auction WHERE status = 'ENDED' LIMIT 1 OFFSET 200)
ORDER BY bid_id DESC
LIMIT 20;

\echo ''
\echo '>>> idx_bid_auction_created 삭제 후 idx_bid_auction_id 생성...'
DROP INDEX IF EXISTS idx_bid_auction_created;
CREATE INDEX idx_bid_auction_id
    ON bid (auction_id, bid_id DESC);
ANALYZE bid;

\echo '[AFTER] 신규 idx_bid_auction_id (auction_id, bid_id DESC)'
EXPLAIN (ANALYZE, FORMAT TEXT)
SELECT bid_id, bid_amount FROM bid
WHERE auction_id = (SELECT auction_id FROM auction WHERE status = 'ENDED' LIMIT 1 OFFSET 200)
ORDER BY bid_id DESC
LIMIT 20;

-- =========================================================
-- STEP 7: 최종 인덱스 현황
-- =========================================================
\echo ''
\echo '========================================================='
\echo 'STEP 7: 최종 인덱스 현황'
\echo '========================================================='
SELECT
    t.relname                                       AS "테이블",
    i.relname                                       AS "인덱스",
    pg_size_pretty(pg_relation_size(ix.indexrelid)) AS "크기",
    s.idx_scan                                      AS "스캔 횟수"
FROM pg_index ix
JOIN pg_class t ON t.oid = ix.indrelid
JOIN pg_class i ON i.oid = ix.indexrelid
LEFT JOIN pg_stat_user_indexes s ON s.indexrelid = ix.indexrelid
WHERE t.relname IN ('auction','bid','notification')
  AND i.relname NOT LIKE '%pkey'
ORDER BY t.relname, i.relname;