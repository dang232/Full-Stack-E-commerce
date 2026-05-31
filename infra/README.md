# Infrastructure Docs

Use these docs when changing infrastructure behavior or responding to production issues.

1. [Migration Policy](migration-policy.md) explains Flyway versioning, safe schema changes, rollback preparation, backups, validation, and staging before production.
2. [Incident Runbook](incident-runbook.md) gives first-response steps for VNShop service, database, Kafka, latency, and flash-sale incidents.
3. [Backups](backups/README.md) explains backup artifacts, restore safety, and retention for database and Keycloak exports.
4. [Evolution Guardrails](evolution-guardrails.md) defines service readiness, API versioning, event evolution, and multi-service boundary rules.
5. [Production No-Go Checklist](production-no-go-checklist.md) gives the release owner a go or no-go gate for the 13-service Kubernetes deployment.
6. [Service Split Assessment](service-split-assessment.md) assesses service boundaries, distributed-monolith risk, and priority fixes.
