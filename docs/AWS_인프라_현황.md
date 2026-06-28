# AWS 인프라 현황

최종 업데이트: 2026-06-28

---

## 아키텍처 요약

```
인터넷 → CloudFront E1FKQDRIT6PB7D (biddo.click / www.biddo.click)
           ├── S3 biddo-front-730335638220 (프론트엔드 정적 파일)
           └── ALB (biddo-alb-1189903426.ap-northeast-2.elb.amazonaws.com)
                 ├── EC2-App-1 (43.203.11.121, t3.small)
                 └── EC2-App-2 (54.116.191.0, t3.small)
                            │ VPC 내부 통신
                            ▼
                 EC2-Infra (동적으로 변경 / 10.0.14.136, t3.large)
                   PostgreSQL / Redis Sentinel / Kafka / Elasticsearch
                   Prometheus / Grafana / Tempo

         CloudFront EJ86WHT8XK6JU
           └── S3 biddo-uploads (사용자 업로드 파일)
```

ECR: `730335638220.dkr.ecr.ap-northeast-2.amazonaws.com/biddo`

---

## S3 & CloudFront

### S3 버킷

| 버킷 | 용도 |
|------|------|
| `biddo-front-730335638220` | 프론트엔드 정적 파일 (S3 웹사이트 호스팅) |
| `biddo-uploads` | 사용자 업로드 파일 (이미지 등) |

### CloudFront 배포

| 배포 ID | CloudFront 도메인 | 커스텀 도메인 | 오리진 | 용도 |
|---------|------------------|---------------|--------|------|
| `E1FKQDRIT6PB7D` | d27fw0aik3qhr.cloudfront.net | biddo.click, www.biddo.click | S3(프론트) + ALB | 프론트엔드 + API |
| `EJ86WHT8XK6JU` | d3hgbiuxcsupkr.cloudfront.net | - | biddo-uploads S3 | 업로드 파일 |

---

## EC2 구성

### EC2-Infra (t3.large)

- Public IP: `동적으로 변경` / Private IP: `10.0.14.136`

**실행 중인 컨테이너:**

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

### EC2-App (t3.small × 2)

| 인스턴스 | Public IP | Private IP |
|----------|-----------|------------|
| App-1 | `43.201.33.170` | `10.0.8.212` |
| App-2 | `3.34.180.44` | `10.0.16.58` |

`~/biddo/`에 `docker-compose.app.yml`, `.env` 배치. GitHub Actions CI/CD로 배포.

---

## 애플리케이션 구성

### 스케줄러 분산 락

App 서버 2대의 `@Scheduled` 중복 실행 방지.

- `SchedulerLockExecutor` — `tryLock(wait=0)`으로 즉시 스킵
- `AuctionLifecycleScheduler`, `TrustScoreScheduler`에 적용

### Kafka advertised listener

로컬은 기본값(localhost), EC2-Infra `.env`에 `KAFKA_ADVERTISED_HOST=10.0.14.136` 설정.

```yaml
KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://${KAFKA_ADVERTISED_HOST:-localhost}:9092,...
```

### docker-compose 파일 구성

| 파일 | 용도 |
|------|------|
| `docker-compose.yml` | 로컬 개발 (전체 스택) |
| `docker-compose.infra.yml` | EC2-Infra 운영 (인프라 서비스) |
| `docker-compose.app.yml` | EC2-App 운영 (Spring Boot 앱) |

### Graceful Shutdown

- `server.shutdown: graceful` + `timeout-per-shutdown-phase: 30s`
- `AuctionSseService`, `NotificationSseAdapter`에 `@PreDestroy` 추가 (SSE emitter 정리)
- ALB deregistration delay 30s와 타임아웃 맞춤

### application-prod.yml

```yaml
spring:
  datasource.url: jdbc:postgresql://10.0.14.136:5432/...
  data.redis.sentinel:
    master: biddo-master
    nodes: 10.0.14.136:26379~26381
  elasticsearch.uris: http://10.0.14.136:9200
  kafka.bootstrap-servers: 10.0.14.136:9092
  jpa.hibernate.ddl-auto: validate
  sql.init.mode: never
management.otlp.tracing.endpoint: http://10.0.14.136:4318/v1/traces
```

---

## CI/CD 파이프라인

`.github/workflows/deploy.yml` — `main` 브랜치 push 시 실행.

```
git push → main
    └── GitHub Actions
          ├── [test] Gradle 전체 테스트
          ├── [build-and-push] bootJar → Docker 이미지 → ECR 푸시 (태그: sha)
          ├── [deploy-app-1] SSH → ECR pull → docker compose up → 헬스체크
          └── [deploy-app-2] App-1 통과 후 동일 진행 (롤링)
```

### GitHub Secrets

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

---

## TODO

- [ ] `develop → main` PR 머지 → 첫 배포 실행
- [ ] ALB DNS로 API 호출 확인
- [ ] 스케줄러 분산 락 동작 확인 (로그)
- [ ] Grafana 대시보드 구성