# Redis Sentinel 도입

> **상태**: 채택
> **작성일**: 2026-06-23

## 1. 배경 및 목적

현재 Biddo는 Redis 단일 노드(Standalone)를 사용하고 있다. Redis는 분산 락(Redisson), TTL 기반 경매 스케줄링, 인기 경매 캐시, Refresh Token 저장 등 핵심 기능에 관여하므로, Redis 장애 시 서비스 전체가 영향받는 SPOF(Single Point of Failure)이다.

본 개선안은 Redis Sentinel을 도입하여 자동 페일오버를 확보하고 SPOF를 제거하는 것을 목적으로 한다.

---

## 2. 현황 분석

### 2.1 현재 구조

| 항목 | 현재 상태 |
| --- | --- |
| Redis 구성 | 단일 노드 (Docker Compose) |
| 클라이언트 | Lettuce (일반 커맨드) + Redisson (분산 락) |
| 사용처 | 분산 락, TTL 경매 스케줄링, 인기 경매 Sorted Set, 현재가/입찰 수 캐시, Refresh Token, 최근 검색어 |
| 장애 대응 | 없음 — Redis 다운 시 위 기능 전부 중단 |

### 2.2 Redis 장애 시 영향 범위

| 기능 | 영향 |
| --- | --- |
| 입찰 | 분산 락 획득 불가 → 입찰 전면 중단 |
| 경매 활성화/종료 | TTL 만료 이벤트 유실 (보조 스케줄러로 지연 복구 가능) |
| 즉시구매 | 분산 락 의존 → 중단 |
| 인기 경매 조회 | 캐시 미스 → DB 직접 조회 또는 실패 |
| 인증 | Refresh Token 조회 불가 → 토큰 갱신 실패 |

### 2.3 장애 발생 가능 시나리오

- EC2 인스턴스 재부팅/AWS 유지보수
- Docker 컨테이너 OOM Kill (메모리 제한 초과)
- 디스크 풀로 RDB/AOF 스냅샷 저장 실패
- 배포/운영 중 의도치 않은 컨테이너 재생성

---

## 3. 후보 비교: Sentinel vs Cluster

### 3.1 Redis Sentinel

자동 페일오버 전용 솔루션. 마스터 1대 + 레플리카 N대 + Sentinel 프로세스 3대로 구성된다.

- Sentinel 과반수(2/3)가 마스터 장애에 동의하면 레플리카를 마스터로 승격
- 데이터는 분산되지 않음 — 마스터 1대가 모든 읽기/쓰기 처리
- 확장은 수직(메모리 증설)만 가능

### 3.2 Redis Cluster

자동 페일오버 + 데이터 분산. 마스터 N대 + 레플리카 N대로 구성된다.

- 16,384개 슬롯에 키를 분산 저장하여 수평 확장 가능
- 각 마스터에 레플리카가 있어 페일오버 내장 (Sentinel 불필요)
- 최소 구성: 마스터 3 + 레플리카 3 = 6대

### 3.3 비교

| 항목 | Sentinel | Cluster |
| --- | --- | --- |
| 목적 | 장애 대응 | 장애 대응 + 데이터 분산 |
| 최소 노드 | 마스터1 + 레플리카1 + Sentinel3 | 마스터3 + 레플리카3 |
| 수평 확장 | 불가 | 가능 |
| 멀티키 연산 | 제약 없음 | 동일 슬롯 내에서만 가능 (해시태그 필요) |
| 운영 복잡도 | 낮음 | 높음 (슬롯 리밸런싱, 노드 관리) |
| keyspace notification | 기존과 동일 | 각 노드가 독립 발행 → 리스너 수정 필요 |
| 코드 변경량 | 설정 변경 수준 | 키 설계 재검토 + 리스너 수정 + 테스트 인프라 변경 |

---

## 4. 결정: Sentinel 채택

### 4.1 채택 근거

- **장애 대응만 필요**: 현재 Redis 메모리 사용량과 처리량이 단일 노드로 충분. 수평 확장이 필요한 트래픽이 아님
- **코드 변경 최소화**: Lettuce, Redisson 모두 Sentinel 모드를 네이티브 지원. 설정 변경만으로 전환 가능
- **키 설계 유지**: 멀티키 연산 제약이 없으므로 기존 키 구조를 변경할 필요 없음
- **keyspace notification 호환**: 마스터 1대에서 발행하므로 `RedisKeyExpirationListener` 수정 불필요

### 4.2 Cluster 미채택 근거

- 최소 6대 운영으로 인프라 비용 과잉 (EC2 2대 프로젝트에서 Redis 6대)
- 해시태그 적용, keyspace notification 리스너 수정 등 코드 변경 범위가 큼
- 슬롯 리밸런싱, 노드 장애 대응 등 1인 운영에 부담
- 해결하려는 문제(SPOF 제거)에 비해 복잡도가 과도

---

## 5. 구현 계획

### 5.1 구성

```
Master (쓰기/읽기) ──복제──▶ Replica (대기/읽기)

Sentinel ×3 (마스터 감시, 과반수 투표로 페일오버 결정)
```

### 5.2 설정 변경

**application.yml**
```yaml
spring:
  data:
    redis:
      sentinel:
        master: biddo-master
        nodes: sentinel1:26379,sentinel2:26379,sentinel3:26379
```

**RedissonConfig**
```java
config.useSentinelServers()
    .setMasterName("biddo-master")
    .addSentinelAddress("redis://sentinel1:26379", "redis://sentinel2:26379", "redis://sentinel3:26379");
```

### 5.3 인프라

- Docker Compose에 Redis Master, Replica, Sentinel ×3 컨테이너 추가
- 배포 환경(EC2)에서는 Master + Replica를 서로 다른 인스턴스에 배치하여 단일 인스턴스 장애에 대비

### 5.4 검증 항목

- [ ] 마스터 강제 종료 시 Sentinel이 레플리카를 승격하는지 확인
- [ ] 페일오버 후 Lettuce/Redisson 클라이언트가 새 마스터에 자동 재연결하는지 확인
- [ ] 페일오버 중 입찰 요청의 실패/재시도 동작 확인
- [ ] keyspace notification이 새 마스터에서 정상 발행되는지 확인