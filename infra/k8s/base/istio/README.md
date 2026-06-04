# Istio Service Mesh — mTLS Manifests

## Overview

These manifests configure Istio for inter-service mTLS within the `vnshop` namespace.
They are **templates only** — they require an Istio control plane to be installed before applying.

## Prerequisites

1. Istio installed via `istioctl install -f istio-base.yaml`
2. Namespace label applied: `kubectl label namespace vnshop istio-injection=enabled`
3. Workload pods restarted to pick up sidecar injection

## Applying the Manifests

```bash
# Install Istio control plane first
istioctl install -f infra/k8s/base/istio/istio-base.yaml --verify

# Then apply the mesh policies via kustomize
kubectl apply -k infra/k8s/base/istio/
```

## Files

| File | Purpose |
|---|---|
| `istio-base.yaml` | IstioOperator — control plane configuration (minimal profile) |
| `peer-authentication.yaml` | Mesh-wide STRICT mTLS for the vnshop namespace |
| `destination-rules.yaml` | Per-service ISTIO_MUTUAL TLS destination rules |
| `authorization-policies.yaml` | Deny-all baseline + allow rules for known service-to-service calls |
| `virtual-services.yaml` | Retry and timeout policies matching Resilience4j defaults |

## Service Account Mapping

Authorization policies reference these service accounts (must match `serviceaccount.yaml` per service):

| Service | Service Account Principal |
|---|---|
| api-gateway | `cluster.local/ns/vnshop/sa/vnshop-api-gateway` |
| order-service | `cluster.local/ns/vnshop/sa/vnshop-order-service` |

All other services currently share `vnshop-workload`. Create per-service accounts before enforcing fine-grained policies.

## CI Validation

Run `infra/scripts/validate-istio-call-graph.sh` in CI to detect service-to-service calls in
application code that are not covered by an authorization policy. The script exits 1 on mismatch.

## Maintenance Cadence

- Review policies after any new service or inter-service call is added.
- Rotate mTLS certificates automatically via Istio's built-in cert manager (default 24 h TTL).
- Audit `AuthorizationPolicy` entries quarterly.

## 6-Month Archive Policy

If these manifests have **not been applied to any environment** within 6 months of creation
(created 2026-06-04), move this directory to `infra/k8s/archive/istio/` and open a ticket
to decide whether the service-mesh initiative should be continued or formally dropped.
