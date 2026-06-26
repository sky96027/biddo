-- ============================================================
-- 인덱스 실효성 검증용 더미 데이터 생성 스크립트
-- 목적: PostgreSQL이 Seq Scan 대신 Index Scan을 선택하도록
--       충분한 데이터(10만 건 이상) 투입
--
-- 실행 순서: member → auction → bid → notification
-- 실행 환경: 개발/스테이징 DB (운영 실행 금지)
-- 소요 시간: 약 30~60초
-- ============================================================

-- -------------------------
-- 0. 실행 전 확인
-- -------------------------
-- SELECT count(*) FROM member;
-- SELECT count(*) FROM auction;
-- 데이터가 이미 충분하면 실행하지 않을 것

-- -------------------------
-- 1. 더미 회원 (1,000명)
-- -------------------------
INSERT INTO member (email, password, nickname, trust_score, role, created_at, updated_at)
SELECT
    'dummy_user_' || i || '@test.com',
    '$2a$10$dummyhashforloadtestingonly00000000000000000000000000',
    'dummy' || i,
    (random() * 5)::numeric(2,1),
    'USER',
    NOW() - (random() * INTERVAL '365 days'),
    NOW()
FROM generate_series(1, 1000) AS g(i)
ON CONFLICT (email) DO NOTHING;

-- -------------------------
-- 2. 더미 경매 (100,000건)
-- 상태 분포: ACTIVE 40%, ENDED 30%, PENDING 15%, SOLD 10%, CANCELLED 5%
-- -------------------------
WITH
    member_ids AS (SELECT member_id FROM member ORDER BY member_id LIMIT 1000),
    category_ids AS (SELECT category_id FROM category ORDER BY category_id),
    status_weights(status, weight) AS (
        VALUES
            ('ACTIVE',    40),
            ('ENDED',     30),
            ('PENDING',   15),
            ('SOLD',      10),
            ('CANCELLED',  5)
    ),
    status_pool AS (
        SELECT status
        FROM status_weights
        CROSS JOIN generate_series(1, weight)
    ),
    statuses AS (SELECT status FROM status_pool ORDER BY random()),
    condition_pool(cond) AS (
        VALUES ('NEW'),('LIKE_NEW'),('LIKE_NEW'),('GOOD'),('GOOD'),('GOOD'),('FAIR'),('FAIR'),('POOR')
    )
INSERT INTO auction (
    seller_id, category_id, title, description, item_condition,
    starting_price, current_price, buy_now_price,
    bid_count, view_count, status, winner_id,
    start_time, end_time, created_at, updated_at
)
SELECT
    (SELECT member_id FROM member_ids OFFSET (g.i % 1000) LIMIT 1),
    (SELECT category_id FROM category_ids OFFSET (g.i % (SELECT count(*) FROM category)) LIMIT 1),
    '더미 상품 ' || g.i || ' - ' || (ARRAY['스마트폰','노트북','의자','운동화','카메라','시계','책','게임기','가방','태블릿'])[1 + (g.i % 10)],
    '더미 상품 설명입니다. 상태 양호합니다. 직거래 선호.',
    (ARRAY['NEW','LIKE_NEW','GOOD','FAIR','POOR'])[1 + (g.i % 5)],
    (1000 + (random() * 999000)::bigint / 1000) * 1000,   -- 1천~100만, 1000원 단위
    (1000 + (random() * 999000)::bigint / 1000) * 1000,
    CASE WHEN random() > 0.4
        THEN (1000 + (random() * 1999000)::bigint / 1000) * 1000
        ELSE NULL
    END,
    (random() * 50)::int,
    (random() * 500)::int,
    -- 상태 분포 (ACTIVE 40%, ENDED 30%, PENDING 15%, SOLD 10%, CANCELLED 5%)
    CASE
        WHEN g.i % 100 < 40 THEN 'ACTIVE'
        WHEN g.i % 100 < 70 THEN 'ENDED'
        WHEN g.i % 100 < 85 THEN 'PENDING'
        WHEN g.i % 100 < 95 THEN 'SOLD'
        ELSE 'CANCELLED'
    END,
    -- winner_id: ENDED/SOLD 상태 경매의 60%에 winner 존재
    CASE
        WHEN g.i % 100 BETWEEN 40 AND 69 AND random() > 0.4
            THEN (SELECT member_id FROM member_ids OFFSET ((g.i + 1) % 1000) LIMIT 1)
        WHEN g.i % 100 BETWEEN 85 AND 94
            THEN (SELECT member_id FROM member_ids OFFSET ((g.i + 1) % 1000) LIMIT 1)
        ELSE NULL
    END,
    -- start_time: ACTIVE/ENDED/SOLD는 과거, PENDING은 미래
    CASE
        WHEN g.i % 100 < 85 OR g.i % 100 >= 95
            THEN NOW() - (random() * INTERVAL '30 days')
        ELSE NOW() + (random() * INTERVAL '7 days')
    END,
    -- end_time
    CASE
        WHEN g.i % 100 < 40  -- ACTIVE: 미래에 종료
            THEN NOW() + (random() * INTERVAL '7 days')
        WHEN g.i % 100 < 70  -- ENDED: 과거에 종료
            THEN NOW() - (random() * INTERVAL '20 days')
        WHEN g.i % 100 < 85  -- PENDING: 미래에 종료
            THEN NOW() + (random() * INTERVAL '14 days')
        ELSE NOW() - (random() * INTERVAL '10 days')
    END,
    NOW() - (random() * INTERVAL '60 days'),
    NOW() - (random() * INTERVAL '10 days')
FROM generate_series(1, 100000) AS g(i);

-- -------------------------
-- 3. 더미 입찰 (약 300,000건)
-- ACTIVE/ENDED/SOLD 경매에 평균 3건의 입찰
-- -------------------------
INSERT INTO bid (auction_id, bidder_id, bid_amount, bid_type, is_winning, created_at)
SELECT
    a.auction_id,
    (SELECT member_id FROM member
     WHERE member_id != a.seller_id
     ORDER BY random() LIMIT 1),
    a.starting_price + ((random() * a.starting_price * 0.5)::bigint / 1000) * 1000,
    (ARRAY['MANUAL','MANUAL','MANUAL','AUTO'])[1 + (floor(random() * 4))::int],
    false,
    a.start_time + (random() * (COALESCE(a.end_time, NOW()) - a.start_time))
FROM auction a
CROSS JOIN generate_series(1, 3) AS g(n)
WHERE a.status IN ('ACTIVE', 'ENDED', 'SOLD')
  AND a.bid_count > 0;

-- winning bid: 각 경매의 가장 높은 입찰을 is_winning=true 로 표시
UPDATE bid b
SET is_winning = true
FROM (
    SELECT DISTINCT ON (auction_id) bid_id
    FROM bid
    ORDER BY auction_id, bid_amount DESC
) top
WHERE b.bid_id = top.bid_id;

-- -------------------------
-- 4. 더미 알림 (약 200,000건)
-- -------------------------
INSERT INTO notification (receiver_id, auction_id, type, message, is_read, created_at)
SELECT
    (SELECT member_id FROM member ORDER BY random() LIMIT 1),
    a.auction_id,
    (ARRAY['BID','OUTBID','AUCTION_END','WON','PRICE_ALERT','KEYWORD_MATCH'])[1 + (g.n % 6)],
    '알림 메시지 ' || a.auction_id || '-' || g.n,
    random() > 0.3,   -- 70% 읽음
    a.created_at + (random() * INTERVAL '30 days')
FROM auction a
CROSS JOIN generate_series(1, 2) AS g(n)
WHERE a.status IN ('ACTIVE', 'ENDED', 'SOLD');

-- -------------------------
-- 5. 통계 확인
-- -------------------------
SELECT 'member'       AS tbl, count(*) FROM member
UNION ALL SELECT 'auction',      count(*) FROM auction
UNION ALL SELECT 'bid',          count(*) FROM bid
UNION ALL SELECT 'notification', count(*) FROM notification;

SELECT status, count(*) FROM auction GROUP BY status ORDER BY count(*) DESC;