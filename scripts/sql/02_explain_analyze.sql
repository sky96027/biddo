-- ============================================================
-- EXPLAIN ANALYZE 쿼리 플랜 검증 스크립트
-- 목적: 인덱스 실효성 확인 (Index Scan vs Seq Scan)
-- 전제: 01_dummy_data.sql 실행 후 10만 건 이상 데이터 확인
--
-- 실행 방법:
--   각 섹션을 psql 또는 DBeaver 등에서 개별 실행
--   Index Scan 여부, actual rows, cost 위주로 확인
-- ============================================================

-- -------------------------
-- [Q1] 경매 스케줄러 - ACTIVE 경매 종료 처리
-- 대응: AuctionJpaRepository.findActiveAuctionsToEnd
-- 예상: idx_auction_status_end_time (Index Scan)
-- -------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT auction_id, status, end_time
FROM auction
WHERE status = 'ACTIVE'
  AND end_time <= NOW();

-- -------------------------
-- [Q2] 경매 스케줄러 - PENDING 경매 활성화
-- 대응: AuctionJpaRepository.findPendingAuctionsToActivate
-- 예상: idx_auction_status_end_time은 start_time 미포함 → Index Scan 불가 주의
-- -------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT auction_id, status, start_time
FROM auction
WHERE status = 'PENDING'
  AND start_time <= NOW();

-- -------------------------
-- [Q3] 판매자별 경매 목록 (상태 필터)
-- 대응: AuctionJpaRepository.findBySellerIdAndStatus*
-- 예상: idx_auction_seller (seller_id) Index Scan 후 status 필터
-- -------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT auction_id, seller_id, status
FROM auction
WHERE seller_id = (SELECT member_id FROM member ORDER BY member_id LIMIT 1 OFFSET 5)
  AND status = 'ACTIVE'
ORDER BY auction_id DESC
LIMIT 10;

-- -------------------------
-- [Q4] 판매자별 전체 경매 목록 (커서 기반)
-- 대응: AuctionJpaRepository.findBySellerIdWithCursor
-- 예상: idx_auction_seller (Index Scan)
-- -------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT auction_id, seller_id
FROM auction
WHERE seller_id = (SELECT member_id FROM member ORDER BY member_id LIMIT 1 OFFSET 5)
  AND auction_id < 50000
ORDER BY auction_id DESC
LIMIT 10;

-- -------------------------
-- [Q5] 낙찰자별 경매 목록
-- 대응: AuctionJpaRepository.findByWinnerId*
-- 예상: idx_auction_winner (Index Scan) 후 status IN 필터
-- -------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT auction_id, winner_id, status
FROM auction
WHERE winner_id = (SELECT member_id FROM member ORDER BY member_id LIMIT 1 OFFSET 10)
  AND status IN ('ENDED', 'SOLD')
ORDER BY auction_id DESC
LIMIT 10;

-- -------------------------
-- [Q6] 카테고리별 유사 경매
-- 대응: AuctionJpaRepository.findSimilarByCategory
-- 예상: idx_auction_category (Index Scan) 후 status 필터 + bid_count 정렬
-- -------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT auction_id, category_id, status, bid_count
FROM auction
WHERE status = 'ACTIVE'
  AND auction_id != 1
  AND category_id = (SELECT category_id FROM auction WHERE auction_id = 1)
ORDER BY bid_count DESC, auction_id DESC
LIMIT 5;

-- -------------------------
-- [Q7] 판매 완료 건수 집계
-- 대응: AuctionJpaRepository.countCompletedBySellerId
-- 예상: idx_auction_seller (Index Scan)
-- -------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT count(*)
FROM auction
WHERE seller_id = (SELECT member_id FROM member ORDER BY member_id LIMIT 1 OFFSET 5)
  AND status IN ('ENDED', 'SOLD')
  AND winner_id IS NOT NULL;

-- ============================================================
-- bid 테이블 쿼리
-- ============================================================

-- -------------------------
-- [Q8] 경매별 입찰 히스토리 (최신순)
-- 대응: BidJpaRepository.findBidHistoryFirstPage
-- 예상: idx_bid_auction_amount or idx_bid_auction_created (auction_id 필터)
--       ORDER BY bid_id DESC는 Index Scan 지원 안 될 수 있음 → Sort 단계 확인
-- -------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT bid_id, auction_id, bid_amount, created_at
FROM bid
WHERE auction_id = (SELECT auction_id FROM auction WHERE status = 'ENDED' LIMIT 1 OFFSET 100)
ORDER BY bid_id DESC
LIMIT 20;

-- -------------------------
-- [Q9] 경매별 최고 입찰가 정렬
-- 대응: BidJpaRepository.findByAuctionIdOrderByBidAmountDesc
-- 예상: idx_bid_auction_amount (auction_id, bid_amount DESC) → Index Scan
-- -------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT bid_id, auction_id, bid_amount
FROM bid
WHERE auction_id = (SELECT auction_id FROM auction WHERE status = 'ENDED' LIMIT 1 OFFSET 100)
ORDER BY bid_amount DESC;

-- -------------------------
-- [Q10] 낙찰 입찰 조회
-- 대응: BidJpaRepository.findWinningBidByAuctionId
-- 예상: idx_bid_auction_amount (auction_id 필터) + is_winning 필터
-- -------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT bid_id, auction_id, is_winning
FROM bid
WHERE auction_id = (SELECT auction_id FROM auction WHERE status = 'ENDED' LIMIT 1 OFFSET 100)
  AND is_winning = true;

-- -------------------------
-- [Q11] 입찰자별 경매 참여 내역 (카테고리 추천)
-- 대응: BidJpaRepository.findTopCategoryIdsByBidderId
-- 예상: idx_bid_bidder (Index Scan) + join auction
-- -------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT a.category_id, count(b.bid_id) AS bid_cnt
FROM bid b
JOIN auction a ON b.auction_id = a.auction_id
WHERE b.bidder_id = (SELECT member_id FROM member ORDER BY member_id LIMIT 1 OFFSET 20)
GROUP BY a.category_id
ORDER BY bid_cnt DESC
LIMIT 3;

-- -------------------------
-- [Q12] 특정 입찰자가 참여 중인 ACTIVE 경매
-- 대응: AuctionJpaRepository.findActiveAuctionsByBidderId*
-- 예상: idx_bid_bidder (Index Scan) + status 필터
-- -------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT DISTINCT a.auction_id, a.status
FROM auction a
JOIN bid b ON b.auction_id = a.auction_id
WHERE b.bidder_id = (SELECT member_id FROM member ORDER BY member_id LIMIT 1 OFFSET 20)
  AND a.status = 'ACTIVE'
ORDER BY a.auction_id DESC
LIMIT 10;

-- ============================================================
-- notification 테이블 쿼리
-- ============================================================

-- -------------------------
-- [Q13] 수신자별 알림 목록 (최신순)
-- 대응: NotificationJpaRepository.findByReceiverIdFirstPage
-- 예상: idx_notification_receiver_created (receiver_id, created_at DESC)
--       ORDER BY notification_id DESC → created_at ≈ id이나 엄밀히 다름
-- -------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT notification_id, receiver_id, is_read
FROM notification
WHERE receiver_id = (SELECT member_id FROM member ORDER BY member_id LIMIT 1 OFFSET 30)
ORDER BY notification_id DESC
LIMIT 20;

-- -------------------------
-- [Q14] 미읽 알림 목록
-- 대응: NotificationJpaRepository.findUnreadByReceiverIdFirstPage
-- 예상: idx_notification_receiver_created (receiver_id 필터) + is_read 필터
-- -------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT notification_id, receiver_id, is_read
FROM notification
WHERE receiver_id = (SELECT member_id FROM member ORDER BY member_id LIMIT 1 OFFSET 30)
  AND is_read = false
ORDER BY notification_id DESC
LIMIT 20;

-- -------------------------
-- [Q15] SSE 재연결용 알림 조회 (id 이후)
-- 대응: NotificationJpaRepository.findByReceiverIdAfter
-- 예상: idx_notification_receiver_created + notification_id 범위 조건
-- -------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT notification_id, receiver_id
FROM notification
WHERE receiver_id = (SELECT member_id FROM member ORDER BY member_id LIMIT 1 OFFSET 30)
  AND notification_id > (
      SELECT notification_id FROM notification ORDER BY notification_id LIMIT 1 OFFSET 500
  )
ORDER BY notification_id ASC;

-- ============================================================
-- 인덱스 사용률 확인 (pg_stat_user_indexes)
-- EXPLAIN ANALYZE 실행 후 이 쿼리로 실제 사용 여부 재확인
-- ============================================================
SELECT
    schemaname,
    relname      AS table_name,
    indexrelname AS index_name,
    idx_scan     AS scan_count,
    idx_tup_read AS tuples_read,
    idx_tup_fetch AS tuples_fetched
FROM pg_stat_user_indexes
WHERE relname IN ('auction', 'bid', 'notification')
ORDER BY relname, idx_scan DESC;