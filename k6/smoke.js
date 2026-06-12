/**
 * K6 스모크 테스트 - 전체 API 플로우
 *
 * 시나리오: 일반 사용자 행동 시뮬레이션
 *   1. 회원가입 & 로그인
 *   2. 경매 목록 검색 & 조회
 *   3. 경매 상세 조회
 *   4. 입찰
 *   5. 입찰 히스토리 조회
 *
 * 실행:
 *   k6 run k6/smoke.js
 *   k6 run k6/smoke.js --env AUCTION_ID=1
 */
import http from "k6/http";
import { check, group, sleep } from "k6";
import { Rate } from "k6/metrics";
import { API, headers, signup, login, createAuction } from "./helpers.js";

const errorRate = new Rate("errors");
const PASSWORD = "loadtest1234";

export const options = {
  stages: [
    { duration: "30s", target: 5 },
    { duration: "1m", target: 10 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    http_req_duration: ["p(95)<3000"],
    errors: ["rate<0.1"],
  },
};

export function setup() {
  let auctionId = __ENV.AUCTION_ID;

  if (!auctionId) {
    const ts = Date.now();
    const email = `smoke_seller_${ts}@loadtest.com`;
    signup(email, PASSWORD, `smoke_seller_${ts}`);
    const token = login(email, PASSWORD);

    if (token) {
      const res = createAuction(token);
      if (res.status === 201) {
        auctionId = JSON.parse(res.body).data.auctionId;
        console.log(`Setup: created auction ${auctionId}`);
        sleep(5);
      }
    }
  }

  return { auctionId: auctionId ? parseInt(auctionId) : null };
}

export default function (data) {
  const ts = Date.now();
  const vuId = `${ts}_${__VU}_${__ITER}`;

  // 1. Auth
  let token;
  group("auth", () => {
    const email = `smoke_${vuId}@loadtest.com`;
    const nickname = `smoke_${vuId}`;

    const signupRes = signup(email, PASSWORD, nickname);
    check(signupRes, { "signup 201": (r) => r.status === 201 }) ||
      errorRate.add(1);

    token = login(email, PASSWORD);
    check(token, { "login ok": (t) => t !== null }) || errorRate.add(1);
  });

  if (!token) return;
  sleep(1);

  // 2. Search
  group("search", () => {
    const res = http.get(`${API}/search/auctions?keyword=test&size=10`, {
      tags: { name: "searchAuctions" },
    });
    check(res, { "search 200": (r) => r.status === 200 }) ||
      errorRate.add(1);
  });
  sleep(0.5);

  // 3. Popular auctions
  group("popular", () => {
    const res = http.get(`${API}/auctions/popular`, {
      tags: { name: "popular" },
    });
    check(res, { "popular 200": (r) => r.status === 200 }) ||
      errorRate.add(1);
  });
  sleep(0.5);

  // 4. Auction detail + bid
  if (data.auctionId) {
    group("auction-detail", () => {
      const res = http.get(`${API}/auctions/${data.auctionId}`, {
        tags: { name: "auctionDetail" },
      });

      const ok = check(res, { "detail 200": (r) => r.status === 200 });
      if (!ok) {
        errorRate.add(1);
        return;
      }

      const auction = JSON.parse(res.body).data;
      if (auction.status !== "ACTIVE") return;

      // bid
      const currentPrice = auction.currentPrice || auction.startingPrice;
      const increment = calculateMinIncrement(currentPrice);

      const bidRes = http.post(
        `${API}/auctions/${data.auctionId}/bids`,
        JSON.stringify({ bidAmount: currentPrice + increment }),
        { headers: headers.auth(token), tags: { name: "placeBid" } }
      );

      check(bidRes, {
        "bid accepted or conflict": (r) =>
          r.status === 201 || r.status === 409,
      }) || errorRate.add(1);
    });
    sleep(0.5);

    // 5. Bid history
    group("bid-history", () => {
      const res = http.get(
        `${API}/auctions/${data.auctionId}/bids?size=10`,
        { tags: { name: "bidHistory" } }
      );
      check(res, { "history 200": (r) => r.status === 200 }) ||
        errorRate.add(1);
    });
  }

  sleep(1);
}

function calculateMinIncrement(currentPrice) {
  let rate;
  if (currentPrice < 10000) rate = 0.1;
  else if (currentPrice < 100000) rate = 0.05;
  else if (currentPrice < 1000000) rate = 0.03;
  else rate = 0.01;
  return Math.ceil((currentPrice * rate) / 100) * 100;
}