
## 2026-05-10T00:00:00Z � T1 Docker daemon blocker
- docker-compose.yml already matches T1 infra requirements by inspection and docker compose config succeeds.
- docker compose up -d postgres redis kafka keycloak elasticsearch failed before startup: Docker Desktop daemon reports Docker Desktop is unable to start.
- Postgres psql verification not runnable until Docker Desktop starts successfully.
- Evidence written: .sisyphus/evidence/task-1-healthy.txt, .sisyphus/evidence/task-1-postgres.txt.

## 2026-05-10T00:00:00Z - T1 Docker daemon blocker (ASCII restatement)
- docker-compose.yml already matches T1 infra requirements by inspection and docker compose config succeeds.
- docker compose up -d postgres redis kafka keycloak elasticsearch failed before startup: Docker Desktop daemon reports Docker Desktop is unable to start.
- Postgres psql verification not runnable until Docker Desktop starts successfully.
- Evidence written: .sisyphus/evidence/task-1-healthy.txt and .sisyphus/evidence/task-1-postgres.txt.

- T11: lsp_diagnostics unavailable in this environment because jdtls command is not installed. Used per-service mvn compile as Java verification; both product-service and search-service exited 0.

## 2026-05-10T18:10+07:00 - T46 verification notes
- lsp_diagnostics for Java remains blocked because jdtls is not installed; Maven compile/test is verification source.
- Live MoMo sandbox QA remains blocked without MOMO_PARTNER_CODE, MOMO_ACCESS_KEY, and MOMO_SECRET_KEY; no external sandbox call made.

2026-05-10T18:11+07:00 T46: Java LSP diagnostics unavailable because jdtls is not installed. Maven wrapper compile/test is current verification source. Real MoMo sandbox QA blocked: MOMO_PARTNER_CODE, MOMO_ACCESS_KEY, and MOMO_SECRET_KEY not provided; no sandbox call made.

## 2026-05-10T19:05+07:00 - P1-2 verification blockers
- Java LSP diagnostics remain blocked because jdtls is not installed.
- Full product-service mvn test initially failed before code validation because JaCoCo 0.8.13 cannot instrument Java 26 classfile major version 70; rerun with -Djacoco.skip=true reached Spring context.
- Full product-service Spring context test with JaCoCo skipped is blocked by local Postgres/Flyway connection refused at localhost:5432; scoped storage unit tests pass without external services.
- Real R2/MinIO integration QA blocked until bucket, credentials, endpoint env/config, and external AV scanner dependency exist; no Docker/MinIO/R2 calls were run.

## 2026-05-10T20:58+07:00 - F3 Real Manual QA blocker
- Fresh runtime check for F3 failed: Docker daemon reports `Docker Desktop is unable to start`.
- `curl.exe -sS -I http://localhost:8080/actuator/health` failed: could not connect to server on port 8080.
- Evidence written: `.sisyphus/evidence/task-F3-qa.txt`.
- F3 remains unchecked until Docker/container/cluster runtime exists and full seller->buyer->fulfillment curl QA can run.

## 2026-05-10T19:58+07:00 - P1-3 verification blockers
- Java LSP diagnostics remain blocked because jdtls is not installed; Maven test/compile passed and was used as verification source.
- Real product/review signed URL runtime QA remains blocked by no object-storage credentials/endpoint/bucket and no Docker/MinIO/R2 runtime per task constraints.
- AV scanner integration remains out of scope; upload activation uses avScanClean as deterministic hook input.

## 2026-05-10T20:16+07:00 - P1-4 invoice R2 blockers
- Java LSP diagnostics still blocked: jdtls command missing; Maven compile/tests used as source of truth.
- Real R2/MinIO runtime QA intentionally not run for P1-4 per task constraint; private bucket credentials/env still needed for live signed URL/object access validation.


## 2026-05-10T20:41+07:00 - P2-1 ledger
- Java LSP diagnostics still blocked because jdtls is not installed; Maven focused tests and compile passed with -Djacoco.skip=true.
- Runtime DB QA remains blocked by no Docker/Postgres per task constraints; synthetic in-memory ledger balance test covers 100 orders/refunds.

- Do not run payment-service Maven clean/test and compile in parallel: both touch target; maven-clean-plugin can fail deleting target while compile uses it.

## 2026-05-10T20:46+07:00 - P2-1 boundary fix
- LSP diagnostics remain blocked by missing jdtls; Maven ledger tests and compile passed after boundary move.
