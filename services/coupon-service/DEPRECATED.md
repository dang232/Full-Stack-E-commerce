# Deprecated Service

Date deprecated: 2026-05-12

Replacement owner: coupon logic now lives in `order-service`.

Migration evidence: see `.sisyphus/evidence/task-4-coupon-happy.txt` and `.sisyphus/evidence/task-4-coupon-error.txt`.

Rollback note: to re-enable the standalone coupon service, restore a `coupon-service` runtime entry in compose, expose its service port, and point API Gateway coupon routes back to the standalone coupon-service target instead of `order-service`.

Deletion conditions: safe to delete this source after coupon endpoints, tests, monitoring, and any persisted schema ownership are confirmed stable in `order-service`, and no compose, gateway, CI, docs, or deployment references require standalone `coupon-service`.
