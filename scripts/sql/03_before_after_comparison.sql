-- ============================================================
-- 인덱스 Before/After 비교 스크립트
-- 목적: 동일 쿼리를 Index Scan / Seq Scan 강제 모드로 실행해
--       cost·actual rows·실행시간 차이를 확인
--
-- 방법: enable_indexscan=off 로 Seq Scan 강제
--       → 인덱스 DROP/RECREATE 없이 비교 가능
--
-- 전제: 01_dummy_data.sql 실행 후 데이터 확인
--   SELECT count(*) FROM auction;  -- 10만 이상
-- ============================================================

-- 통계 최신화 (플래너가 정확한 비용 추정을 하도록)
ANALYZE auction;
ANALYZE bid;
ANALYZE notification;

-- ============================================================
-- [Q1] ACTIVE 경매 종료 스케줄러
-- 인덱스: idx_auction_status_end_time (status, end_time)
-- ============================================================
\echo '====== Q1: findActiveAuctionsToEnd ======'
\echo '--- [INDEX SCAN] ---'
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT auction_id FROM auction
WHERE status = 'ACTIVE' AND end_time <= NOW();

\echo '--- [SEQ SCAN (인덱스 비활성화)] ---'
SET enable_indexscan = off;
SET enable_bitmapscan = off;
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT auction_id FROM auction
WHERE status = 'ACTIVE' AND end_time <= NOW();
RESET enable_indexscan;
RESET enable_bitmapscan;

-- ============================================================
-- [Q2] PENDING 경매 활성화 스케줄러
-- 인덱스: idx_auction_status_start_time (status, start_time) ← 신규 추가
-- ============================================================
\echo '====== Q2: findPendingAuctionsToActivate ======'
\echo '--- [INDEX SCAN] ---'
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT auction_id FROM auction
WHERE status = 'PENDING' AND start_time <= NOW();

\echo '--- [SEQ SCAN (인덱스 비활성화)] ---'
SET enable_indexscan = off;
SET enable_bitmapscan = off;
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT auction_id FROM auction
WHERE status = 'PENDING' AND start_time <= NOW();
RESET enable_indexscan;
RESET enable_bitmapscan;

-- ============================================================
-- [Q3] 판매자별 경매 목록 (상태 필터)
-- 인덱스: idx_auction_seller (seller_id)
-- ============================================================
\echo '====== Q3: findBySellerIdAndStatus ======'
\echo '--- [INDEX SCAN] ---'
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT auction_id FROM auction
WHERE seller_id = (SELECT member_id FROM member ORDER BY member_id LIMIT 1 OFFSET 5)
  AND status = 'ACTIVE'
ORDER BY auction_id DESC LIMIT 10;

\echo '--- [SEQ SCAN] ---'
SET enable_indexscan = off;
SET enable_bitmapscan = off;
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT auction_id FROM auction
WHERE seller_id = (SELECT member_id FROM member ORDER BY member_id LIMIT 1 OFFSET 5)
  AND status = 'ACTIVE'
ORDER BY auction_id DESC LIMIT 10;
RESET enable_indexscan;
RESET enable_bitmapscan;

-- ============================================================
-- [Q8] 입찰 히스토리 조회 (최신순)
-- 인덱스: idx_bid_auction_id (auction_id, bid_id DESC) ← 신규 교체
-- ============================================================
\echo '====== Q8: findBidHistoryFirstPage ======'
\echo '--- [INDEX SCAN] ---'
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT bid_id FROM bid
WHERE auction_id = (SELECT auction_id FROM auction WHERE status = 'ENDED' LIMIT 1 OFFSET 50)
ORDER BY bid_id DESC LIMIT 20;

\echo '--- [SEQ SCAN] ---'
SET enable_indexscan = off;
SET enable_bitmapscan = off;
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT bid_id FROM bid
WHERE auction_id = (SELECT auction_id FROM auction WHERE status = 'ENDED' LIMIT 1 OFFSET 50)
ORDER BY bid_id DESC LIMIT 20;
RESET enable_indexscan;
RESET enable_bitmapscan;

-- ============================================================
-- [Q9] 경매별 입찰가 내림차순
-- 인덱스: idx_bid_auction_amount (auction_id, bid_amount DESC)
-- ============================================================
\echo '====== Q9: findByAuctionIdOrderByBidAmountDesc ======'
\echo '--- [INDEX SCAN] ---'
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT bid_id, bid_amount FROM bid
WHERE auction_id = (SELECT auction_id FROM auction WHERE status = 'ENDED' LIMIT 1 OFFSET 50)
ORDER BY bid_amount DESC;

\echo '--- [SEQ SCAN] ---'
SET enable_indexscan = off;
SET enable_bitmapscan = off;
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT bid_id, bid_amount FROM bid
WHERE auction_id = (SELECT auction_id FROM auction WHERE status = 'ENDED' LIMIT 1 OFFSET 50)
ORDER BY bid_amount DESC;
RESET enable_indexscan;
RESET enable_bitmapscan;

-- ============================================================
-- [Q13] 수신자별 알림 목록
-- 인덱스: idx_notification_receiver_id (receiver_id, notification_id DESC) ← 교체
-- ============================================================
\echo '====== Q13: findByReceiverIdFirstPage ======'
\echo '--- [INDEX SCAN] ---'
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT notification_id FROM notification
WHERE receiver_id = (SELECT member_id FROM member ORDER BY member_id LIMIT 1 OFFSET 30)
ORDER BY notification_id DESC LIMIT 20;

\echo '--- [SEQ SCAN] ---'
SET enable_indexscan = off;
SET enable_bitmapscan = off;
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT notification_id FROM notification
WHERE receiver_id = (SELECT member_id FROM member ORDER BY member_id LIMIT 1 OFFSET 30)
ORDER BY notification_id DESC LIMIT 20;
RESET enable_indexscan;
RESET enable_bitmapscan;

-- ============================================================
-- [Q14] 미읽 알림 목록
-- ============================================================
\echo '====== Q14: findUnreadByReceiverIdFirstPage ======'
\echo '--- [INDEX SCAN] ---'
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT notification_id, is_read FROM notification
WHERE receiver_id = (SELECT member_id FROM member ORDER BY member_id LIMIT 1 OFFSET 30)
  AND is_read = false
ORDER BY notification_id DESC LIMIT 20;

\echo '--- [SEQ SCAN] ---'
SET enable_indexscan = off;
SET enable_bitmapscan = off;
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT notification_id, is_read FROM notification
WHERE receiver_id = (SELECT member_id FROM member ORDER BY member_id LIMIT 1 OFFSET 30)
  AND is_read = false
ORDER BY notification_id DESC LIMIT 20;
RESET enable_indexscan;
RESET enable_bitmapscan;

-- ============================================================
-- 최종: 인덱스 사용 통계
-- ============================================================
\echo '====== 인덱스 사용 통계 ======'
SELECT
    relname      AS "테이블",
    indexrelname AS "인덱스",
    idx_scan     AS "스캔 횟수",
    idx_tup_read AS "읽은 행",
    pg_size_pretty(pg_relation_size(indexrelid)) AS "인덱스 크기"
FROM pg_stat_user_indexes
WHERE relname IN ('auction', 'bid', 'notification')
ORDER BY relname, idx_scan DESC;