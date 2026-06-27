# AWS 배포 작업 내역

작성일: 2026-06-28  
브랜치: `chore/aws-deploy` → `develop` 머지 완료

---

## 아키텍처 요약

```
인터넷 → ALB (biddo-alb-1189903426.ap-northeast-2.elb.amazonaws.com)
           ├── EC2-App-1 (43.201.33.170, t3.small)
           └── EC2-App-2 (3.34.180.44, t3.small)
                      │ VPC 내부 통신
                      ▼
           EC2-Infra (54.180.132.114 / 10.0.14.136, t3.large)
             PostgreSQL / Redis Sentinel / Kafka / Elasticsearch
             Prometheus / Grafana / Tempo
```

ECR: `730335638220.dkr.ecr.ap-northeast-2.amazonaws.com/biddo`

---

## 코드 변경 사항

### 1. Redisson 분산 락 (스케줄러 중복 실행 방지)

App 서버 2대가 동일한 `@Scheduled`를 동시에 실행하는 문제 해결.

- `SchedulerLockExecutor` — `tryLock(wait=0)`으로 즉시 스킵
- `AuctionLifecycleScheduler`, `TrustScoreScheduler`에 적용
- 단위 테스트 추가 (락 획득/미획득/인터럽트/예외 케이스)

### 2. Kafka advertised listener 환경변수 추출

EC2 환경에서 `localhost`가 아닌 실제 Private IP로 advertise해야 하는 문제 해결.

```yaml
# docker-compose.yml
KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://${KAFKA_ADVERTISED_HOST:-localhost}:9092,...
```

로컬은 기본값(localhost) 유지, EC2-Infra `.env`에 `KAFKA_ADVERTISED_HOST=10.0.14.136` 설정.

### 3. docker-compose 분리

| 파일 | 용도 |
|------|------|
| `docker-compose.yml` | 로컬 개발 (전체 스택) |
| `docker-compose.infra.yml` | EC2-Infra 운영 (인프라 서비스) |
| `docker-compose.app.yml` | EC2-App 운영 (Spring Boot 앱) |

### 4. Graceful Shutdown

롤링 배포 중 in-flight 요청 보호.

- `server.shutdown: graceful` + `timeout-per-shutdown-phase: 30s`
- `AuctionSseService`, `NotificationSseAdapter`에 `@PreDestroy` 추가 (SSE emitter 정리)
- ALB deregistration delay 30s와 타임아웃 맞춤

**버그 수정**: `application.yml`에 `spring:` 키 중복으로 `lifecycle` 설정이 무시되던 문제 수정.

### 5. Dockerfile + GitHub Actions CI/CD

**`Dockerfile`** — JRE 21 Alpine, bootJar 결과물 복사

**`.github/workflows/deploy.yml`** — 4단계 파이프라인:
1. `test` — Gradle 전체 테스트
2. `build-and-push` — bootJar 빌드 → Docker 이미지 → ECR 푸시 (태그: `${{ github.sha }}`)
3. `deploy-app-1` — SSH → ECR pull → `docker compose up -d` → 헬스체크 (최대 3분)
4. `deploy-app-2` — App-1 헬스체크 통과 후 동일 진행 (롤링)

### 6. application-prod.yml

EC2-Infra Private IP(`10.0.14.136`) 기반 커넥션 설정 및 prod 안전 설정.

```yaml
spring:
  datasource.url: jdbc:postgresql://10.0.14.136:5432/...
  data.redis.sentinel:
    master: biddo-master
    nodes: 10.0.14.136:26379~26381
  elasticsearch.uris: http://10.0.14.136:9200
  kafka.bootstrap-servers: 10.0.14.136:9092
  jpa.hibernate.ddl-auto: validate   # update → validate
  sql.init.mode: never               # 초기화 스크립트 비활성화
management.otlp.tracing.endpoint: http://10.0.14.136:4318/v1/traces
```

`docker-compose.app.yml`에 `SPRING_PROFILES_ACTIVE: prod` 추가, 연결 정보 env var 제거 (prod yml에서 처리).

---

## EC2 서버 설정

### GitHub Secrets (8개)

| Secret | 값 |
|--------|----|
| `AWS_ACCESS_KEY_ID` | IAM 액세스 키 |
| `AWS_SECRET_ACCESS_KEY` | IAM 시크릿 키 |
| `ECR_REGISTRY` | `730335638220.dkr.ecr.ap-northeast-2.amazonaws.com` |
| `ECR_REPOSITORY` | `biddo` |
| `EC2_APP_1_HOST` | `43.201.33.170` |
| `EC2_APP_2_HOST` | `3.34.180.44` |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | biddo.pem 내용 |

### EC2-Infra 설정

- 루트 볼륨 8GB 부족 → 100GB EBS 볼륨(`vol-04b387ac1271e226b`) 추가 연결
- `/var/lib/docker`, `/var/lib/containerd` → 100GB 볼륨으로 이동 (symlink)
- fstab 등록으로 재부팅 후에도 마운트 유지

**Redis Sentinel 버그 수정 2건:**
1. YAML `>` 블록 스칼라에서 `printf '\n'`의 역슬래시가 소실되는 문제 → `echo` 명령으로 교체
2. Redis 7.4에서 hostname 해석 실패 → `sentinel resolve-hostnames yes` / `sentinel announce-hostnames yes` 추가

### 현재 실행 중인 컨테이너 (EC2-Infra)

```
biddo-postgres          Up
biddo-redis-master      Up
biddo-redis-replica     Up
biddo-redis-sentinel-1  Up
biddo-redis-sentinel-2  Up
biddo-redis-sentinel-3  Up
biddo-kafka             Up (healthy)
biddo-kafka-exporter    Up
biddo-elasticsearch     Up (healthy)
biddo-prometheus        Up
biddo-tempo             Up
biddo-grafana           Up
```

### EC2-App 설정

두 인스턴스 모두 `~/biddo/` 에 `docker-compose.app.yml`, `.env` 배치 완료.  
Docker는 GitHub Actions가 첫 배포 시 `docker compose up -d`로 기동.

---

## 배포 흐름

```
git push → main
    └── GitHub Actions
          ├── [test] Gradle 테스트
          ├── [build-and-push] ECR 이미지 푸시
          ├── [deploy-app-1] SSH → pull → up → 헬스체크
          └── [deploy-app-2] SSH → pull → up → 헬스체크
```

---

## 남은 작업

- [ ] `develop → main` PR 머지 → 첫 배포 실행
- [ ] ALB DNS로 API 호출 확인
- [ ] 스케줄러 분산 락 동작 확인 (로그)
- [ ] Grafana 대시보드 구성
- [ ] `monitoring/prometheus/prometheus-prod.yml` App Private IP 확인
  - App-1: `10.0.8.212`, App-2: `10.0.16.58`