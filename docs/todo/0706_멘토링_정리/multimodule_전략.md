# 멀티모듈 설계

## 디렉토리 구조 및 모듈 책임

```text
fds-system/
├── common/         # DTO, 공통 Exception, 상수, 유틸
├── domain/         # Entity, JPA, Rule 정의, 점수 계산기
├── api/            # REST API (login, transfer, admin)
├── gateway/        # TokenVerifier 포함된 API Gateway (optional)
├── auth/           # 인증 서버 (token 발급)
├── detector/       # Kafka consumer + RiskEval 판단 로직
├── txworker/       # Kafka consumer + RDB INSERT + DLQ emit
├── syncworker/     # DLQ consumer → 복구/보정 담당
├── batch/          # 통계, 리포트, 사후 분석
├── external/       # Redis, Kafka, S3 설정, 외부 연동
├── support/        # Infra 유틸 (Logging, tracing, Sentry, Slack)
```


## 모듈간 의존성
```text
common       <-- 모든 모듈에서 참조 한다.
domain       <-- api, detector, txworker
external     <-- api, detector, txworker, syncworker
support      <-- api, detector, txworker (Slack, tracing)

api
├── dependsOn: domain, external, support
├── exposes: REST (login, transfer)

auth
├── independent (token 발급만)

detector
├── dependsOn: domain, external
├── consumes: transfer.created
├── emits: transfer.approved / transfer.flagged

txworker
├── dependsOn: domain, external, support
├── consumes: transfer.initiated
├── emits: transfer.created or DLQ

syncworker
├── consumes: tx.sync_required
├── emits: correction_log
```