/**
 * K6 스파이크 테스트 - 입찰 폭주
 *
 * 시나리오: 인기 경매에 단시간 대량 입찰 (스나이핑 시뮬레이션)
 *   - 평소 적은 트래픽 -> 갑자기 대량 입찰 -> 다시 감소
 *   - Redis 분산 락 + DB 커넥션 풀 한계 검증
 *
 * 실행:
 *   k6 run k6/spike-bid.js --env AUCTION_ID=1
 *   k6 run k6/spike-bid.js --env AUCTION_ID=1 --env PEAK_VUS=100
 */
import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";
import { API, headers, signup, login, toLocalISOString } from "./helpers.js";

const bidSuccess = new Counter("bid_success");
const bidFailed = new Counter("bid_failed");
const bidErrorRate = new Rate("bid_error_rate");
const bidDuration = new Trend("bid_duration", true);

const PEAK_VUS = parseInt(__ENV.PEAK_VUS || "50");
const PASSWORD = "loadtest1234";

export const options = {
  scenarios: {
    spike: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "10s", target: 5 },      // warm-up
        { duration: "5s", target: PEAK_VUS }, // spike
        { duration: "30s", target: PEAK_VUS },// sustain peak
        { duration: "10s", target: 5 },       // recover
        { duration: "10s", target: 0 },       // cool-down
      ],
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<5000"],
    bid_error_rate: ["rate<0.5"],
  },
};

export function setup() {
  const maxVUs = PEAK_VUS;
  const ts = Date.now();
  const users = [];

  for (let i = 0; i < maxVUs; i++) {
    const email = `spike_${ts}_${i}@loadtest.com`;
    const nickname = `spike_${ts}_${i}`;
    signup(email, PASSWORD, nickname);
    const token = login(email, PASSWORD);
    if (token) users.push({ token });
  }
  console.log(`Setup: ${users.length}/${maxVUs} users ready`);

  let auctionId = __ENV.AUCTION_ID;
  if (!auctionId) {
    const sellerEmail = `spike_seller_${ts}@loadtest.com`;
    signup(sellerEmail, PASSWORD, `spike_seller_${ts}`);
    const sellerToken = login(sellerEmail, PASSWORD);

    const now = new Date();
    const body = {
      title: `K6 Spike Test ${ts}`,
      description: "Spike load test auction",
      categoryId: 1,
      condition: "GOOD",
      startingPrice: 10000,
      buyNowPrice: 50000000,
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
    }
    for (let i = 0; i < 15; i++) {
      sleep(2);
      const d = http.get(`${API}/auctions/${auctionId}`);
      if (JSON.parse(d.body).data.status === "ACTIVE") break;
    }
  }

  return { users, auctionId: parseInt(auctionId) };
}

export default function (data) {
  const idx = (__VU - 1) % data.users.length;
  const user = data.users[idx];
  if (!user) return;

  // 현재가 조회
  const detail = http.get(`${API}/auctions/${data.auctionId}`, {
    tags: { name: "getAuction" },
  });

  if (detail.status !== 200) {
    bidFailed.add(1);
    bidErrorRate.add(1);
    return;
  }

  const auction = JSON.parse(detail.body).data;
  if (auction.status !== "ACTIVE") {
    sleep(1);
    return;
  }

  const currentPrice = auction.currentPrice || auction.startingPrice;
  const increment = calculateMinIncrement(currentPrice);
  // 스파이크: 살짝 높은 금액으로 경쟁
  const extra = Math.floor(Math.random() * 3) + 1;
  const bidAmount = currentPrice + increment * extra;

  const start = Date.now();
  const res = http.post(
    `${API}/auctions/${data.auctionId}/bids`,
    JSON.stringify({ bidAmount }),
    { headers: headers.auth(user.token), tags: { name: "placeBid" } }
  );
  bidDuration.add(Date.now() - start);

  const ok = check(res, {
    "bid ok or conflict": (r) => r.status === 201 || r.status === 409 || r.status === 400,
  });

  if (res.status === 201) {
    bidSuccess.add(1);
    bidErrorRate.add(0);
  } else if (res.status === 409) {
    bidErrorRate.add(0);
  } else {
    bidFailed.add(1);
    bidErrorRate.add(1);
  }

  sleep(Math.random() * 0.3);
}

function calculateMinIncrement(currentPrice) {
  let rate;
  if (currentPrice < 10000) rate = 0.1;
  else if (currentPrice < 100000) rate = 0.05;
  else if (currentPrice < 1000000) rate = 0.03;
  else rate = 0.01;
  return Math.ceil((currentPrice * rate) / 100) * 100;
}