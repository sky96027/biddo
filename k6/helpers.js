import http from "k6/http";

export const BASE_URL = __ENV.BASE_URL || "http://localhost:9090";
export const API = `${BASE_URL}/api/v1`;

// 서버가 타임존 없는 LocalDateTime을 기대하므로 로컬 시간 포맷 반환
export function toLocalISOString(date) {
  const pad = (n) => String(n).padStart(2, "0");
  return (
    date.getFullYear() +
    "-" + pad(date.getMonth() + 1) +
    "-" + pad(date.getDate()) +
    "T" + pad(date.getHours()) +
    ":" + pad(date.getMinutes()) +
    ":" + pad(date.getSeconds())
  );
}

export const headers = {
  json: { "Content-Type": "application/json" },
  auth(token) {
    return {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    };
  },
};

export function signup(email, password, nickname) {
  return http.post(
    `${API}/auth/signup`,
    JSON.stringify({ email, password, nickname }),
    { headers: headers.json, tags: { name: "signup" } }
  );
}

export function login(email, password) {
  const res = http.post(
    `${API}/auth/login`,
    JSON.stringify({ email, password }),
    { headers: headers.json, tags: { name: "login" } }
  );
  if (res.status === 200) {
    return JSON.parse(res.body).data.accessToken;
  }
  return null;
}

export function createAuction(token, overrides = {}) {
  const now = new Date();
  const startTime = toLocalISOString(new Date(now.getTime() + 5000));
  const endTime = toLocalISOString(
    new Date(now.getTime() + 2 * 60 * 60 * 1000)
  );

  const body = Object.assign(
    {
      title: `K6 Load Test Auction ${Date.now()}`,
      description: "K6 load test auction item",
      categoryId: 1,
      condition: "GOOD",
      startingPrice: 10000,
      buyNowPrice: 1000000,
      startTime,
      endTime,
      imageUrls: ["https://example.com/test.jpg"],
    },
    overrides
  );

  return http.post(`${API}/auctions`, JSON.stringify(body), {
    headers: headers.auth(token),
    tags: { name: "createAuction" },
  });
}
