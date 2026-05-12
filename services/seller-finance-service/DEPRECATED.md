# Deprecated Service

Date deprecated: 2026-05-12

Replacement owner: seller finance logic now lives in `order-service`.

Migration evidence: see `.sisyphus/evidence/task-6-finance-happy.txt` and `.sisyphus/evidence/task-6-finance-error.txt`.

Rollback note: to re-enable the standalone seller finance service, restore a `seller-finance-service` runtime entry in compose, expose its service port, and point API Gateway seller-finance routes back to the standalone seller-finance-service target instead of `order-service`.

Deletion conditions: safe to delete this source after seller-finance endpoints, tests, monitoring, and any persisted schema ownership are confirmed stable in `order-service`, and no compose, gateway, CI, docs, or deployment references require standalone `seller-finance-service`.
