# mTLS Between Services (Istio)

## Overview

VNShop uses Istio to enforce mutual TLS (mTLS) between all services in Kubernetes.
In local Docker Compose development, mTLS is not used — services communicate over plain HTTP/gRPC on an isolated Docker bridge network.

## Local vs Kubernetes

| Environment | Transport | Auth mechanism |
|---|---|---|
| Docker Compose (local) | Plain HTTP / gRPC | Docker network isolation |
| Kubernetes (dev/staging/prod) | mTLS via Istio sidecar | Istio SPIFFE certificates |

In Kubernetes the application code does not change. The Istio sidecar proxy (Envoy) intercepts all inbound and outbound traffic and negotiates mTLS transparently. From the service's perspective it sends and receives plain HTTP/gRPC on localhost.

## Manifests

All Istio policy resources live under `infra/k8s/base/istio/`:

| File | Purpose |
|---|---|
| `istio-base.yaml` | IstioOperator — install profile, mesh config. Applied via `istioctl install`. |
| `peer-authentication.yaml` | Namespace-wide `STRICT` mTLS — rejects any plain-text inbound traffic. |
| `destination-rules.yaml` | Outbound TLS mode `ISTIO_MUTUAL` for every vnshop service. |
| `authorization-policies.yaml` | Deny-all baseline + explicit allow rules per service principal. |
| `virtual-services.yaml` | Traffic routing rules. |

Namespace sidecar injection is enabled via the `istio-injection: enabled` label on each overlay namespace manifest (`infra/k8s/overlays/{env}/namespace.yaml`).

## Call Graph Enforced by AuthorizationPolicies

```
api-gateway        --> all services (HTTP, ports 8081-8093)
order-service      --> inventory-service  (gRPC, port 9093)
order-service      --> payment-service    (gRPC, port 9094)
order-service      --> shipping-service   (gRPC, port 9095)
payment-service    --> order-service      (async via Kafka broker, no direct mesh call)
notification-service                      (inbound from Kafka only, no direct mesh egress)
```

All other traffic is denied by the `deny-all` AuthorizationPolicy.

## Current Limitation — Shared Service Account

AuthorizationPolicies identify callers by SPIFFE principal, which is bound to the pod's Kubernetes ServiceAccount. Currently all services share the `vnshop-workload` ServiceAccount. To enforce per-service identity in production:

1. Create a dedicated ServiceAccount per service (e.g. `vnshop-api-gateway`, `vnshop-order-service`).
2. Set `serviceAccountName` in each Deployment.
3. Update the `principals` in `authorization-policies.yaml` to match.

## Verifying mTLS is Working

### Check sidecar injection

```bash
kubectl get pods -n vnshop -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.containers[*].name}{"\n"}{end}'
```

Each pod should list two containers: the service container and `istio-proxy`.

### Check PeerAuthentication is STRICT

```bash
istioctl x describe pod <pod-name> -n vnshop
```

Look for `mTLS is enabled` in the output.

### Verify encrypted connections

```bash
istioctl x authz check <pod-name>.<namespace>
```

### Check that plain-text is rejected

```bash
# From outside the mesh, a direct TCP call to a service port should fail:
kubectl run test --image=curlimages/curl --restart=Never -it --rm -- \
  curl -v http://vnshop-user.vnshop.svc.cluster.local:8081/actuator/health
# Expected: connection reset or 503 — plain HTTP is rejected by the sidecar
```

### Inspect live mTLS traffic

```bash
istioctl dashboard kiali
```

Open the Graph view, select the vnshop namespace. Green padlock icons on edges indicate active mTLS.

## Adding a New Service to the Mesh

1. Add the Deployment and Service to `infra/k8s/base/workloads.yaml`. No annotation needed — namespace-level injection handles it automatically.
2. Add a DestinationRule in `destination-rules.yaml` with `mode: ISTIO_MUTUAL`.
3. Add AuthorizationPolicy entries in `authorization-policies.yaml`:
   - One policy on the new service allowing its expected callers.
   - Add the new service as an allowed caller in any upstream service policies.
4. Create a dedicated ServiceAccount for the new service if tightening principal-based auth.

## Disabling mTLS for a Single Service (Emergency)

To temporarily exempt one service (e.g. for debugging), apply a port-specific `PERMISSIVE` override:

```yaml
apiVersion: security.istio.io/v1
kind: PeerAuthentication
metadata:
  name: debug-permissive-my-service
  namespace: vnshop
spec:
  selector:
    matchLabels:
      app: my-service
  mtls:
    mode: PERMISSIVE
```

Remove the override as soon as debugging is complete. Do not commit this to the repo.
