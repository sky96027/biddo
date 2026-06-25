/**
 * K6 부하 테스트 - 다중 경매 분산 입찰
 *
 * 시나리오: 여러 경매에 다수의 참가자가 분산 입찰
 *   - 현실적 부하 패턴 (단일 경매 폭주가 아닌 다중 경매 동시 진행)
 *   - 각 VU가 랜덤 경매를 선택하여 입찰
 *
 * 사전 조건:
 *   서버 실행 상태 (localhost:9090)
 *
 * 경매 수는 VU / 3 (경매당 평균 3명 경합)으로 자동 계산.
 *
 * 실행:
 *   k6 run k6/load-bid.js
 *   k6 run k6/load-bid.js --env USERS=100
 */
import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";
import { API, headers, signup, login, createAuction, toLocalISOString } from "./helpers.js";

// --- Custom Metrics ---
const bidSuccess = new Counter("bid_success");
const bidFailed = new Counter("bid_failed");
const bidConflict = new Counter("bid_conflict");
const bidErrorRate = new Rate("bid_error_rate");
const bidDuration = new Trend("bid_duration", true);

// --- Config ---
const USERS = parseInt(__ENV.USERS || "30");
const BIDDERS_PER_AUCTION = 3;
const AUCTIONS = Math.max(1, Math.ceil(USERS / BIDDERS_PER_AUCTION));
const PASSWORD = "loadtest1234";

// --- Options ---
export const options = {
  setupTimeout: "5m",
  scenarios: {
    distributed_bid: {
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
    bid_error_rate: ["rate<0.3"],
  },
};

// --- Setup: 사용자 + 다중 경매 생성 ---
export function setup() {
  const ts = Date.now();
  const users = [];

  // 1) 사용자 생성 & 로그인
  for (let i = 0; i < USERS; i++) {
    const email = `load_bidder_${ts}_${i}@loadtest.com`;
    const nickname = `load_bidder_${ts}_${i}`;

    signup(email, PASSWORD, nickname);
    const token = login(email, PASSWORD);
    if (!token) {
      console.error(`Login failed for ${email}`);
      continue;
    }
    users.push({ email, token });
  }
  console.log(`Setup: ${users.length}/${USERS} users ready`);

  // 2) 판매자 계정으로 다중 경매 생성
  const auctionIds = [];
  for (let i = 0; i < AUCTIONS; i++) {
    const sellerEmail = `load_seller_${ts}_${i}@loadtest.com`;
    signup(sellerEmail, PASSWORD, `load_seller_${ts}_${i}`);
    const sellerToken = login(sellerEmail, PASSWORD);

    if (!sellerToken) {
      console.error(`Seller login failed: ${sellerEmail}`);
      continue;
    }

    const res = createAuction(sellerToken, {
      title: `K6 Load Bid Test ${ts} #${i}`,
      startingPrice: 10000 + i * 5000,
    });

    if (res.status === 201) {
      const id = JSON.parse(res.body).data.auctionId;
      auctionIds.push(id);
      console.log(`Setup: created auction ${id}`);
    } else {
      console.error(`Setup: auction creation failed - ${res.status} ${res.body}`);
    }
  }

  // ACTIVE 대기 (첫 번째 경매만 확인 — 동일 시점에 생성했으므로 하나가 ACTIVE면 전부 ACTIVE)
  console.log("Setup: waiting for auctions to become ACTIVE...");
  if (auctionIds.length > 0) {
    for (let attempt = 0; attempt < 15; attempt++) {
      sleep(2);
      const detail = http.get(`${API}/auctions/${auctionIds[0]}`);
      const status = JSON.parse(detail.body).data.status;
      if (status === "ACTIVE") break;
    }
  }
  console.log(`Setup: ${auctionIds.length} auctions ready`);

  return { users, auctionIds };
}

// --- VU Scenario ---
export default function (data) {
  const user = data.users[__VU % data.users.length];
  if (!user || data.auctionIds.length === 0) return;

  // 랜덤 경매 선택
  const auctionId = data.auctionIds[Math.floor(Math.random() * data.auctionIds.length)];

  // 현재가 조회
  const detail = http.get(`${API}/auctions/${auctionId}`, {
    tags: { name: "getAuction" },
  });

  let currentPrice = 10000;
  if (detail.status === 200) {
    const auction = JSON.parse(detail.body).data;
    currentPrice = auction.currentPrice || auction.startingPrice;
  }

  // 입찰 (최대 3회 재시도)
  const MAX_RETRIES = 3;
  for (let attempt = 0; attempt <= MAX_RETRIES; attempt++) {
    if (attempt > 0) {
      // 재시도 시 현재가 재조회
      const refresh = http.get(`${API}/auctions/${auctionId}`, {
        tags: { name: "getAuction" },
      });
      if (refresh.status === 200) {
        const auction = JSON.parse(refresh.body).data;
        currentPrice = auction.currentPrice || auction.startingPrice;
      }
    }

    const increment = calculateMinIncrement(currentPrice);
    const bidAmount = currentPrice + increment;

    const start = Date.now();
    const res = http.post(
      `${API}/auctions/${auctionId}/bids`,
      JSON.stringify({ bidAmount }),
      {
        headers: headers.auth(user.token),
        tags: { name: "placeBid" },
      }
    );
    bidDuration.add(Date.now() - start);

    if (res.status === 201) {
      check(res, { "bid accepted (201)": () => true });
      bidSuccess.add(1);
      bidErrorRate.add(0);
      break;
    } else if (res.status === 400 && res.body && res.body.includes("BID_003")) {
      if (attempt === MAX_RETRIES) {
        bidConflict.add(1);
        bidErrorRate.add(0);
      }
      continue;
    } else if (res.status === 409) {
      bidConflict.add(1);
      bidErrorRate.add(0);
      break;
    } else {
      bidFailed.add(1);
      bidErrorRate.add(1);
      console.warn(`VU${__VU} bid failed: ${res.status} ${res.body}`);
      break;
    }
  }

  sleep(Math.random() * 2 + 0.5);
}

function calculateMinIncrement(currentPrice) {
  let rate;
  if (currentPrice < 10000) rate = 0.1;
  else if (currentPrice < 100000) rate = 0.05;
  else if (currentPrice < 1000000) rate = 0.03;
  else rate = 0.01;
  return Math.ceil((currentPrice * rate) / 100) * 100;
}

// --- Teardown: 결과 요약 ---
export function teardown(data) {
  if (!data.auctionIds || data.auctionIds.length === 0) return;

  console.log(`\n=== Bid Summary per Auction ===`);
  for (const auctionId of data.auctionIds) {
    const res = http.get(`${API}/auctions/${auctionId}/bids?size=5`);
    if (res.status === 200) {
      const body = JSON.parse(res.body).data;
      const bids = body.content;
      const topBid = bids.length > 0 ? bids[0].bidAmount : "N/A";
      console.log(`  Auction ${auctionId}: ${bids.length}+ bids, top = ${topBid}`);
    }
  }
}