# AWS 배포 계획

작성일: 2026-06-26

---

## 아키텍처

```
인터넷
  │
  ▼
ALB (Application Load Balancer)
  │
  ├── EC2-App-1 (t3.small)  Spring Boot
  └── EC2-App-2 (t3.small)  Spring Boot
            │
            │  내부 통신 (VPC Private Subnet)
            ▼
  EC2-Infra (t3.large)
    ├── PostgreSQL
    ├── Redis (master + replica + sentinel x3)
    ├── Kafka (KRaft)
    ├── Elasticsearch
    ├── Prometheus / Grafana / Tempo
```

- **EC2-App x2**: Spring Boot 앱만 실행. ALB가 라운드로빈으로 트래픽 분산
- **EC2-Infra x1**: 모든 인프라 서비스. VPC 내부에서만 접근 가능
- **ALB**: 앱 EC2 헬스체크 + 트래픽 분산. 도메인 없이 ALB DNS 사용
- **ECR**: Spring Boot 앱 Docker 이미지 레지스트리

---

## 기술적 차별점

### 분산 스케줄링 문제 해결

앱 서버 2대가 동일한 `@Scheduled` 작업(경매 시작/종료)을 동시에 실행하면 중복 처리 발생.
Redisson 분산 락을 스케줄러에 적용해 먼저 락을 획득한 1대만 실행하도록 방어.

### 기술 스토리 연결

| 기술 | 역할 |
|------|------|
| Redis Sentinel | 분산 락 저장소 HA 보장 |
| Redisson | 분산 락으로 스케줄러 중복 실행 방지 |
| ALB | 무중단 롤링 배포 + 트래픽 분산 |
| Kafka Consumer Group | 2대 앱 서버가 파티션 분산 처리 |
| GitHub Actions | ECR 푸시 → EC2 롤링 배포 자동화 |

---

## 운영 방식

**EventBridge Scheduler**로 EC2 자동 시작/중지 (14시 ~ 18시 cron). 수동 조작 불필요, 비용 무료.

| 구성 요소 | 상시 | 하루 4시간 기준/월 |
|-----------|------|-------------------|
| EC2-App x2 (t3.small) | ~$30/월 | ~$5/월 |
| EC2-Infra x1 (t3.large) | ~$60/월 | ~$8/월 |
| ALB | ~$16/월 고정 | ~$6/월 |
| ECR | 500MB 무료 | $0 |
| EventBridge Scheduler | 무료 | $0 |
| **합계** | **~$106/월** | **~$19/월** |

---

## 구현 순서

### 1단계: AWS 리소스 생성
- [ ] VPC, Subnet (Public/Private), Security Group 구성
- [ ] EC2-Infra (t3.large) 생성 — Private Subnet
- [ ] EC2-App x2 (t3.small) 생성 — Public Subnet
- [ ] ALB + Target Group 생성
- [ ] ECR 레포지토리 생성
- [ ] EventBridge Scheduler 생성 (EC2 자동 시작/중지 cron)

### 2단계: 코드 수정
- [ ] `@Scheduled` 메서드에 Redisson 분산 락 적용
- [ ] docker-compose 분리: `docker-compose.infra.yml` / `docker-compose.app.yml`
- [ ] `application-prod.yml` 작성 (EC2-Infra 내부 IP 기준)

### 3단계: GitHub Actions CI/CD
- [ ] `deploy.yml` 워크플로우 작성
  - main push → Gradle 빌드 → Docker 이미지 빌드 → ECR 푸시
  - EC2-App-1 SSH → pull & restart → 헬스체크
  - EC2-App-2 SSH → pull & restart (롤링 배포)

### 4단계: 검증
- [ ] ALB DNS로 API 호출 확인
- [ ] EC2-App-1 중지 후 트래픽 EC2-App-2 페일오버 확인
- [ ] 스케줄러 분산 락 동작 확인 (중복 실행 방지)
- [ ] GitHub Actions 자동 배포 확인