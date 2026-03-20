-- Category seed data (대분류 → 중분류)
-- depth: 0=대분류, 1=중분류, 2=소분류
-- 중복 실행 방지: NOT EXISTS 사용

-- 대분류
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '전자기기', NULL, 0, 1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '전자기기' AND depth = 0);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '패션/의류', NULL, 0, 2 WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '패션/의류' AND depth = 0);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '가구/인테리어', NULL, 0, 3 WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '가구/인테리어' AND depth = 0);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '스포츠/레저', NULL, 0, 4 WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '스포츠/레저' AND depth = 0);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '도서/음반', NULL, 0, 5 WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '도서/음반' AND depth = 0);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '게임/취미', NULL, 0, 6 WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '게임/취미' AND depth = 0);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '생활/주방', NULL, 0, 7 WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '생활/주방' AND depth = 0);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '기타', NULL, 0, 8 WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '기타' AND depth = 0);

-- 전자기기 중분류
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '스마트폰', c.category_id, 1, 1 FROM category c WHERE c.name = '전자기기' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '스마트폰' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '노트북/PC', c.category_id, 1, 2 FROM category c WHERE c.name = '전자기기' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '노트북/PC' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '태블릿', c.category_id, 1, 3 FROM category c WHERE c.name = '전자기기' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '태블릿' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '카메라', c.category_id, 1, 4 FROM category c WHERE c.name = '전자기기' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '카메라' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '오디오/음향', c.category_id, 1, 5 FROM category c WHERE c.name = '전자기기' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '오디오/음향' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT 'TV/모니터', c.category_id, 1, 6 FROM category c WHERE c.name = '전자기기' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = 'TV/모니터' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '웨어러블', c.category_id, 1, 7 FROM category c WHERE c.name = '전자기기' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '웨어러블' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '주변기기/액세서리', c.category_id, 1, 8 FROM category c WHERE c.name = '전자기기' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '주변기기/액세서리' AND depth = 1);

-- 패션/의류 중분류
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '남성의류', c.category_id, 1, 1 FROM category c WHERE c.name = '패션/의류' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '남성의류' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '여성의류', c.category_id, 1, 2 FROM category c WHERE c.name = '패션/의류' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '여성의류' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '신발', c.category_id, 1, 3 FROM category c WHERE c.name = '패션/의류' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '신발' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '가방/지갑', c.category_id, 1, 4 FROM category c WHERE c.name = '패션/의류' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '가방/지갑' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '시계/주얼리', c.category_id, 1, 5 FROM category c WHERE c.name = '패션/의류' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '시계/주얼리' AND depth = 1);

-- 가구/인테리어 중분류
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '의자/소파', c.category_id, 1, 1 FROM category c WHERE c.name = '가구/인테리어' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '의자/소파' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '책상/테이블', c.category_id, 1, 2 FROM category c WHERE c.name = '가구/인테리어' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '책상/테이블' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '수납/선반', c.category_id, 1, 3 FROM category c WHERE c.name = '가구/인테리어' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '수납/선반' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '조명', c.category_id, 1, 4 FROM category c WHERE c.name = '가구/인테리어' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '조명' AND depth = 1);

-- 스포츠/레저 중분류
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '헬스/요가', c.category_id, 1, 1 FROM category c WHERE c.name = '스포츠/레저' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '헬스/요가' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '자전거', c.category_id, 1, 2 FROM category c WHERE c.name = '스포츠/레저' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '자전거' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '캠핑', c.category_id, 1, 3 FROM category c WHERE c.name = '스포츠/레저' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '캠핑' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '골프', c.category_id, 1, 4 FROM category c WHERE c.name = '스포츠/레저' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '골프' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '낚시', c.category_id, 1, 5 FROM category c WHERE c.name = '스포츠/레저' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '낚시' AND depth = 1);

-- 도서/음반 중분류
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '도서', c.category_id, 1, 1 FROM category c WHERE c.name = '도서/음반' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '도서' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '음반/LP', c.category_id, 1, 2 FROM category c WHERE c.name = '도서/음반' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '음반/LP' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '악기', c.category_id, 1, 3 FROM category c WHERE c.name = '도서/음반' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '악기' AND depth = 1);

-- 게임/취미 중분류
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '콘솔/게임기', c.category_id, 1, 1 FROM category c WHERE c.name = '게임/취미' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '콘솔/게임기' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '피규어/프라모델', c.category_id, 1, 2 FROM category c WHERE c.name = '게임/취미' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '피규어/프라모델' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '보드게임', c.category_id, 1, 3 FROM category c WHERE c.name = '게임/취미' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '보드게임' AND depth = 1);

-- 생활/주방 중분류
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '주방용품', c.category_id, 1, 1 FROM category c WHERE c.name = '생활/주방' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '주방용품' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '생활가전', c.category_id, 1, 2 FROM category c WHERE c.name = '생활/주방' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '생활가전' AND depth = 1);
INSERT INTO category (name, parent_id, depth, sort_order) SELECT '반려동물용품', c.category_id, 1, 3 FROM category c WHERE c.name = '생활/주방' AND c.depth = 0 AND NOT EXISTS (SELECT 1 FROM category WHERE name = '반려동물용품' AND depth = 1);