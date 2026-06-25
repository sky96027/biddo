# K6 부하 테스트 결과

## 테스트 환경

| 항목 | 값 |
|------|-----|
| 서버 | localhost (개발 머신, 단일 인스턴스) |
| OS | Windows 11 + WSL2 (k6 실행) |
| CPU | AMD Ryzen 7 5800X (8코어 / 16스레드) |
| RAM | 16GB |
| JDK | OpenJDK 21.0.11 |
| DB | PostgreSQL 16 (Docker) |
| Redis | Redis 7 (Docker) |
| Kafka | Confluent 7.6.0 KRaft (Docker) |
| Elasticsearch | 8.13.0 (Docker) |
| K6 | v1.6.1 |
| HikariCP | maximum-pool-size=20 |
| Kafka consumer | concurrency=3 (파티션 3개 풀 활용) |

> 로컬 환경이므로 운영 환경과 수치 차이가 있을 수 있음. 상대적 비교와 병목 식별이 목적.

---

## 1. 경매 목록 조회 (load-auction-list.js)

**시나리오**: 인기 경매 → 상세 조회 → 유사 경매 → 입찰 내역 순회

### VU별 비교

| VU | p50 | p95 | max | error rate | throughput |
|----|-----|-----|-----|------------|------------|
| 10 | 4.74ms | 10.46ms | 200ms | 0% | 7.3 req/s |
| 50 | 3.68ms | 6.68ms | 921ms | 0.02% (1건) | 36.7 req/s |
| 100 | 3.68ms | 6.73ms | 929ms | 0% | 73.2 req/s |

### 엔드포인트별 응답 시간 (100 VU)

| 엔드포인트 | avg | p90 | p95 |
|-----------|-----|-----|-----|
| GET /auctions/popular | 4.58ms | 5ms | 6ms |
| GET /auctions/{id} | 3.3ms | 4ms | 5ms |
| GET /auctions/{id}/similar | 7.07ms | 7ms | 8ms |
| GET /auctions/{id}/bids | 3.93ms | 4ms | 4ms |

### 분석

- 10 → 100 VU에서 p95가 오히려 개선(10.46ms → 6.73ms). JVM 워밍업 효과.
- 50 VU에서 popular max 31s 스파이크 1건 발생. outlier이며 p95는 영향 없음.
- similar 경매(Elasticsearch 조회)가 가장 느리나 p95 8ms로 절대값 낮음.
- 100 VU까지 p95 < 11ms, 에러 0%. 조회 계층은 충분한 여유.

---

## 2. 검색 API (load-search.js)

**시나리오**: 키워드 검색 → 복합 필터 검색 → 카테고리 검색 → 커서 페이지네이션

### VU별 비교

| VU | p50 | p95 | max | error rate | throughput |
|----|-----|-----|-----|------------|------------|
| 10 | 3.85ms | 5.88ms | 72ms | 0% | 7.6 req/s |
| 50 | 3.44ms | 4.64ms | 74ms | 0% | 38.1 req/s |
| 100 | 3.27ms | 4.61ms | 691ms | 0.02% (2건) | 79.2 req/s |

### 검색 유형별 응답 시간 (100 VU)

| 유형 | avg | p90 | p95 |
|------|-----|-----|-----|
| 키워드 / 카테고리 검색 | 3.4ms | 4ms | 5ms |
| 복합 필터 검색 | 3.52ms | 4ms | 5ms |

### 분석

- 10 → 100 VU p95 5.88ms → 4.61ms로 역시 JVM 워밍업 효과 확인.
- 키워드 검색과 복합 필터 간 응답 시간 차이 0.1ms로 사실상 동일.
- 100 VU까지 p95 < 6ms, 처리량 79 req/s. Elasticsearch 부하 여유 충분.
- 에러 2건은 순간적 타임아웃 outlier. p95 임계값(3000ms) 통과.

---

## 3. 분산 입찰 (load-bid.js)

**시나리오**: 여러 경매에 다수 참가자가 분산 입찰. 경매당 평균 3명 경합 (현실적 밀도).

### VU별 비교

| VU | 경매 수 | bid p50 | bid p95 | bid max | conflict | success rate | throughput |
|----|---------|---------|---------|---------|----------|-------------|------------|
| 30 | 10 | 11ms | 14ms | 75ms | 0건 | 100% | 25.6 req/s |
| 100 | 34 | 11ms | 16ms | 958ms | 1건 | 100% | 81.5 req/s |
| 200 | 67 | 14ms | 30ms | 1,010ms | 15건 | 100% | 141.4 req/s |
| 500 | 167 | 24ms | 66ms | 1,090ms | 71건 | 100% | 276.6 req/s |

### 3-2. 커넥션 풀 / Consumer concurrency 튜닝 

| 설정 | bid p95 | conflict | throughput | 비고 |
|------|---------|----------|------------|------|
| pool=10, concurrency=1 | 26ms | 13건 | 144.8 req/s | 기본값 |
| pool=20, concurrency=3 | 30ms | 15건 | 141.4 req/s | 튜닝 후 |

### 분석

- **30 → 200 VU**: bid p95 14ms → 30ms (2.1x). 부하 6.7배 증가 대비 응답 증가는 완만.
- **conflict 증가**: 30 VU 0건 → 200 VU 15건. 경합 밀도(3명/경매)가 동일해도 절대 동시 요청 수가 늘며 소폭 증가. 총 입찰 11,416건 대비 0.1%로 허용 범위.
- **입찰 처리 과정**: Redis 분산 락 획득 → 현재가 조회/검증 → DB 저장 → Kafka 이벤트 발행 → 락 해제. 조회 API 대비 2~3배 느린 것은 정상.
- **커넥션 풀 튜닝 효과 없음**: pool=20, concurrency=3으로 변경해도 p95가 오히려 소폭 증가. 200 VU 피크 시 active 커넥션이 최대 7개에 불과해 풀이 병목이 아님을 확인. 입찰 요청이 Redis 분산 락 대기 구간에서 이미 직렬화되어 DB에 도달하는 동시 요청 수 자체가 적기 때문.
- **예상 병목**: Redis 분산 락 직렬화 구간. 같은 경매 경합 요청은 락 획득 순서대로 처리되므로 DB/커넥션 풀 확장으로는 개선 불가.
- **500 VU에서 병목 전환**: VU 증가로 경매 수(167개)가 늘며 락을 동시에 통과하는 요청도 증가. 커넥션 풀 20개가 포화되어 pending 최대 24개 발생. 200 VU까지는 Redis 락이, 500 VU부터는 커넥션 풀도 병목으로 합류.

---

## 4. 500 VU Grafana 지표 분석

> 500 VU 테스트 중 Grafana 대시보드 스냅샷 기반 분석.

### CPU

| 지표 | Mean | Max |
|------|------|-----|
| System CPU Usage | 0.267 | **0.997** |
| Process CPU Usage | 0.031 | 0.292 |

- System CPU가 99.7%까지 도달. **500 VU가 이 머신의 CPU saturation point**.
- Process(JVM) 기여는 29.2%로 나머지는 Docker/OS 오버헤드.

### Threads

| 지표 | Mean | Max |
|------|------|-----|
| Live threads | 187 | **317** |
| Peak threads | - | 317 |

- 500 VU 구간에서 317 thread 피크. Tomcat 기본 max-thread(200) + Kafka consumer threads(8그룹 × 3) + 기타.
- 스레드 경합이 응답시간 증가에 기여했을 가능성.

### HikariCP

| 지표 | 값 |
|------|-----|
| Connections Size | 20 |
| Connection Timeout Count | **0** |
| Connection Acquire Time (peak) | 1.5ms |
| Connection Usage Time (peak) | 150ms |

- Timeout 0건 — pending 24개가 발생했어도 30초 기본 타임아웃 내 처리 완료.
- Acquire Time 1.5ms — 대기 영향은 있었으나 심각한 수준 아님.
- Usage Time 150ms 급증 — 500 VU 구간에서 커넥션 점유 시간 증가. 트랜잭션 처리 지연 반영.

### GC

| 지표 | Mean | Max |
|------|------|-----|
| G1 Evacuation Pause (count/s) | 0.229 | 1.801 |
| G1 Evacuation Pause (duration) | 406µs | 6ms |

- Minor GC만 발생, STW 최대 6ms. GC는 병목 아님.
- Heap Used 4.1% — 메모리 여유 충분.

### Kafka Consumer Lag

| 토픽 | Total Lag |
|------|-----------|
| bid-events | **8.04K** |
| auction-events | 0 |

- 500 VU 테스트로 notification lag이 8K로 증가. 다른 consumer group은 모두 0.
- `biddo-notification`의 단건 INSERT 방식이 고부하 시 처리 속도를 따라가지 못함 → 이슈 [#85](https://github.com/sky96027/biddo/issues/85) 참조.

### 추가 관찰

- **DEBUG 로그 최대 4,236/s** — Hibernate SQL DEBUG 로깅이 고부하 시 CPU 오버헤드를 가중. 부하 테스트 및 운영 시 INFO 레벨 권장.
- **WARN 로그 최대 232/s** — 입찰 conflict 재시도 경고 로그가 주요 원인.

---

## 병목 식별 요약

| 구간 | 상태 | 비고 |
|------|------|------|
| 경매 조회 (DB + Redis) | 양호 | 100 VU p95 < 7ms |
| 검색 (Elasticsearch) | 양호 | 100 VU p95 < 6ms |
| 입찰 — Redis 락 직렬화 | 관찰 | 200 VU까지 주 병목, p95 30ms |
| 입찰 — 커넥션 풀 | 관찰 | 500 VU에서 pending 24개 발생 |
| CPU | **포화** | 500 VU System CPU 99.7% |
| Kafka notification lag | 관찰 | 500 VU 이후 8K lag 누적 |

---

## 병목 개선 사이클

### Stage 1 — 관측 가능성 확보 및 컨슈머 최적화 (완료)

병목 원인을 제거 가능한 단계부터 순서대로 처리.

#### Round 1 — DEBUG 로깅 제거 ✅

- **조치**: 운영/테스트 프로파일에서 Hibernate SQL 로그 레벨 DEBUG → INFO
- **효과**: CPU 오버헤드 감소, 고부하 시 DEBUG 4,236/s → 제거

#### Round 2 — Tomcat 스레드 풀 튜닝 ✅

- **조치**: `server.tomcat.threads.max` 조정 (기본값 200 → 150)
- **효과**: 500 VU 구간 불필요한 스레드 생성 억제

#### Round 3 — 커넥션 풀 조정 ✅

- **조치**: `maximum-pool-size` 20 유지, `minimum-idle` 조정
- **결과**: 500 VU pending 24개 발생했어도 timeout 0건 확인 → 현 설정 유지

#### Round 4 — Notification consumer 배치 처리 ✅

- **조치**: Kafka 배치 리스너 도입 + `saveAll()` bulk INSERT → 이슈 [#85](https://github.com/sky96027/biddo/issues/85)
  - `batchKafkaListenerContainerFactory` (concurrency=3) 추가
  - `BidEventConsumer`, `AuctionEventConsumer` 배치 처리 리팩터링
  - 배치 내 경매 조회 N회 → 1회 (`findByIdIn`) 및 알림 단건 INSERT → 일괄 INSERT

**500 VU 재측정 결과 (Round 1~4 적용 후)**

| 지표 | 적용 전 | 적용 후 | 변화 |
|---------------|------------|------------|--------------------------|
| bid p50 | 24ms | 27ms | +3ms |
| bid p95 | 66ms | 80ms | +14ms |
| bid max | 1,090ms | 451ms | **-59%** |
| conflict | 71건 | 216건 | +145건 (반복 횟수 2.4배↑) |
| 처리량 | 276.6 req/s | 286.7 req/s | +3.7% |
| bid-events lag | **8,040** | **0** | **완전 해소** |

> bid p95가 소폭 증가한 것은 누적 경매 데이터로 DB 스캔 범위가 늘었기 때문. conflict 증가도 반복 횟수(11,416 → 27,928) 증가에 비례하며 충돌률은 0.62% → 0.77%로 유사.
> 핵심 성과: **bid-events 컨슈머 랙 8K → 0**, **max 응답시간 1,090ms → 451ms**.

---

### Stage 2 — 핵심 입찰 경로 개선

#### Round 5 — Redis 락 대기 시간 측정 ✅

→ 이슈 [#86](https://github.com/sky96027/biddo/issues/86)

`RedissonAuctionLock`에 Micrometer Timer 추가 (`auction.lock.wait`, `auction.lock.hold`).

**실측 결과 (500 VU)**

| 지표 | 50 VU | 500 VU |
|------------------|--------|--------|
| 락 대기(wait) avg | 0.64ms | 7.96ms |
| 락 보유(hold) avg | 8.6ms | 42.6ms |
| 락 보유(hold) max | - | 798ms |
| 타임아웃 | 0 | 0 |

**분석**

- wait < hold — 병목은 경합(wait)이 아니라 락 안 트랜잭션 처리 시간(hold) 자체.
- 500 VU에서 hold가 8.6ms → 42.6ms로 5배 증가한 원인은 DB 커넥션 풀 경쟁. 락 범위와 무관하게 동시 요청이 커넥션을 잡으려 대기하면서 쿼리 응답이 느려짐.
- `processAutoBids`가 루프마다 DB 쿼리를 반복 실행하므로 자동입찰 체인이 길수록 hold가 추가 증가.

**개선 방향 검토 및 결정**

| 방향 | 효과 | 트레이드오프 | 결정 |
|------|------|------------|-------|
| 락 범위 축소 | 미미 — hold의 2% | 회원 조회만 락 밖으로 뺄 수 있음. 나머지(현재가 검증·저장·자동입찰)는 원자성 보장 필요로 락 안 유지 필수 | **보류** |
| 낙관적 락 | wait 제거 | 고경합 시 retry storm 위험. `processAutoBids` 체인 전체 재시도 로직 필요. 구조 선행 개선 필요 | **보류** |
| DB 커넥션 풀 증설 | hold 내 쿼리 대기 감소 | 로컬 단일 인스턴스 환경에서 효과 불명확 | **보류** |

**결론**: 500 VU bid p95 167ms는 Redis 분산 락이 직렬화를 보장하는 구조에서 `processAutoBids`를 포함한 현 설계의 한계치에 가까움. 낙관적 락 도입은 `processAutoBids` 구조 개선을 선행한 후 재검토.
