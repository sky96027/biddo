/**
 * K6 부하 테스트 - 동시 입찰
 *
 * 시나리오: 다수의 사용자가 동일 경매에 동시에 입찰
 * 검증 목표:
 *   - Redis 분산 락 기반 동시성 제어 정상 동작
 *   - 입찰 정합성 (중복 낙찰 없음, 금액 순서 보장)
 *   - 응답 시간 & 에러율
 *
 * 사전 조건:
 *   서버 실행 상태 (localhost:9090)
 *   ACTIVE 상태의 경매가 1건 이상 존재
 *
 * 실행:
 *   k6 run k6/concurrent-bid.js
 *   k6 run k6/concurrent-bid.js --env AUCTION_ID=1 --env USERS=50
 */
import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";
import { API, headers, signup, login, toLocalISOString } from "./helpers.js";

// --- Custom Metrics ---
const bidSuccess = new Counter("bid_success");
const bidFailed = new Counter("bid_failed");
const bidConflict = new Counter("bid_conflict");
const bidErrorRate = new Rate("bid_error_rate");
const bidDuration = new Trend("bid_duration", true);

// --- Config ---
const USERS = parseInt(__ENV.USERS || "30");
const AUCTION_ID = __ENV.AUCTION_ID || null;
const PASSWORD = "loadtest1234";

// --- Options ---
export const options = {
  scenarios: {
    concurrent_bid: {
      executor: "per-vu-iterations",
      vus: USERS,
      iterations: 5,
      maxDuration: "2m",
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<2000"],
    bid_error_rate: ["rate<0.3"],
    bid_success: [`count>=${USERS}`],
  },
};

// --- Setup: 테스트 사용자 생성 + 로그인 + 경매 준비 ---
export function setup() {
  const users = [];
  const ts = Date.now();

  // 1) 사용자 생성 & 로그인
  for (let i = 0; i < USERS; i++) {
    const email = `bidder_${ts}_${i}@loadtest.com`;
    const nickname = `bidder_${ts}_${i}`;

    signup(email, PASSWORD, nickname);
    const token = login(email, PASSWORD);
    if (!token) {
      console.error(`Login failed for ${email}`);
      continue;
    }
    users.push({ email, token });
  }
  console.log(`Setup: ${users.length}/${USERS} users ready`);

  // 2) 경매 ID 결정 (환경변수 or 새로 생성)
  let auctionId = AUCTION_ID;
  if (!auctionId) {
    // 판매자 계정 생성
    const sellerEmail = `seller_${ts}@loadtest.com`;
    signup(sellerEmail, PASSWORD, `seller_${ts}`);
    const sellerToken = login(sellerEmail, PASSWORD);

    // 경매 생성 (5초 후 시작, 2시간 후 종료)
    const now = new Date();
    const body = {
      title: `K6 Concurrent Bid Test ${ts}`,
      description: "Load test auction for concurrent bidding",
      categoryId: 1,
      condition: "GOOD",
      startingPrice: 10000,
      buyNowPrice: 10000000,
      startTime: toLocalISOString(new Date(now.getTime() + 5000)),
      endTime: toLocalISOString(new Date(now.getTime() + 2 * 60 * 60 * 1000)),
      imageUrls: ["https://example.com/test.jpg"],
    };

    const res = http.post(`${API}/auctions`, JSON.stringify(body), {
      headers: headers.auth(sellerToken),
    });

    if (res.status === 201) {
      auctionId = JSON.parse(res.body).data.auctionId;
      console.log(`Setup: created auction ${auctionId}`);
    } else {
      console.error(`Setup: auction creation failed - ${res.status} ${res.body}`);
    }

    // PENDING -> ACTIVE 전환 대기 (폴링, 최대 30초)
    console.log("Setup: waiting for auction to become ACTIVE...");
    let status = "PENDING";
    for (let i = 0; i < 15; i++) {
      sleep(2);
      const detail = http.get(`${API}/auctions/${auctionId}`);
      status = JSON.parse(detail.body).data.status;
      if (status === "ACTIVE") break;
    }
    console.log(`Setup: auction ${auctionId} status = ${status}`);
  }

  return { users, auctionId: parseInt(auctionId) };
}

// --- VU Scenario ---
export default function (data) {
  const user = data.users[__VU - 1];
  if (!user) return;

  const auctionId = data.auctionId;
  const MAX_RETRIES = 3;

  for (let attempt = 0; attempt <= MAX_RETRIES; attempt++) {
    // 현재가 조회
    const detail = http.get(`${API}/auctions/${auctionId}`, {
      tags: { name: "getAuction" },
    });

    let currentPrice = 10000;
    if (detail.status === 200) {
      const auction = JSON.parse(detail.body).data;
      currentPrice = auction.currentPrice || auction.startingPrice;
    }

    // 현재가 기반 입찰 금액 계산 (최소 증가 단위 적용)
    const increment = calculateMinIncrement(currentPrice);
    const bidAmount = currentPrice + increment;

    // 입찰
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
      check(res, {
        "bid accepted (201)": () => true,
        "response has bidId": (r) => {
          const body = JSON.parse(r.body);
          return body.data && body.data.bidId;
        },
      });
      bidSuccess.add(1);
      bidErrorRate.add(0);
      break;
    } else if (res.status === 400 && res.body && res.body.includes("BID_003")) {
      // 최소 입찰 금액 미달 — 현재가가 이미 올랐으므로 재시도
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

  sleep(Math.random() * 0.5);
}

// --- 최소 입찰 증가 단위 계산 (비즈니스 규칙) ---
function calculateMinIncrement(currentPrice) {
  let rate;
  if (currentPrice < 10000) rate = 0.1;
  else if (currentPrice < 100000) rate = 0.05;
  else if (currentPrice < 1000000) rate = 0.03;
  else rate = 0.01;

  const raw = currentPrice * rate;
  return Math.ceil(raw / 100) * 100;
}

// --- Teardown: 결과 요약 ---
export function teardown(data) {
  if (!data.auctionId) return;

  const res = http.get(`${API}/auctions/${data.auctionId}/bids?size=20`);
  if (res.status === 200) {
    const bids = JSON.parse(res.body).data.content;
    console.log(`\n=== Bid History (top ${bids.length}) ===`);
    bids.forEach((b, i) => {
      console.log(
        `  ${i + 1}. ${b.bidderNickname} - ${b.bidAmount.toLocaleString()} (${b.bidType})`
      );
    });

    // 정합성 검증: 금액 내림차순 정렬 확인
    let ordered = true;
    for (let i = 1; i < bids.length; i++) {
      if (bids[i - 1].bidAmount < bids[i].bidAmount) {
        ordered = false;
        break;
      }
    }
    console.log(
      `\nIntegrity check - descending order: ${ordered ? "PASS" : "FAIL"}`
    );
  }
}