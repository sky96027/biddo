/**
 * K6 부하 테스트 - 검색 API
 *
 * 시나리오: 다수의 사용자가 다양한 조건으로 경매 검색
 *   - Elasticsearch 부하 집중
 *   - 키워드, 카테고리, 가격대, 마감 임박 필터 조합
 *
 * 실행:
 *   k6 run k6/load-search.js
 *   k6 run k6/load-search.js --env USERS=50
 */
import http from "k6/http";
import { check, group, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";
import { API, headers, signup, login, createAuction } from "./helpers.js";

// --- Custom Metrics ---
const errorRate = new Rate("errors");
const searchDuration = new Trend("search_duration", true);
const searchWithFilterDuration = new Trend("search_with_filter_duration", true);

// --- Config ---
const USERS = parseInt(__ENV.USERS || "30");
const PASSWORD = "loadtest1234";

const KEYWORDS = ["테스트", "경매", "전자", "의류", "가구", "도서", "스포츠", "K6"];
const SORT_OPTIONS = [null, "price_asc", "price_desc", "ending_soon"];
const END_WITHIN_OPTIONS = [null, "1h", "6h", "24h"];

// --- Options ---
export const options = {
  scenarios: {
    search_load: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "15s", target: Math.ceil(USERS / 2) },
        { duration: "1m", target: USERS },
        { duration: "30s", target: USERS },
        { duration: "15s", target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<3000"],
    errors: ["rate<0.1"],
  },
};

// --- Setup: 검색 대상 데이터 생성 ---
export function setup() {
  const ts = Date.now();

  // 검색 가능한 경매를 몇 개 생성
  const email = `search_seller_${ts}@loadtest.com`;
  signup(email, PASSWORD, `search_seller_${ts}`);
  const token = login(email, PASSWORD);

  if (token) {
    for (let i = 0; i < 5; i++) {
      createAuction(token, {
        title: `${KEYWORDS[i % KEYWORDS.length]} 테스트 상품 ${ts} #${i}`,
        startingPrice: 10000 + i * 10000,
      });
    }
    // 인덱싱 대기
    sleep(3);
  }

  // 인증된 검색을 위한 사용자
  const users = [];
  for (let i = 0; i < Math.min(USERS, 10); i++) {
    const userEmail = `search_user_${ts}_${i}@loadtest.com`;
    signup(userEmail, PASSWORD, `search_user_${ts}_${i}`);
    const userToken = login(userEmail, PASSWORD);
    if (userToken) users.push({ token: userToken });
  }
  console.log(`Setup: ${users.length} users ready for authenticated search`);

  return { users };
}

function randomFrom(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function buildSearchUrl(params) {
  const qs = Object.entries(params)
    .filter(([, v]) => v != null)
    .map(([k, v]) => `${k}=${encodeURIComponent(v)}`)
    .join("&");
  return `${API}/search/auctions${qs ? "?" + qs : ""}`;
}

// --- VU Scenario ---
export default function (data) {
  // 1. 키워드 검색 (단순)
  group("keyword-search", () => {
    const keyword = randomFrom(KEYWORDS);
    const url = buildSearchUrl({ keyword, size: 20 });

    const start = Date.now();
    const res = http.get(url, { tags: { name: "searchKeyword" } });
    searchDuration.add(Date.now() - start);

    check(res, { "search 200": (r) => r.status === 200 }) || errorRate.add(1);
  });
  sleep(0.5);

  // 2. 복합 필터 검색
  group("filtered-search", () => {
    const params = {
      keyword: randomFrom(KEYWORDS),
      minPrice: randomFrom([null, 10000, 50000]),
      maxPrice: randomFrom([null, 100000, 500000]),
      sort: randomFrom(SORT_OPTIONS),
      endWithin: randomFrom(END_WITHIN_OPTIONS),
      size: 20,
    };
    const url = buildSearchUrl(params);

    const start = Date.now();
    const res = http.get(url, { tags: { name: "searchFiltered" } });
    searchWithFilterDuration.add(Date.now() - start);

    check(res, { "filtered search 200": (r) => r.status === 200 }) ||
      errorRate.add(1);
  });
  sleep(0.5);

  // 3. 카테고리 필터 검색
  group("category-search", () => {
    const categoryId = Math.floor(Math.random() * 5) + 1;
    const url = buildSearchUrl({ categoryId, size: 20 });

    const start = Date.now();
    const res = http.get(url, { tags: { name: "searchCategory" } });
    searchDuration.add(Date.now() - start);

    check(res, { "category search 200": (r) => r.status === 200 }) ||
      errorRate.add(1);
  });
  sleep(0.5);

  // 4. 커서 페이지네이션 (다음 페이지 조회)
  group("pagination", () => {
    const keyword = randomFrom(KEYWORDS);
    const firstPage = http.get(
      buildSearchUrl({ keyword, size: 10 }),
      { tags: { name: "searchPage1" } }
    );

    if (firstPage.status === 200) {
      const body = JSON.parse(firstPage.body).data;
      if (body.hasNext && body.nextCursor) {
        const start = Date.now();
        const secondPage = http.get(
          buildSearchUrl({ keyword, cursor: body.nextCursor, size: 10 }),
          { tags: { name: "searchPage2" } }
        );
        searchDuration.add(Date.now() - start);

        check(secondPage, { "page2 200": (r) => r.status === 200 }) ||
          errorRate.add(1);
      }
    }
  });

  sleep(Math.random() * 2 + 1);
}