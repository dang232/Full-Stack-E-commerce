# Deprecated Service

Date deprecated: 2026-05-12

Replacement owner: review logic now lives in `product-service`.

Migration evidence: see `.sisyphus/evidence/task-5-review-happy.txt` and `.sisyphus/evidence/task-5-review-error.txt`.

Rollback note: to re-enable the standalone review service, restore a `review-service` runtime entry in compose, expose its service port, and point API Gateway review routes back to the standalone review-service target instead of `product-service`.

Deletion conditions: safe to delete this source after review endpoints, tests, monitoring, and any persisted schema ownership are confirmed stable in `product-service`, and no compose, gateway, CI, docs, or deployment references require standalone `review-service`.
