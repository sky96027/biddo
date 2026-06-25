/**
 * K6 부하 테스트 - 경매 목록 조회
 *
 * 시나리오: 다수의 사용자가 경매 목록/인기/상세/유사 경매를 반복 조회
 *   - 읽기 부하 집중 (DB 쿼리, Redis, Elasticsearch)
 *   - 인증 없이 접근 가능한 조회 API 위주
 *
 * 실행:
 *   k6 run k6/load-auction-list.js
 *   k6 run k6/load-auction-list.js --env USERS=50
 */
import http from "k6/http";
import { check, group, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";
import { API, headers, signup, login, createAuction } from "./helpers.js";

// --- Custom Metrics ---
const errorRate = new Rate("errors");
const popularDuration = new Trend("popular_duration", true);
const detailDuration = new Trend("detail_duration", true);
const similarDuration = new Trend("similar_duration", true);
const bidHistoryDuration = new Trend("bid_history_duration", true);

// --- Config ---
const USERS = parseInt(__ENV.USERS || "30");
const AUCTIONS = parseInt(__ENV.AUCTIONS || "5");
const PASSWORD = "loadtest1234";

// --- Options ---
export const options = {
  scenarios: {
    auction_reads: {
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
    http_req_duration: ["p(95)<2000"],
    errors: ["rate<0.1"],
  },
};

// --- Setup: 조회할 경매 데이터 준비 ---
export function setup() {
  const ts = Date.now();
  const auctionIds = [];

  for (let i = 0; i < AUCTIONS; i++) {
    const email = `list_seller_${ts}_${i}@loadtest.com`;
    signup(email, PASSWORD, `list_seller_${ts}_${i}`);
    const token = login(email, PASSWORD);

    if (!token) continue;

    const res = createAuction(token, {
      title: `K6 List Test Auction ${ts} #${i}`,
      startingPrice: 10000 + i * 5000,
    });

    if (res.status === 201) {
      auctionIds.push(JSON.parse(res.body).data.auctionId);
    }
  }

  // ACTIVE 대기
  console.log("Setup: waiting for auctions to become ACTIVE...");
  for (let attempt = 0; attempt < 15; attempt++) {
    sleep(2);
    let allActive = true;
    for (const id of auctionIds) {
      const detail = http.get(`${API}/auctions/${id}`);
      if (JSON.parse(detail.body).data.status !== "ACTIVE") {
        allActive = false;
        break;
      }
    }
    if (allActive) break;
  }
  console.log(`Setup: ${auctionIds.length} auctions ready`);

  return { auctionIds };
}

// --- VU Scenario ---
export default function (data) {
  if (data.auctionIds.length === 0) return;

  // 1. 인기 경매 조회
  group("popular", () => {
    const start = Date.now();
    const res = http.get(`${API}/auctions/popular?size=10`, {
      tags: { name: "popular" },
    });
    popularDuration.add(Date.now() - start);
    check(res, { "popular 200": (r) => r.status === 200 }) || errorRate.add(1);
  });
  sleep(0.5);

  // 2. 경매 상세 조회 (랜덤 경매)
  const auctionId = data.auctionIds[Math.floor(Math.random() * data.auctionIds.length)];

  group("detail", () => {
    const start = Date.now();
    const res = http.get(`${API}/auctions/${auctionId}`, {
      tags: { name: "auctionDetail" },
    });
    detailDuration.add(Date.now() - start);
    check(res, { "detail 200": (r) => r.status === 200 }) || errorRate.add(1);
  });
  sleep(0.5);

  // 3. 유사 경매 조회
  group("similar", () => {
    const start = Date.now();
    const res = http.get(`${API}/auctions/${auctionId}/similar?size=6`, {
      tags: { name: "similar" },
    });
    similarDuration.add(Date.now() - start);
    check(res, { "similar 200": (r) => r.status === 200 }) || errorRate.add(1);
  });
  sleep(0.5);

  // 4. 입찰 내역 조회
  group("bid-history", () => {
    const start = Date.now();
    const res = http.get(`${API}/auctions/${auctionId}/bids?size=20`, {
      tags: { name: "bidHistory" },
    });
    bidHistoryDuration.add(Date.now() - start);
    check(res, { "bidHistory 200": (r) => r.status === 200 }) || errorRate.add(1);
  });

  sleep(Math.random() * 2 + 1);
}