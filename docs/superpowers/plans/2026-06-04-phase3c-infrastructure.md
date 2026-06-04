# Phase 3C: Infrastructure Hardening — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

---

## Goal

Harden the infrastructure layer for production readiness: TLS termination via Ingress, Redis high-availability with Sentinel, Kafka partition scaling for throughput, and inter-service mTLS via Istio service mesh.

## Architecture

- **Ingress**: Nginx Ingress Controller with cert-manager for automated Let's Encrypt TLS certificates
- **Redis HA**: Sentinel topology (1 master + 2 replicas + 3 sentinels) behind docker-compose `--profile ha`
- **Kafka Scaling**: Partitions scaled 4-12x with matching consumer concurrency
- **Istio mTLS**: STRICT PeerAuthentication, AuthorizationPolicies encoding the service call graph

## Tech Stack

- Kubernetes 1.30+, Kustomize
- Nginx Ingress Controller 1.10+
- cert-manager v1.15+
- Redis 8.6 Sentinel
- Apache Kafka (Confluent 8.2.0)
- Istio 1.22+
- Spring Boot 4.0.6 (sentinel-aware Redis, Kafka concurrency)
- Node 24 / ioredis (sentinel mode)

---

## File Structure

### Created Files

```
infra/k8s/base/ingress/
├── namespace.yaml
├── nginx-ingress-controller.yaml
├── cert-manager.yaml
├── cluster-issuers.yaml
├── ingress.yaml
└── kustomization.yaml

infra/k8s/overlays/prod/ingress-patch.yaml
infra/k8s/overlays/staging/ingress-patch.yaml

infra/k8s/base/redis/
├── redis-master.yaml
├── redis-replicas.yaml
├── redis-sentinel.yaml
├── redis-configmap.yaml
├── redis-services.yaml
└── kustomization.yaml

infra/k8s/base/istio/
├── istio-operator.yaml
├── peer-authentication.yaml
├── destination-rules.yaml
├── authorization-policies.yaml
├── virtual-services.yaml
├── README.md
└── kustomization.yaml

infra/scripts/validate-istio-call-graph.sh
infra/scripts/kafka-partition-reassignment.sh
```

### Modified Files

```
docker-compose.yml                          (Redis Sentinel services under profile ha)
infra/scripts/init-kafka-topics.sh          (new partition counts)
infra/k8s/base/kustomization.yaml           (include new subdirs)
infra/k8s/overlays/prod/kustomization.yaml  (include prod patches)
infra/k8s/overlays/staging/kustomization.yaml

# Java services - Redis sentinel config:
inventory-service/src/main/resources/application.yml
order-service/src/main/resources/application.yml
payment-service/src/main/resources/application.yml
product-service/src/main/resources/application.yml
user-service/src/main/resources/application.yml
recommendations-service/src/main/resources/application.yml
search-service/src/main/resources/application.yml
coupon-service/src/main/resources/application.yml
seller-finance-service/src/main/resources/application.yml

# Cart-service Node sentinel config:
cart-service/src/config/redis.config.ts

# Kafka consumer concurrency updates:
order-service/src/main/java/.../infrastructure/kafka/  (listeners)
payment-service/src/main/java/.../infrastructure/kafka/ (listeners)
inventory-service/src/main/java/.../infrastructure/kafka/ (listeners)
shipping-service/src/main/java/.../infrastructure/kafka/ (listeners)
product-service/src/main/java/.../infrastructure/kafka/ (listeners)
```

---

## Task C1: K8s Ingress + cert-manager TLS

### Step-by-step

- [ ] **C1.1** Create ingress namespace manifest

**File: `infra/k8s/base/ingress/namespace.yaml`**
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: ingress-nginx
  labels:
    app.kubernetes.io/name: ingress-nginx
    app.kubernetes.io/part-of: ingress-nginx
---
apiVersion: v1
kind: Namespace
metadata:
  name: cert-manager
  labels:
    app.kubernetes.io/name: cert-manager
```

- [ ] **C1.2** Create Nginx Ingress Controller manifests

**File: `infra/k8s/base/ingress/nginx-ingress-controller.yaml`**
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: ingress-nginx
  namespace: ingress-nginx
  labels:
    app.kubernetes.io/name: ingress-nginx
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: ingress-nginx
  labels:
    app.kubernetes.io/name: ingress-nginx
rules:
  - apiGroups: [""]
    resources: ["configmaps", "endpoints", "nodes", "pods", "secrets", "namespaces"]
    verbs: ["list", "watch"]
  - apiGroups: [""]
    resources: ["nodes"]
    verbs: ["get"]
  - apiGroups: [""]
    resources: ["services"]
    verbs: ["get", "list", "watch"]
  - apiGroups: ["networking.k8s.io"]
    resources: ["ingresses", "ingressclasses"]
    verbs: ["get", "list", "watch"]
  - apiGroups: ["networking.k8s.io"]
    resources: ["ingresses/status"]
    verbs: ["update"]
  - apiGroups: [""]
    resources: ["events"]
    verbs: ["create", "patch"]
  - apiGroups: ["discovery.k8s.io"]
    resources: ["endpointslices"]
    verbs: ["list", "watch", "get"]
  - apiGroups: ["coordination.k8s.io"]
    resources: ["leases"]
    verbs: ["get", "create", "update"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: ingress-nginx
  labels:
    app.kubernetes.io/name: ingress-nginx
subjects:
  - kind: ServiceAccount
    name: ingress-nginx
    namespace: ingress-nginx
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: ingress-nginx
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: ingress-nginx-controller
  namespace: ingress-nginx
  labels:
    app.kubernetes.io/name: ingress-nginx
data:
  use-forwarded-headers: "true"
  compute-full-forwarded-for: "true"
  use-proxy-protocol: "false"
  enable-real-ip: "true"
  proxy-body-size: "50m"
  proxy-read-timeout: "60"
  proxy-send-timeout: "60"
  ssl-protocols: "TLSv1.2 TLSv1.3"
  ssl-ciphers: "ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384"
  hsts: "true"
  hsts-max-age: "31536000"
  hsts-include-subdomains: "true"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ingress-nginx-controller
  namespace: ingress-nginx
  labels:
    app.kubernetes.io/name: ingress-nginx
    app.kubernetes.io/component: controller
spec:
  replicas: 2
  selector:
    matchLabels:
      app.kubernetes.io/name: ingress-nginx
      app.kubernetes.io/component: controller
  template:
    metadata:
      labels:
        app.kubernetes.io/name: ingress-nginx
        app.kubernetes.io/component: controller
    spec:
      serviceAccountName: ingress-nginx
      containers:
        - name: controller
          image: registry.k8s.io/ingress-nginx/controller:v1.10.1
          args:
            - /nginx-ingress-controller
            - --configmap=$(POD_NAMESPACE)/ingress-nginx-controller
            - --publish-service=$(POD_NAMESPACE)/ingress-nginx-controller
            - --election-id=ingress-nginx-leader
            - --controller-class=k8s.io/ingress-nginx
            - --ingress-class=nginx
            - --watch-ingress-without-class=true
          env:
            - name: POD_NAME
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
            - name: POD_NAMESPACE
              valueFrom:
                fieldRef:
                  fieldPath: metadata.namespace
          ports:
            - name: http
              containerPort: 80
              protocol: TCP
            - name: https
              containerPort: 443
              protocol: TCP
            - name: metrics
              containerPort: 10254
              protocol: TCP
          livenessProbe:
            httpGet:
              path: /healthz
              port: 10254
            initialDelaySeconds: 10
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /healthz
              port: 10254
            initialDelaySeconds: 10
            periodSeconds: 10
          resources:
            requests:
              cpu: 100m
              memory: 128Mi
            limits:
              cpu: 500m
              memory: 512Mi
---
apiVersion: v1
kind: Service
metadata:
  name: ingress-nginx-controller
  namespace: ingress-nginx
  labels:
    app.kubernetes.io/name: ingress-nginx
    app.kubernetes.io/component: controller
spec:
  type: LoadBalancer
  externalTrafficPolicy: Local
  ports:
    - name: http
      port: 80
      targetPort: http
      protocol: TCP
    - name: https
      port: 443
      targetPort: https
      protocol: TCP
  selector:
    app.kubernetes.io/name: ingress-nginx
    app.kubernetes.io/component: controller
---
apiVersion: networking.k8s.io/v1
kind: IngressClass
metadata:
  name: nginx
  labels:
    app.kubernetes.io/name: ingress-nginx
  annotations:
    ingressclass.kubernetes.io/is-default-class: "true"
spec:
  controller: k8s.io/ingress-nginx
```

- [ ] **C1.3** Create cert-manager CRDs and deployment reference

**File: `infra/k8s/base/ingress/cert-manager.yaml`**
```yaml
# cert-manager is installed via Helm or static manifests.
# This file documents the required installation command:
#   kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.15.1/cert-manager.yaml
#
# After cert-manager pods are running, apply the ClusterIssuers below.
#
# Verify installation:
#   kubectl get pods -n cert-manager
#   kubectl get crds | grep cert-manager
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: cert-manager-install-note
  namespace: cert-manager
  labels:
    app.kubernetes.io/name: cert-manager
    app.kubernetes.io/part-of: vnshop
data:
  install-command: "kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.15.1/cert-manager.yaml"
  version: "v1.15.1"
```

- [ ] **C1.4** Create Let's Encrypt ClusterIssuers (staging + production)

**File: `infra/k8s/base/ingress/cluster-issuers.yaml`**
```yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-staging
  labels:
    app.kubernetes.io/part-of: vnshop
spec:
  acme:
    server: https://acme-staging-v02.api.letsencrypt.org/directory
    email: admin@vnshop.dev
    privateKeySecretRef:
      name: letsencrypt-staging-account-key
    solvers:
      - http01:
          ingress:
            class: nginx
---
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
  labels:
    app.kubernetes.io/part-of: vnshop
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@vnshop.dev
    privateKeySecretRef:
      name: letsencrypt-prod-account-key
    solvers:
      - http01:
          ingress:
            class: nginx
```

- [ ] **C1.5** Create Ingress resource with routing rules, rate-limiting, and CORS

**File: `infra/k8s/base/ingress/ingress.yaml`**
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: vnshop-ingress
  namespace: default
  labels:
    app.kubernetes.io/name: vnshop-ingress
    app.kubernetes.io/part-of: vnshop
  annotations:
    # TLS - default to staging issuer (overridden in prod overlay)
    cert-manager.io/cluster-issuer: "letsencrypt-staging"
    # Rate limiting
    nginx.ingress.kubernetes.io/limit-rps: "50"
    nginx.ingress.kubernetes.io/limit-burst-multiplier: "5"
    nginx.ingress.kubernetes.io/limit-connections: "20"
    # CORS
    nginx.ingress.kubernetes.io/enable-cors: "true"
    nginx.ingress.kubernetes.io/cors-allow-origin: "https://vnshop.dev,https://www.vnshop.dev"
    nginx.ingress.kubernetes.io/cors-allow-methods: "GET, POST, PUT, DELETE, PATCH, OPTIONS"
    nginx.ingress.kubernetes.io/cors-allow-headers: "DNT,X-CustomHeader,Keep-Alive,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Authorization"
    nginx.ingress.kubernetes.io/cors-allow-credentials: "true"
    nginx.ingress.kubernetes.io/cors-max-age: "86400"
    # Security headers
    nginx.ingress.kubernetes.io/configuration-snippet: |
      more_set_headers "X-Frame-Options: DENY";
      more_set_headers "X-Content-Type-Options: nosniff";
      more_set_headers "X-XSS-Protection: 1; mode=block";
      more_set_headers "Referrer-Policy: strict-origin-when-cross-origin";
    # Proxy settings
    nginx.ingress.kubernetes.io/proxy-body-size: "50m"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "60"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "60"
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - vnshop.dev
        - www.vnshop.dev
      secretName: vnshop-tls-cert
  rules:
    - host: vnshop.dev
      http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: api-gateway
                port:
                  number: 8080
          - path: /auth
            pathType: Prefix
            backend:
              service:
                name: keycloak
                port:
                  number: 8085
          - path: /grafana
            pathType: Prefix
            backend:
              service:
                name: grafana
                port:
                  number: 3000
    - host: www.vnshop.dev
      http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: api-gateway
                port:
                  number: 8080
          - path: /auth
            pathType: Prefix
            backend:
              service:
                name: keycloak
                port:
                  number: 8085
          - path: /grafana
            pathType: Prefix
            backend:
              service:
                name: grafana
                port:
                  number: 3000
```

- [ ] **C1.6** Create ingress kustomization

**File: `infra/k8s/base/ingress/kustomization.yaml`**
```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - namespace.yaml
  - nginx-ingress-controller.yaml
  - cert-manager.yaml
  - cluster-issuers.yaml
  - ingress.yaml
```

- [ ] **C1.7** Create prod overlay ingress patch (switches to prod issuer)

**File: `infra/k8s/overlays/prod/ingress-patch.yaml`**
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: vnshop-ingress
  namespace: default
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
    nginx.ingress.kubernetes.io/limit-rps: "100"
    nginx.ingress.kubernetes.io/limit-burst-multiplier: "10"
    nginx.ingress.kubernetes.io/limit-connections: "50"
```

- [ ] **C1.8** Create staging overlay ingress patch

**File: `infra/k8s/overlays/staging/ingress-patch.yaml`**
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: vnshop-ingress
  namespace: default
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-staging"
    nginx.ingress.kubernetes.io/limit-rps: "30"
    nginx.ingress.kubernetes.io/limit-burst-multiplier: "3"
    nginx.ingress.kubernetes.io/limit-connections: "10"
```

- [ ] **C1.9** Update base kustomization to include ingress

Append to `infra/k8s/base/kustomization.yaml`:
```yaml
resources:
  # ... existing resources ...
  - ingress/
```

- [ ] **C1.10** Update prod overlay kustomization

Append to `infra/k8s/overlays/prod/kustomization.yaml`:
```yaml
patches:
  # ... existing patches ...
  - path: ingress-patch.yaml
    target:
      kind: Ingress
      name: vnshop-ingress
```

- [ ] **C1.11** Update staging overlay kustomization

Append to `infra/k8s/overlays/staging/kustomization.yaml`:
```yaml
patches:
  # ... existing patches ...
  - path: ingress-patch.yaml
    target:
      kind: Ingress
      name: vnshop-ingress
```

- [ ] **C1.12** Verify Kustomize builds cleanly

```powershell
kubectl kustomize infra/k8s/base/
kubectl kustomize infra/k8s/overlays/prod/
kubectl kustomize infra/k8s/overlays/staging/
```

- [ ] **C1.13** Commit

```
feat(k8s): add Nginx Ingress Controller + cert-manager TLS

- Nginx Ingress Controller deployment with 2 replicas
- cert-manager integration with Let's Encrypt staging + prod ClusterIssuers
- Ingress resource routing /api, /auth, /grafana
- Rate limiting, CORS, security headers via annotations
- Prod overlay switches to letsencrypt-prod issuer with higher limits
- Staging overlay uses letsencrypt-staging with conservative limits
```

---

## Task C2: Redis Sentinel HA

### Step-by-step

- [ ] **C2.1** Add Redis Sentinel services to docker-compose.yml under `--profile ha`

Append to `docker-compose.yml` services section:
```yaml
  redis-master:
    image: redis:8.6-alpine
    container_name: vnshop-redis-master
    profiles: ["ha"]
    command: >
      redis-server
      --requirepass vnshop123
      --masterauth vnshop123
      --appendonly yes
      --appendfsync everysec
      --maxmemory 512mb
      --maxmemory-policy allkeys-lru
    ports:
      - "6380:6379"
    volumes:
      - redis-master-data:/data
    networks:
      - vnshop-network
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "vnshop123", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

  redis-replica-1:
    image: redis:8.6-alpine
    container_name: vnshop-redis-replica-1
    profiles: ["ha"]
    command: >
      redis-server
      --requirepass vnshop123
      --masterauth vnshop123
      --replicaof redis-master 6379
      --appendonly yes
      --appendfsync everysec
      --maxmemory 512mb
      --maxmemory-policy allkeys-lru
    depends_on:
      redis-master:
        condition: service_healthy
    networks:
      - vnshop-network
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "vnshop123", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

  redis-replica-2:
    image: redis:8.6-alpine
    container_name: vnshop-redis-replica-2
    profiles: ["ha"]
    command: >
      redis-server
      --requirepass vnshop123
      --masterauth vnshop123
      --replicaof redis-master 6379
      --appendonly yes
      --appendfsync everysec
      --maxmemory 512mb
      --maxmemory-policy allkeys-lru
    depends_on:
      redis-master:
        condition: service_healthy
    networks:
      - vnshop-network
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "vnshop123", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

  redis-sentinel-1:
    image: redis:8.6-alpine
    container_name: vnshop-redis-sentinel-1
    profiles: ["ha"]
    command: >
      redis-sentinel /etc/redis/sentinel.conf
    volumes:
      - ./infra/redis/sentinel.conf:/etc/redis/sentinel.conf:ro
    depends_on:
      redis-master:
        condition: service_healthy
    ports:
      - "26379:26379"
    networks:
      - vnshop-network
    healthcheck:
      test: ["CMD", "redis-cli", "-p", "26379", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

  redis-sentinel-2:
    image: redis:8.6-alpine
    container_name: vnshop-redis-sentinel-2
    profiles: ["ha"]
    command: >
      redis-sentinel /etc/redis/sentinel.conf
    volumes:
      - ./infra/redis/sentinel.conf:/etc/redis/sentinel.conf:ro
    depends_on:
      redis-master:
        condition: service_healthy
    ports:
      - "26380:26379"
    networks:
      - vnshop-network
    healthcheck:
      test: ["CMD", "redis-cli", "-p", "26379", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

  redis-sentinel-3:
    image: redis:8.6-alpine
    container_name: vnshop-redis-sentinel-3
    profiles: ["ha"]
    command: >
      redis-sentinel /etc/redis/sentinel.conf
    volumes:
      - ./infra/redis/sentinel.conf:/etc/redis/sentinel.conf:ro
    depends_on:
      redis-master:
        condition: service_healthy
    ports:
      - "26381:26379"
    networks:
      - vnshop-network
    healthcheck:
      test: ["CMD", "redis-cli", "-p", "26379", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5
```

Add volumes:
```yaml
volumes:
  # ... existing volumes ...
  redis-master-data:
```

- [ ] **C2.2** Create Sentinel configuration file

**File: `infra/redis/sentinel.conf`**
```conf
port 26379

sentinel monitor mymaster redis-master 6379 2
sentinel auth-pass mymaster vnshop123
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 10000
sentinel parallel-syncs mymaster 1

sentinel resolve-hostnames yes
sentinel announce-hostnames yes
```

- [ ] **C2.3** Add Spring Boot Redis Sentinel configuration to all Java services

For each Java service that uses Redis, add the following block to `src/main/resources/application.yml`. The sentinel config is activated only under the `ha` or `prod` Spring profile.

**Template (apply to all 9 Java services):**
```yaml
---
spring:
  config:
    activate:
      on-profile: ha,prod
  data:
    redis:
      sentinel:
        master: mymaster
        nodes:
          - redis-sentinel-1:26379
          - redis-sentinel-2:26379
          - redis-sentinel-3:26379
        password: vnshop123
      password: vnshop123
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4
          max-wait: 2000ms
        sentinel:
          refresh:
            period: 10s
```

Services to update:
1. `inventory-service/src/main/resources/application.yml`
2. `order-service/src/main/resources/application.yml`
3. `payment-service/src/main/resources/application.yml`
4. `product-service/src/main/resources/application.yml`
5. `user-service/src/main/resources/application.yml`
6. `recommendations-service/src/main/resources/application.yml`
7. `search-service/src/main/resources/application.yml`
8. `coupon-service/src/main/resources/application.yml`
9. `seller-finance-service/src/main/resources/application.yml`

- [ ] **C2.4** Update cart-service (Node/ioredis) to support Sentinel mode

**File: `cart-service/src/config/redis.config.ts`**
```typescript
import { Redis } from 'ioredis';

interface RedisConfig {
  host: string;
  port: number;
  password: string;
}

interface SentinelConfig {
  sentinels: Array<{ host: string; port: number }>;
  name: string;
  password: string;
  sentinelPassword: string;
  db: number;
  lazyConnect: boolean;
  maxRetriesPerRequest: number;
  retryStrategy: (times: number) => number | null;
}

function parseSentinelNodes(): Array<{ host: string; port: number }> {
  const nodesEnv = process.env.REDIS_SENTINEL_NODES || '';
  if (!nodesEnv) return [];

  return nodesEnv.split(',').map((node) => {
    const [host, portStr] = node.trim().split(':');
    return { host, port: parseInt(portStr, 10) || 26379 };
  });
}

function isSentinelMode(): boolean {
  return (
    process.env.REDIS_MODE === 'sentinel' ||
    process.env.NODE_ENV === 'production' ||
    process.env.REDIS_SENTINEL_NODES !== undefined
  );
}

export function createRedisClient(): Redis {
  if (isSentinelMode()) {
    const sentinelNodes = parseSentinelNodes();
    if (sentinelNodes.length === 0) {
      throw new Error(
        'REDIS_SENTINEL_NODES env var required in sentinel mode (format: host1:port1,host2:port2,host3:port3)',
      );
    }

    const sentinelConfig: SentinelConfig = {
      sentinels: sentinelNodes,
      name: process.env.REDIS_SENTINEL_MASTER || 'mymaster',
      password: process.env.REDIS_PASSWORD || 'vnshop123',
      sentinelPassword: process.env.REDIS_PASSWORD || 'vnshop123',
      db: 0,
      lazyConnect: true,
      maxRetriesPerRequest: 3,
      retryStrategy: (times: number) => {
        if (times > 10) return null;
        return Math.min(times * 200, 5000);
      },
    };

    return new Redis(sentinelConfig);
  }

  // Default standalone mode for local dev
  const standaloneConfig: RedisConfig = {
    host: process.env.REDIS_HOST || 'localhost',
    port: parseInt(process.env.REDIS_PORT || '6379', 10),
    password: process.env.REDIS_PASSWORD || 'vnshop123',
  };

  return new Redis(standaloneConfig);
}

export function createRedisSubscriber(): Redis {
  if (isSentinelMode()) {
    const sentinelNodes = parseSentinelNodes();
    return new Redis({
      sentinels: sentinelNodes,
      name: process.env.REDIS_SENTINEL_MASTER || 'mymaster',
      password: process.env.REDIS_PASSWORD || 'vnshop123',
      sentinelPassword: process.env.REDIS_PASSWORD || 'vnshop123',
      db: 0,
      lazyConnect: true,
      maxRetriesPerRequest: null,
      retryStrategy: (times: number) => {
        if (times > 10) return null;
        return Math.min(times * 200, 5000);
      },
    });
  }

  return new Redis({
    host: process.env.REDIS_HOST || 'localhost',
    port: parseInt(process.env.REDIS_PORT || '6379', 10),
    password: process.env.REDIS_PASSWORD || 'vnshop123',
  });
}
```

- [ ] **C2.5** Add docker-compose environment variables for cart-service sentinel mode

In `docker-compose.yml`, update the cart-service environment section to include sentinel vars (only relevant when running with `--profile ha`):
```yaml
  cart-service:
    # ... existing config ...
    environment:
      # ... existing vars ...
      REDIS_MODE: ${REDIS_MODE:-standalone}
      REDIS_SENTINEL_NODES: ${REDIS_SENTINEL_NODES:-}
      REDIS_SENTINEL_MASTER: ${REDIS_SENTINEL_MASTER:-mymaster}
```

- [ ] **C2.6** Create K8s Redis StatefulSet and Sentinel Deployment

**File: `infra/k8s/base/redis/redis-configmap.yaml`**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: redis-config
  namespace: default
  labels:
    app.kubernetes.io/name: redis
    app.kubernetes.io/part-of: vnshop
data:
  redis.conf: |
    appendonly yes
    appendfsync everysec
    maxmemory 1gb
    maxmemory-policy allkeys-lru
    tcp-keepalive 60
    timeout 300
    hz 10
    
  sentinel.conf: |
    port 26379
    sentinel monitor mymaster redis-master-0.redis-master-headless.default.svc.cluster.local 6379 2
    sentinel down-after-milliseconds mymaster 5000
    sentinel failover-timeout mymaster 10000
    sentinel parallel-syncs mymaster 1
    sentinel resolve-hostnames yes
    sentinel announce-hostnames yes
```

**File: `infra/k8s/base/redis/redis-master.yaml`**
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: redis-master
  namespace: default
  labels:
    app.kubernetes.io/name: redis
    app.kubernetes.io/component: master
    app.kubernetes.io/part-of: vnshop
spec:
  serviceName: redis-master-headless
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: redis
      app.kubernetes.io/component: master
  template:
    metadata:
      labels:
        app.kubernetes.io/name: redis
        app.kubernetes.io/component: master
    spec:
      containers:
        - name: redis
          image: redis:8.6-alpine
          command:
            - redis-server
            - /etc/redis/redis.conf
            - --requirepass
            - $(REDIS_PASSWORD)
            - --masterauth
            - $(REDIS_PASSWORD)
          env:
            - name: REDIS_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: redis-secret
                  key: password
          ports:
            - containerPort: 6379
              name: redis
          volumeMounts:
            - name: redis-config
              mountPath: /etc/redis
            - name: redis-data
              mountPath: /data
          resources:
            requests:
              cpu: 100m
              memory: 256Mi
            limits:
              cpu: 500m
              memory: 1Gi
          livenessProbe:
            exec:
              command:
                - redis-cli
                - -a
                - $(REDIS_PASSWORD)
                - ping
            initialDelaySeconds: 10
            periodSeconds: 10
          readinessProbe:
            exec:
              command:
                - redis-cli
                - -a
                - $(REDIS_PASSWORD)
                - ping
            initialDelaySeconds: 5
            periodSeconds: 5
      volumes:
        - name: redis-config
          configMap:
            name: redis-config
            items:
              - key: redis.conf
                path: redis.conf
  volumeClaimTemplates:
    - metadata:
        name: redis-data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 5Gi
```

**File: `infra/k8s/base/redis/redis-replicas.yaml`**
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: redis-replicas
  namespace: default
  labels:
    app.kubernetes.io/name: redis
    app.kubernetes.io/component: replica
    app.kubernetes.io/part-of: vnshop
spec:
  serviceName: redis-replicas-headless
  replicas: 2
  selector:
    matchLabels:
      app.kubernetes.io/name: redis
      app.kubernetes.io/component: replica
  template:
    metadata:
      labels:
        app.kubernetes.io/name: redis
        app.kubernetes.io/component: replica
    spec:
      containers:
        - name: redis
          image: redis:8.6-alpine
          command:
            - redis-server
            - /etc/redis/redis.conf
            - --requirepass
            - $(REDIS_PASSWORD)
            - --masterauth
            - $(REDIS_PASSWORD)
            - --replicaof
            - redis-master-0.redis-master-headless.default.svc.cluster.local
            - "6379"
          env:
            - name: REDIS_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: redis-secret
                  key: password
          ports:
            - containerPort: 6379
              name: redis
          volumeMounts:
            - name: redis-config
              mountPath: /etc/redis
            - name: redis-data
              mountPath: /data
          resources:
            requests:
              cpu: 100m
              memory: 256Mi
            limits:
              cpu: 500m
              memory: 1Gi
          livenessProbe:
            exec:
              command:
                - redis-cli
                - -a
                - $(REDIS_PASSWORD)
                - ping
            initialDelaySeconds: 10
            periodSeconds: 10
          readinessProbe:
            exec:
              command:
                - redis-cli
                - -a
                - $(REDIS_PASSWORD)
                - ping
            initialDelaySeconds: 5
            periodSeconds: 5
      volumes:
        - name: redis-config
          configMap:
            name: redis-config
            items:
              - key: redis.conf
                path: redis.conf
  volumeClaimTemplates:
    - metadata:
        name: redis-data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 5Gi
```

**File: `infra/k8s/base/redis/redis-sentinel.yaml`**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis-sentinel
  namespace: default
  labels:
    app.kubernetes.io/name: redis
    app.kubernetes.io/component: sentinel
    app.kubernetes.io/part-of: vnshop
spec:
  replicas: 3
  selector:
    matchLabels:
      app.kubernetes.io/name: redis
      app.kubernetes.io/component: sentinel
  template:
    metadata:
      labels:
        app.kubernetes.io/name: redis
        app.kubernetes.io/component: sentinel
    spec:
      containers:
        - name: sentinel
          image: redis:8.6-alpine
          command:
            - sh
            - -c
            - |
              cp /etc/redis/sentinel.conf /tmp/sentinel.conf
              echo "sentinel auth-pass mymaster $REDIS_PASSWORD" >> /tmp/sentinel.conf
              redis-sentinel /tmp/sentinel.conf
          env:
            - name: REDIS_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: redis-secret
                  key: password
          ports:
            - containerPort: 26379
              name: sentinel
          volumeMounts:
            - name: redis-config
              mountPath: /etc/redis
          resources:
            requests:
              cpu: 50m
              memory: 64Mi
            limits:
              cpu: 200m
              memory: 128Mi
          livenessProbe:
            exec:
              command:
                - redis-cli
                - -p
                - "26379"
                - ping
            initialDelaySeconds: 10
            periodSeconds: 10
          readinessProbe:
            exec:
              command:
                - redis-cli
                - -p
                - "26379"
                - ping
            initialDelaySeconds: 5
            periodSeconds: 5
      volumes:
        - name: redis-config
          configMap:
            name: redis-config
            items:
              - key: sentinel.conf
                path: sentinel.conf
```

**File: `infra/k8s/base/redis/redis-services.yaml`**
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: redis-secret
  namespace: default
  labels:
    app.kubernetes.io/name: redis
    app.kubernetes.io/part-of: vnshop
type: Opaque
stringData:
  password: vnshop123
---
apiVersion: v1
kind: Service
metadata:
  name: redis-master-headless
  namespace: default
  labels:
    app.kubernetes.io/name: redis
    app.kubernetes.io/component: master
spec:
  clusterIP: None
  ports:
    - port: 6379
      targetPort: redis
      name: redis
  selector:
    app.kubernetes.io/name: redis
    app.kubernetes.io/component: master
---
apiVersion: v1
kind: Service
metadata:
  name: redis-replicas-headless
  namespace: default
  labels:
    app.kubernetes.io/name: redis
    app.kubernetes.io/component: replica
spec:
  clusterIP: None
  ports:
    - port: 6379
      targetPort: redis
      name: redis
  selector:
    app.kubernetes.io/name: redis
    app.kubernetes.io/component: replica
---
apiVersion: v1
kind: Service
metadata:
  name: redis-sentinel
  namespace: default
  labels:
    app.kubernetes.io/name: redis
    app.kubernetes.io/component: sentinel
spec:
  ports:
    - port: 26379
      targetPort: sentinel
      name: sentinel
  selector:
    app.kubernetes.io/name: redis
    app.kubernetes.io/component: sentinel
```

**File: `infra/k8s/base/redis/kustomization.yaml`**
```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - redis-configmap.yaml
  - redis-services.yaml
  - redis-master.yaml
  - redis-replicas.yaml
  - redis-sentinel.yaml
```

- [ ] **C2.7** Update base kustomization to include redis

Append to `infra/k8s/base/kustomization.yaml`:
```yaml
resources:
  # ... existing resources ...
  - redis/
```

- [ ] **C2.8** Verify docker-compose HA profile starts correctly

```powershell
docker compose --profile ha up redis-master redis-replica-1 redis-replica-2 redis-sentinel-1 redis-sentinel-2 redis-sentinel-3 -d
docker exec vnshop-redis-sentinel-1 redis-cli -p 26379 sentinel master mymaster
docker compose --profile ha down
```

- [ ] **C2.9** Verify Kustomize builds cleanly

```powershell
kubectl kustomize infra/k8s/base/
```

- [ ] **C2.10** Commit

```
feat(redis): add Sentinel HA topology with docker-compose profile

- 1 master + 2 replicas + 3 sentinels behind --profile ha
- Spring Boot sentinel-aware config under ha/prod profiles
- Cart-service (Node/ioredis) sentinel support with REDIS_MODE env
- K8s StatefulSet for master/replicas, Deployment for sentinels
- Sentinel quorum=2, down-after=5000ms, failover-timeout=10000ms
- Default dev mode unchanged (single redis instance)
```

---

## Task C3: Kafka Partition Scaling

### Step-by-step

- [ ] **C3.1** Update init-kafka-topics.sh with new partition counts

**File: `infra/scripts/init-kafka-topics.sh`** (full replacement)
```bash
#!/bin/bash
set -euo pipefail

# Kafka Partition Configuration
# Last updated: Phase 3C - Infrastructure Hardening
# Partition strategy:
#   - High-throughput topics (product-events): 12 partitions
#   - Order/payment/inventory/shipping topics: 6 partitions
#   - Low-volume topics (messaging, notifications): 3 partitions
#
# Replication factor: 3 for production, 1 for dev (single broker)

KAFKA_BROKER="${KAFKA_BROKER:-kafka:9092}"
REPLICATION_FACTOR="${KAFKA_REPLICATION_FACTOR:-1}"
COMMAND_CONFIG="${KAFKA_COMMAND_CONFIG:-}"

CMD_CONFIG_FLAG=""
if [ -n "$COMMAND_CONFIG" ]; then
  CMD_CONFIG_FLAG="--command-config $COMMAND_CONFIG"
fi

echo "=== VNShop Kafka Topic Initialization ==="
echo "Broker: $KAFKA_BROKER"
echo "Replication Factor: $REPLICATION_FACTOR"
echo ""

create_topic() {
  local topic=$1
  local partitions=$2
  local config="${3:-}"

  echo "Creating topic: $topic (partitions=$partitions, rf=$REPLICATION_FACTOR)"

  CONFIG_FLAG=""
  if [ -n "$config" ]; then
    CONFIG_FLAG="--config $config"
  fi

  kafka-topics --bootstrap-server "$KAFKA_BROKER" $CMD_CONFIG_FLAG \
    --create \
    --if-not-exists \
    --topic "$topic" \
    --partitions "$partitions" \
    --replication-factor "$REPLICATION_FACTOR" \
    $CONFIG_FLAG \
    2>/dev/null || echo "  (topic may already exist)"
}

# ============================================================
# Product Events - High throughput (12 partitions)
# Consumer: product-service, search-service, recommendations-service
# ============================================================
create_topic "product-events" 12 "retention.ms=604800000"

# ============================================================
# Order Events (6 partitions each)
# Consumers: order-service-projection, order-service-payment, order-service-refund
# ============================================================
create_topic "order.created" 6 "retention.ms=2592000000"
create_topic "order.confirmed" 6 "retention.ms=2592000000"
create_topic "order.cancelled" 6 "retention.ms=2592000000"
create_topic "order.completed" 6 "retention.ms=2592000000"
create_topic "order.refund-requested" 6 "retention.ms=2592000000"

# ============================================================
# Payment Events (6 partitions each)
# Consumers: payment-service, order-service (callback)
# ============================================================
create_topic "payment.initiated" 6 "retention.ms=2592000000"
create_topic "payment.completed" 6 "retention.ms=2592000000"
create_topic "payment.failed" 6 "retention.ms=2592000000"
create_topic "payment.refunded" 6 "retention.ms=2592000000"
create_topic "payment.callback" 6 "retention.ms=2592000000"

# ============================================================
# Inventory Events (6 partitions each)
# Consumers: inventory-service, order-service
# ============================================================
create_topic "inventory.reserved" 6 "retention.ms=604800000"
create_topic "inventory.released" 6 "retention.ms=604800000"
create_topic "inventory.updated" 6 "retention.ms=604800000"

# ============================================================
# Shipping Events (6 partitions each)
# Consumers: shipping-service, order-service
# ============================================================
create_topic "shipping.created" 6 "retention.ms=2592000000"
create_topic "shipping.updated" 6 "retention.ms=2592000000"
create_topic "shipping.cancelled" 6 "retention.ms=2592000000"
create_topic "shipping.delivered" 6 "retention.ms=2592000000"

# ============================================================
# Messaging / Notification Events (3 partitions - lower volume)
# Consumers: notification-service, messaging-service
# ============================================================
create_topic "messaging.message.sent" 3 "retention.ms=604800000"
create_topic "notification.email" 3 "retention.ms=604800000"
create_topic "notification.sms" 3 "retention.ms=604800000"
create_topic "notification.push" 3 "retention.ms=604800000"

# ============================================================
# Dead Letter Topics (3 partitions, long retention)
# ============================================================
create_topic "order.created.DLT" 3 "retention.ms=7776000000"
create_topic "payment.completed.DLT" 3 "retention.ms=7776000000"
create_topic "inventory.reserved.DLT" 3 "retention.ms=7776000000"

echo ""
echo "=== Topic initialization complete ==="
kafka-topics --bootstrap-server "$KAFKA_BROKER" $CMD_CONFIG_FLAG --list
```

- [ ] **C3.2** Create partition reassignment script for live clusters

**File: `infra/scripts/kafka-partition-reassignment.sh`**
```bash
#!/bin/bash
set -euo pipefail

# Kafka Partition Reassignment Script
# Use this script to increase partitions on an existing Kafka cluster
# WARNING: Increasing partitions may break ordering guarantees for existing keys
#
# Usage: ./kafka-partition-reassignment.sh [--dry-run]

KAFKA_BROKER="${KAFKA_BROKER:-kafka:9092}"
COMMAND_CONFIG="${KAFKA_COMMAND_CONFIG:-}"
DRY_RUN="${1:-}"

CMD_CONFIG_FLAG=""
if [ -n "$COMMAND_CONFIG" ]; then
  CMD_CONFIG_FLAG="--command-config $COMMAND_CONFIG"
fi

echo "=== VNShop Kafka Partition Reassignment ==="
echo "Broker: $KAFKA_BROKER"
if [ "$DRY_RUN" = "--dry-run" ]; then
  echo "MODE: DRY RUN (no changes will be made)"
fi
echo ""

alter_partitions() {
  local topic=$1
  local target_partitions=$2

  # Get current partition count
  local current
  current=$(kafka-topics --bootstrap-server "$KAFKA_BROKER" $CMD_CONFIG_FLAG \
    --describe --topic "$topic" 2>/dev/null | grep "PartitionCount" | awk '{print $2}' | cut -d: -f2 || echo "0")

  if [ "$current" = "0" ]; then
    echo "  SKIP: Topic '$topic' does not exist"
    return
  fi

  if [ "$current" -ge "$target_partitions" ]; then
    echo "  OK: $topic already has $current partitions (target: $target_partitions)"
    return
  fi

  echo "  SCALE: $topic from $current -> $target_partitions partitions"

  if [ "$DRY_RUN" != "--dry-run" ]; then
    kafka-topics --bootstrap-server "$KAFKA_BROKER" $CMD_CONFIG_FLAG \
      --alter --topic "$topic" --partitions "$target_partitions"
    echo "    Done."
  fi
}

echo "--- Product Events (target: 12) ---"
alter_partitions "product-events" 12

echo ""
echo "--- Order Events (target: 6) ---"
alter_partitions "order.created" 6
alter_partitions "order.confirmed" 6
alter_partitions "order.cancelled" 6
alter_partitions "order.completed" 6
alter_partitions "order.refund-requested" 6

echo ""
echo "--- Payment Events (target: 6) ---"
alter_partitions "payment.initiated" 6
alter_partitions "payment.completed" 6
alter_partitions "payment.failed" 6
alter_partitions "payment.refunded" 6
alter_partitions "payment.callback" 6

echo ""
echo "--- Inventory Events (target: 6) ---"
alter_partitions "inventory.reserved" 6
alter_partitions "inventory.released" 6
alter_partitions "inventory.updated" 6

echo ""
echo "--- Shipping Events (target: 6) ---"
alter_partitions "shipping.created" 6
alter_partitions "shipping.updated" 6
alter_partitions "shipping.cancelled" 6
alter_partitions "shipping.delivered" 6

echo ""
echo "--- Messaging Events (target: 3) ---"
alter_partitions "messaging.message.sent" 3

echo ""
echo "=== Reassignment complete ==="
echo ""
echo "Current topic state:"
kafka-topics --bootstrap-server "$KAFKA_BROKER" $CMD_CONFIG_FLAG --describe | grep "PartitionCount"
```

- [ ] **C3.3** Update order-service Kafka consumer concurrency

Find all `@KafkaListener` annotations in order-service and update concurrency to match partition count (6).

In order-service listener classes, update annotations:

```java
// OrderProjectionListener - update concurrency from 3 to 6
@KafkaListener(
    topics = "${kafka.topics.order-created:order.created}",
    groupId = "order-service-projection",
    concurrency = "6"
)

// OrderPaymentListener - add concurrency = 6
@KafkaListener(
    topics = "${kafka.topics.payment-completed:payment.completed}",
    groupId = "order-service-payment",
    concurrency = "6"
)

// OrderRefundListener - add concurrency = 6
@KafkaListener(
    topics = "${kafka.topics.payment-refunded:payment.refunded}",
    groupId = "order-service-refund",
    concurrency = "6"
)

// OrderShippingListener (if exists) - add concurrency = 6
@KafkaListener(
    topics = "${kafka.topics.shipping-delivered:shipping.delivered}",
    groupId = "order-service-shipping",
    concurrency = "6"
)

// OrderInventoryListener (if exists) - add concurrency = 6
@KafkaListener(
    topics = "${kafka.topics.inventory-reserved:inventory.reserved}",
    groupId = "order-service-inventory",
    concurrency = "6"
)
```

- [ ] **C3.4** Update payment-service Kafka consumer concurrency

```java
// PaymentCallbackListener - add concurrency = 6
@KafkaListener(
    topics = "${kafka.topics.payment-callback:payment.callback}",
    groupId = "payment-service-callback",
    concurrency = "6"
)

// PaymentOrderListener (listens to order events) - add concurrency = 6
@KafkaListener(
    topics = "${kafka.topics.order-created:order.created}",
    groupId = "payment-service-order",
    concurrency = "6"
)
```

- [ ] **C3.5** Update inventory-service Kafka consumer concurrency

```java
// InventoryReservationListener - add concurrency = 6
@KafkaListener(
    topics = "${kafka.topics.order-created:order.created}",
    groupId = "inventory-service-reservation",
    concurrency = "6"
)

// InventoryReleaseListener - add concurrency = 6
@KafkaListener(
    topics = "${kafka.topics.order-cancelled:order.cancelled}",
    groupId = "inventory-service-release",
    concurrency = "6"
)
```

- [ ] **C3.6** Update shipping-service Kafka consumer concurrency

```java
// ShippingOrderListener - add concurrency = 6
@KafkaListener(
    topics = "${kafka.topics.order-confirmed:order.confirmed}",
    groupId = "shipping-service-order",
    concurrency = "6"
)

// ShippingCancellationListener - add concurrency = 6
@KafkaListener(
    topics = "${kafka.topics.order-cancelled:order.cancelled}",
    groupId = "shipping-service-cancellation",
    concurrency = "6"
)
```

- [ ] **C3.7** Update product-service Kafka consumer concurrency

```java
// ProductEventListener (if consuming own events for projections) - add concurrency = 12
@KafkaListener(
    topics = "${kafka.topics.product-events:product-events}",
    groupId = "product-service-projection",
    concurrency = "12"
)
```

- [ ] **C3.8** Document HPA targets for K8s scaling

**File: `infra/k8s/base/kafka-hpa.yaml`**
```yaml
# Kafka Consumer HPA Configuration
# Scale pods based on consumer lag metric: kafka_consumer_fetch_manager_records_lag_max
#
# Prerequisites:
#   - Prometheus Adapter or KEDA installed
#   - Kafka consumer lag metrics exposed via actuator/prometheus
#
# Scaling strategy:
#   - Target consumer lag: 1000 records (scale up when lag exceeds this)
#   - Max replicas: equal to partition count (no benefit beyond that)
#   - Scale-down stabilization: 5 minutes (prevent flapping)
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service-hpa
  namespace: default
  labels:
    app.kubernetes.io/name: order-service
    app.kubernetes.io/part-of: vnshop
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 2
  maxReplicas: 6
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Pods
          value: 1
          periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
        - type: Pods
          value: 2
          periodSeconds: 60
  metrics:
    - type: Pods
      pods:
        metric:
          name: kafka_consumer_fetch_manager_records_lag_max
        target:
          type: AverageValue
          averageValue: "1000"
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: payment-service-hpa
  namespace: default
  labels:
    app.kubernetes.io/name: payment-service
    app.kubernetes.io/part-of: vnshop
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: payment-service
  minReplicas: 2
  maxReplicas: 6
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Pods
          value: 1
          periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
        - type: Pods
          value: 2
          periodSeconds: 60
  metrics:
    - type: Pods
      pods:
        metric:
          name: kafka_consumer_fetch_manager_records_lag_max
        target:
          type: AverageValue
          averageValue: "1000"
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: inventory-service-hpa
  namespace: default
  labels:
    app.kubernetes.io/name: inventory-service
    app.kubernetes.io/part-of: vnshop
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: inventory-service
  minReplicas: 2
  maxReplicas: 6
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Pods
          value: 1
          periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
        - type: Pods
          value: 2
          periodSeconds: 60
  metrics:
    - type: Pods
      pods:
        metric:
          name: kafka_consumer_fetch_manager_records_lag_max
        target:
          type: AverageValue
          averageValue: "1000"
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: shipping-service-hpa
  namespace: default
  labels:
    app.kubernetes.io/name: shipping-service
    app.kubernetes.io/part-of: vnshop
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: shipping-service
  minReplicas: 2
  maxReplicas: 6
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Pods
          value: 1
          periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
        - type: Pods
          value: 2
          periodSeconds: 60
  metrics:
    - type: Pods
      pods:
        metric:
          name: kafka_consumer_fetch_manager_records_lag_max
        target:
          type: AverageValue
          averageValue: "1000"
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: product-service-hpa
  namespace: default
  labels:
    app.kubernetes.io/name: product-service
    app.kubernetes.io/part-of: vnshop
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: product-service
  minReplicas: 2
  maxReplicas: 12
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Pods
          value: 1
          periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
        - type: Pods
          value: 3
          periodSeconds: 60
  metrics:
    - type: Pods
      pods:
        metric:
          name: kafka_consumer_fetch_manager_records_lag_max
        target:
          type: AverageValue
          averageValue: "1000"
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

- [ ] **C3.9** Update base kustomization to include HPA

Append to `infra/k8s/base/kustomization.yaml`:
```yaml
resources:
  # ... existing resources ...
  - kafka-hpa.yaml
```

- [ ] **C3.10** Verify scripts have correct permissions and syntax

```powershell
# Validate bash syntax (if bash available)
bash -n infra/scripts/init-kafka-topics.sh
bash -n infra/scripts/kafka-partition-reassignment.sh
```

- [ ] **C3.11** Commit

```
feat(kafka): scale partitions and consumer concurrency for throughput

- product-events: 1 -> 12 partitions
- order.*: 3 -> 6 partitions
- payment.*: 1 -> 6 partitions
- inventory.*: 1 -> 6 partitions
- shipping.*: 1 -> 6 partitions
- Updated all @KafkaListener concurrency annotations to match
- Live partition reassignment script for existing clusters
- HPA configs targeting kafka_consumer_fetch_manager_records_lag_max
- Max replicas capped at partition count per service
```

---

## Task C4: Inter-service mTLS (Istio)

### Step-by-step

- [ ] **C4.1** Create IstioOperator manifest

**File: `infra/k8s/base/istio/istio-operator.yaml`**
```yaml
# IstioOperator configuration for VNShop service mesh
# Install with: istioctl install -f istio-operator.yaml
#
# Prerequisites:
#   - istioctl v1.22+ installed
#   - Kubernetes 1.30+
#   - At least 2 CPU and 2GB memory available for istiod
#
apiVersion: install.istio.io/v1alpha1
kind: IstioOperator
metadata:
  name: vnshop-istio
  namespace: istio-system
spec:
  profile: default
  meshConfig:
    # Enable strict mTLS mesh-wide
    defaultConfig:
      holdApplicationUntilProxyStarts: true
      proxyMetadata:
        ISTIO_META_DNS_CAPTURE: "true"
        ISTIO_META_DNS_AUTO_ALLOCATE: "true"
    # Access logging for debugging
    accessLogFile: /dev/stdout
    accessLogFormat: |
      [%START_TIME%] "%REQ(:METHOD)% %REQ(X-ENVOY-ORIGINAL-PATH?:PATH)% %PROTOCOL%"
      %RESPONSE_CODE% %RESPONSE_FLAGS% %BYTES_RECEIVED% %BYTES_SENT%
      %DURATION% %RESP(X-ENVOY-UPSTREAM-SERVICE-TIME)%
      "%REQ(X-FORWARDED-FOR)%" "%REQ(USER-AGENT)%"
      "%REQ(X-REQUEST-ID)%" "%REQ(:AUTHORITY)%" "%UPSTREAM_HOST%"
    # Enable tracing (connect to existing Jaeger)
    enableTracing: true
    defaultConfig:
      tracing:
        zipkin:
          address: jaeger-collector.default.svc.cluster.local:9411
        sampling: 10.0
    # Outbound traffic policy - REGISTRY_ONLY for security
    outboundTrafficPolicy:
      mode: REGISTRY_ONLY
  components:
    pilot:
      k8s:
        resources:
          requests:
            cpu: 200m
            memory: 256Mi
          limits:
            cpu: 1000m
            memory: 1Gi
        hpaSpec:
          minReplicas: 2
          maxReplicas: 5
          metrics:
            - type: Resource
              resource:
                name: cpu
                target:
                  type: Utilization
                  averageUtilization: 80
    ingressGateways:
      - name: istio-ingressgateway
        enabled: true
        k8s:
          resources:
            requests:
              cpu: 100m
              memory: 128Mi
            limits:
              cpu: 500m
              memory: 512Mi
          service:
            type: LoadBalancer
            ports:
              - port: 80
                targetPort: 8080
                name: http2
              - port: 443
                targetPort: 8443
                name: https
              - port: 15021
                targetPort: 15021
                name: status-port
    egressGateways:
      - name: istio-egressgateway
        enabled: true
        k8s:
          resources:
            requests:
              cpu: 50m
              memory: 64Mi
            limits:
              cpu: 200m
              memory: 256Mi
  values:
    global:
      proxy:
        resources:
          requests:
            cpu: 50m
            memory: 64Mi
          limits:
            cpu: 200m
            memory: 256Mi
      # Enable mTLS everywhere by default
      mtls:
        enabled: true
    pilot:
      traceSampling: 10.0
```

- [ ] **C4.2** Create PeerAuthentication (STRICT mTLS)

**File: `infra/k8s/base/istio/peer-authentication.yaml`**
```yaml
# Mesh-wide STRICT mTLS - all service-to-service communication must use mTLS
apiVersion: security.istio.io/v1
kind: PeerAuthentication
metadata:
  name: default
  namespace: istio-system
spec:
  mtls:
    mode: STRICT
---
# Namespace-level STRICT for default namespace (where services run)
apiVersion: security.istio.io/v1
kind: PeerAuthentication
metadata:
  name: vnshop-default-ns
  namespace: default
spec:
  mtls:
    mode: STRICT
---
# Exception: Allow plaintext for Kafka broker (external to mesh)
apiVersion: security.istio.io/v1
kind: PeerAuthentication
metadata:
  name: kafka-permissive
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: kafka
  mtls:
    mode: PERMISSIVE
  portLevelMtls:
    9092:
      mode: DISABLE
    9093:
      mode: DISABLE
---
# Exception: Allow plaintext for Redis (external to mesh initially)
apiVersion: security.istio.io/v1
kind: PeerAuthentication
metadata:
  name: redis-permissive
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: redis
  mtls:
    mode: PERMISSIVE
  portLevelMtls:
    6379:
      mode: DISABLE
    26379:
      mode: DISABLE
```

- [ ] **C4.3** Create DestinationRules for all services

**File: `infra/k8s/base/istio/destination-rules.yaml`**
```yaml
# DestinationRules enforce mTLS for outbound connections to each service
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: api-gateway
  namespace: default
spec:
  host: api-gateway.default.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
    connectionPool:
      http:
        h2UpgradePolicy: UPGRADE
        maxRequestsPerConnection: 100
      tcp:
        maxConnections: 100
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: order-service
  namespace: default
spec:
  host: order-service.default.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
    connectionPool:
      http:
        h2UpgradePolicy: UPGRADE
        maxRequestsPerConnection: 50
      tcp:
        maxConnections: 100
    outlierDetection:
      consecutive5xxErrors: 5
      interval: 10s
      baseEjectionTime: 30s
      maxEjectionPercent: 50
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: payment-service
  namespace: default
spec:
  host: payment-service.default.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
    connectionPool:
      http:
        h2UpgradePolicy: UPGRADE
        maxRequestsPerConnection: 50
      tcp:
        maxConnections: 100
    outlierDetection:
      consecutive5xxErrors: 3
      interval: 10s
      baseEjectionTime: 60s
      maxEjectionPercent: 30
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: inventory-service
  namespace: default
spec:
  host: inventory-service.default.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
    connectionPool:
      http:
        h2UpgradePolicy: UPGRADE
        maxRequestsPerConnection: 50
      tcp:
        maxConnections: 100
    outlierDetection:
      consecutive5xxErrors: 5
      interval: 10s
      baseEjectionTime: 30s
      maxEjectionPercent: 50
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: shipping-service
  namespace: default
spec:
  host: shipping-service.default.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
    connectionPool:
      http:
        h2UpgradePolicy: UPGRADE
        maxRequestsPerConnection: 50
      tcp:
        maxConnections: 100
    outlierDetection:
      consecutive5xxErrors: 5
      interval: 10s
      baseEjectionTime: 30s
      maxEjectionPercent: 50
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: product-service
  namespace: default
spec:
  host: product-service.default.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
    connectionPool:
      http:
        h2UpgradePolicy: UPGRADE
        maxRequestsPerConnection: 100
      tcp:
        maxConnections: 150
    outlierDetection:
      consecutive5xxErrors: 5
      interval: 10s
      baseEjectionTime: 30s
      maxEjectionPercent: 50
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: user-service
  namespace: default
spec:
  host: user-service.default.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
    connectionPool:
      http:
        h2UpgradePolicy: UPGRADE
        maxRequestsPerConnection: 50
      tcp:
        maxConnections: 100
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: cart-service
  namespace: default
spec:
  host: cart-service.default.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
    connectionPool:
      http:
        h2UpgradePolicy: UPGRADE
        maxRequestsPerConnection: 100
      tcp:
        maxConnections: 100
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: notification-service
  namespace: default
spec:
  host: notification-service.default.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
    connectionPool:
      http:
        maxRequestsPerConnection: 50
      tcp:
        maxConnections: 50
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: messaging-service
  namespace: default
spec:
  host: messaging-service.default.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
    connectionPool:
      http:
        maxRequestsPerConnection: 50
      tcp:
        maxConnections: 50
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: search-service
  namespace: default
spec:
  host: search-service.default.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
    connectionPool:
      http:
        h2UpgradePolicy: UPGRADE
        maxRequestsPerConnection: 100
      tcp:
        maxConnections: 150
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: recommendations-service
  namespace: default
spec:
  host: recommendations-service.default.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
    connectionPool:
      http:
        h2UpgradePolicy: UPGRADE
        maxRequestsPerConnection: 50
      tcp:
        maxConnections: 50
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: coupon-service
  namespace: default
spec:
  host: coupon-service.default.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
    connectionPool:
      http:
        h2UpgradePolicy: UPGRADE
        maxRequestsPerConnection: 50
      tcp:
        maxConnections: 50
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: seller-finance-service
  namespace: default
spec:
  host: seller-finance-service.default.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
    connectionPool:
      http:
        h2UpgradePolicy: UPGRADE
        maxRequestsPerConnection: 50
      tcp:
        maxConnections: 50
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: configuration-service
  namespace: default
spec:
  host: configuration-service.default.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
    connectionPool:
      http:
        maxRequestsPerConnection: 50
      tcp:
        maxConnections: 50
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: monitoring-service
  namespace: default
spec:
  host: monitoring-service-v2.default.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
    connectionPool:
      http:
        maxRequestsPerConnection: 50
      tcp:
        maxConnections: 50
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: keycloak
  namespace: default
spec:
  host: keycloak.default.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
    connectionPool:
      http:
        maxRequestsPerConnection: 100
      tcp:
        maxConnections: 100
```

- [ ] **C4.4** Create AuthorizationPolicies encoding the service call graph

**File: `infra/k8s/base/istio/authorization-policies.yaml`**
```yaml
# VNShop Service Call Graph - AuthorizationPolicies
#
# Call graph (derived from proto definitions + code analysis):
#   api-gateway -> all services (HTTP, port 8080-8099)
#   order-service -> inventory-service (gRPC :9093)
#   order-service -> payment-service (gRPC :9094)
#   order-service -> shipping-service (gRPC :9095)
#   payment-service -> order-service (Kafka callback - not enforced here, via Kafka)
#
# Kafka communication is handled outside the mesh (PERMISSIVE on Kafka ports)
# Database connections are direct (not mesh-managed)
#
# Policy: deny-all by default, allow only known caller->callee pairs
---
# Default deny-all for the namespace
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: deny-all
  namespace: default
spec:
  {}
---
# api-gateway: allow from istio-ingressgateway only
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-ingress-to-api-gateway
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: api-gateway
  action: ALLOW
  rules:
    - from:
        - source:
            namespaces: ["istio-system"]
        - source:
            principals: ["cluster.local/ns/istio-system/sa/istio-ingressgateway-service-account"]
      to:
        - operation:
            ports: ["8080"]
---
# order-service: allow from api-gateway (HTTP) + payment-service (gRPC callback if any)
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-to-order-service
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: order-service
  action: ALLOW
  rules:
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/api-gateway"]
      to:
        - operation:
            ports: ["8080"]
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/payment-service"]
      to:
        - operation:
            ports: ["8080", "9090"]
---
# payment-service: allow from api-gateway (HTTP) + order-service (gRPC :9094)
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-to-payment-service
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: payment-service
  action: ALLOW
  rules:
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/api-gateway"]
      to:
        - operation:
            ports: ["8080"]
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/order-service"]
      to:
        - operation:
            ports: ["9094"]
---
# inventory-service: allow from api-gateway (HTTP) + order-service (gRPC :9093)
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-to-inventory-service
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: inventory-service
  action: ALLOW
  rules:
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/api-gateway"]
      to:
        - operation:
            ports: ["8080"]
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/order-service"]
      to:
        - operation:
            ports: ["9093"]
---
# shipping-service: allow from api-gateway (HTTP) + order-service (gRPC :9095)
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-to-shipping-service
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: shipping-service
  action: ALLOW
  rules:
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/api-gateway"]
      to:
        - operation:
            ports: ["8080"]
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/order-service"]
      to:
        - operation:
            ports: ["9095"]
---
# product-service: allow from api-gateway only
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-to-product-service
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: product-service
  action: ALLOW
  rules:
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/api-gateway"]
      to:
        - operation:
            ports: ["8080"]
---
# user-service: allow from api-gateway only
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-to-user-service
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: user-service
  action: ALLOW
  rules:
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/api-gateway"]
      to:
        - operation:
            ports: ["8080"]
---
# cart-service: allow from api-gateway only
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-to-cart-service
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: cart-service
  action: ALLOW
  rules:
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/api-gateway"]
      to:
        - operation:
            ports: ["3001"]
---
# notification-service: allow from api-gateway only
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-to-notification-service
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: notification-service
  action: ALLOW
  rules:
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/api-gateway"]
      to:
        - operation:
            ports: ["3002"]
---
# messaging-service: allow from api-gateway only
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-to-messaging-service
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: messaging-service
  action: ALLOW
  rules:
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/api-gateway"]
      to:
        - operation:
            ports: ["3003"]
---
# search-service: allow from api-gateway only
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-to-search-service
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: search-service
  action: ALLOW
  rules:
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/api-gateway"]
      to:
        - operation:
            ports: ["8080"]
---
# recommendations-service: allow from api-gateway only
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-to-recommendations-service
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: recommendations-service
  action: ALLOW
  rules:
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/api-gateway"]
      to:
        - operation:
            ports: ["8080"]
---
# coupon-service: allow from api-gateway + order-service
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-to-coupon-service
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: coupon-service
  action: ALLOW
  rules:
    - from:
        - source:
            principals:
              - "cluster.local/ns/default/sa/api-gateway"
              - "cluster.local/ns/default/sa/order-service"
      to:
        - operation:
            ports: ["8080"]
---
# seller-finance-service: allow from api-gateway only
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-to-seller-finance-service
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: seller-finance-service
  action: ALLOW
  rules:
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/api-gateway"]
      to:
        - operation:
            ports: ["8080"]
---
# configuration-service: allow from api-gateway + all services (config reads)
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-to-configuration-service
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: configuration-service
  action: ALLOW
  rules:
    - from:
        - source:
            namespaces: ["default"]
      to:
        - operation:
            ports: ["3004"]
            methods: ["GET"]
---
# monitoring-service-v2: allow from all services (metrics push) + api-gateway
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-to-monitoring-service
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: monitoring-service-v2
  action: ALLOW
  rules:
    - from:
        - source:
            namespaces: ["default"]
      to:
        - operation:
            ports: ["3005"]
---
# keycloak: allow from api-gateway + all services (token validation)
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-to-keycloak
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: keycloak
  action: ALLOW
  rules:
    - from:
        - source:
            namespaces: ["default", "istio-system"]
      to:
        - operation:
            ports: ["8085"]
```

- [ ] **C4.5** Create VirtualServices for traffic management

**File: `infra/k8s/base/istio/virtual-services.yaml`**
```yaml
# VirtualServices for traffic management, retries, and timeouts
---
apiVersion: networking.istio.io/v1
kind: VirtualService
metadata:
  name: api-gateway-vs
  namespace: default
spec:
  hosts:
    - api-gateway
  http:
    - route:
        - destination:
            host: api-gateway
            port:
              number: 8080
      timeout: 30s
      retries:
        attempts: 2
        perTryTimeout: 10s
        retryOn: 5xx,reset,connect-failure,retriable-4xx
---
apiVersion: networking.istio.io/v1
kind: VirtualService
metadata:
  name: order-service-vs
  namespace: default
spec:
  hosts:
    - order-service
  http:
    - match:
        - port: 8080
      route:
        - destination:
            host: order-service
            port:
              number: 8080
      timeout: 15s
      retries:
        attempts: 2
        perTryTimeout: 5s
        retryOn: 5xx,reset,connect-failure
    - match:
        - port: 9090
      route:
        - destination:
            host: order-service
            port:
              number: 9090
      timeout: 10s
      retries:
        attempts: 1
        perTryTimeout: 5s
        retryOn: unavailable,resource-exhausted
---
apiVersion: networking.istio.io/v1
kind: VirtualService
metadata:
  name: payment-service-vs
  namespace: default
spec:
  hosts:
    - payment-service
  http:
    - match:
        - port: 8080
      route:
        - destination:
            host: payment-service
            port:
              number: 8080
      timeout: 30s
      retries:
        attempts: 1
        perTryTimeout: 15s
        retryOn: 5xx,reset,connect-failure
    - match:
        - port: 9094
      route:
        - destination:
            host: payment-service
            port:
              number: 9094
      timeout: 15s
      retries:
        attempts: 1
        perTryTimeout: 10s
        retryOn: unavailable,resource-exhausted
---
apiVersion: networking.istio.io/v1
kind: VirtualService
metadata:
  name: inventory-service-vs
  namespace: default
spec:
  hosts:
    - inventory-service
  http:
    - match:
        - port: 8080
      route:
        - destination:
            host: inventory-service
            port:
              number: 8080
      timeout: 10s
      retries:
        attempts: 2
        perTryTimeout: 3s
        retryOn: 5xx,reset,connect-failure
    - match:
        - port: 9093
      route:
        - destination:
            host: inventory-service
            port:
              number: 9093
      timeout: 5s
      retries:
        attempts: 2
        perTryTimeout: 2s
        retryOn: unavailable,resource-exhausted
---
apiVersion: networking.istio.io/v1
kind: VirtualService
metadata:
  name: shipping-service-vs
  namespace: default
spec:
  hosts:
    - shipping-service
  http:
    - match:
        - port: 8080
      route:
        - destination:
            host: shipping-service
            port:
              number: 8080
      timeout: 15s
      retries:
        attempts: 2
        perTryTimeout: 5s
        retryOn: 5xx,reset,connect-failure
    - match:
        - port: 9095
      route:
        - destination:
            host: shipping-service
            port:
              number: 9095
      timeout: 10s
      retries:
        attempts: 2
        perTryTimeout: 3s
        retryOn: unavailable,resource-exhausted
---
apiVersion: networking.istio.io/v1
kind: VirtualService
metadata:
  name: product-service-vs
  namespace: default
spec:
  hosts:
    - product-service
  http:
    - route:
        - destination:
            host: product-service
            port:
              number: 8080
      timeout: 10s
      retries:
        attempts: 2
        perTryTimeout: 3s
        retryOn: 5xx,reset,connect-failure
---
apiVersion: networking.istio.io/v1
kind: VirtualService
metadata:
  name: user-service-vs
  namespace: default
spec:
  hosts:
    - user-service
  http:
    - route:
        - destination:
            host: user-service
            port:
              number: 8080
      timeout: 10s
      retries:
        attempts: 2
        perTryTimeout: 3s
        retryOn: 5xx,reset,connect-failure
---
apiVersion: networking.istio.io/v1
kind: VirtualService
metadata:
  name: cart-service-vs
  namespace: default
spec:
  hosts:
    - cart-service
  http:
    - route:
        - destination:
            host: cart-service
            port:
              number: 3001
      timeout: 5s
      retries:
        attempts: 2
        perTryTimeout: 2s
        retryOn: 5xx,reset,connect-failure
```

- [ ] **C4.6** Create CI validation script for call graph staleness

**File: `infra/scripts/validate-istio-call-graph.sh`**
```bash
#!/bin/bash
set -euo pipefail

# Istio Call Graph Validation Script
# Validates that AuthorizationPolicies match the actual service call graph
# derived from proto definitions and gRPC adapter code.
#
# Run in CI to detect staleness when service dependencies change.
#
# Exit codes:
#   0 - Call graph is up to date
#   1 - Staleness detected or validation error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ISTIO_DIR="$PROJECT_ROOT/infra/k8s/base/istio"
PROTO_DIR="$PROJECT_ROOT/proto"
AUTH_POLICY_FILE="$ISTIO_DIR/authorization-policies.yaml"

ERRORS=0
WARNINGS=0

echo "=== VNShop Istio Call Graph Validation ==="
echo "Project root: $PROJECT_ROOT"
echo "Istio dir: $ISTIO_DIR"
echo ""

# Check required files exist
check_file_exists() {
  if [ ! -f "$1" ]; then
    echo "ERROR: Required file missing: $1"
    ERRORS=$((ERRORS + 1))
    return 1
  fi
  return 0
}

echo "--- Checking required files ---"
check_file_exists "$AUTH_POLICY_FILE" || true
check_file_exists "$ISTIO_DIR/peer-authentication.yaml" || true
check_file_exists "$ISTIO_DIR/destination-rules.yaml" || true
check_file_exists "$ISTIO_DIR/virtual-services.yaml" || true
check_file_exists "$ISTIO_DIR/istio-operator.yaml" || true
echo ""

# Extract gRPC service dependencies from proto files
echo "--- Extracting gRPC dependencies from proto files ---"
PROTO_SERVICES=()
if [ -d "$PROTO_DIR" ]; then
  while IFS= read -r line; do
    PROTO_SERVICES+=("$line")
  done < <(grep -rh "^service " "$PROTO_DIR"/*.proto 2>/dev/null | sed 's/service \([^ {]*\).*/\1/' | sort -u)
  echo "Found proto services: ${PROTO_SERVICES[*]:-none}"
else
  echo "WARNING: Proto directory not found at $PROTO_DIR"
  WARNINGS=$((WARNINGS + 1))
fi
echo ""

# Extract gRPC adapter calls from Java source
echo "--- Extracting gRPC adapter calls from Java source ---"
GRPC_CALLS=()

# order-service -> inventory-service (port 9093)
if grep -rq "inventory" "$PROJECT_ROOT/order-service/src" 2>/dev/null; then
  GRPC_CALLS+=("order-service->inventory-service:9093")
  echo "  Found: order-service -> inventory-service (gRPC)"
fi

# order-service -> payment-service (port 9094)
if grep -rq "payment" "$PROJECT_ROOT/order-service/src" 2>/dev/null; then
  GRPC_CALLS+=("order-service->payment-service:9094")
  echo "  Found: order-service -> payment-service (gRPC)"
fi

# order-service -> shipping-service (port 9095)
if grep -rq "shipping" "$PROJECT_ROOT/order-service/src" 2>/dev/null; then
  GRPC_CALLS+=("order-service->shipping-service:9095")
  echo "  Found: order-service -> shipping-service (gRPC)"
fi
echo ""

# Validate AuthorizationPolicies cover all discovered calls
echo "--- Validating AuthorizationPolicies ---"

validate_policy_exists() {
  local caller=$1
  local callee=$2
  local port=$3

  if grep -q "sa/$caller" "$AUTH_POLICY_FILE" 2>/dev/null; then
    # Check that the caller is allowed to reach the callee on the correct port
    # Simple heuristic: check both the service account and port appear in the same policy
    local callee_policy
    callee_policy=$(grep -A 30 "name: allow-to-${callee}" "$AUTH_POLICY_FILE" 2>/dev/null || echo "")
    
    if [ -z "$callee_policy" ]; then
      echo "  ERROR: No AuthorizationPolicy found for callee '$callee'"
      ERRORS=$((ERRORS + 1))
      return
    fi

    if echo "$callee_policy" | grep -q "$port"; then
      echo "  OK: $caller -> $callee:$port is authorized"
    else
      echo "  ERROR: $caller -> $callee:$port - port $port not found in policy"
      ERRORS=$((ERRORS + 1))
    fi
  else
    echo "  ERROR: Service account 'sa/$caller' not referenced in any policy"
    ERRORS=$((ERRORS + 1))
  fi
}

for call in "${GRPC_CALLS[@]}"; do
  caller=$(echo "$call" | cut -d'>' -f1 | sed 's/-$//')
  target=$(echo "$call" | cut -d'>' -f2)
  callee=$(echo "$target" | cut -d':' -f1)
  port=$(echo "$target" | cut -d':' -f2)
  validate_policy_exists "$caller" "$callee" "$port"
done
echo ""

# Check for services in workloads.yaml that lack AuthorizationPolicies
echo "--- Checking for services without policies ---"
EXPECTED_SERVICES=(
  "api-gateway"
  "order-service"
  "payment-service"
  "inventory-service"
  "shipping-service"
  "product-service"
  "user-service"
  "cart-service"
  "notification-service"
  "messaging-service"
  "search-service"
  "recommendations-service"
  "coupon-service"
  "seller-finance-service"
  "configuration-service"
  "monitoring-service-v2"
)

for svc in "${EXPECTED_SERVICES[@]}"; do
  svc_normalized=$(echo "$svc" | sed 's/-v2$//')
  if ! grep -q "name:.*${svc_normalized}\|name:.*${svc}" "$AUTH_POLICY_FILE" 2>/dev/null; then
    echo "  WARNING: No explicit policy found for service '$svc'"
    WARNINGS=$((WARNINGS + 1))
  fi
done
echo ""

# Validate YAML syntax
echo "--- Validating YAML syntax ---"
for yaml_file in "$ISTIO_DIR"/*.yaml; do
  if [ -f "$yaml_file" ]; then
    if command -v python3 &>/dev/null; then
      if python3 -c "import yaml; yaml.safe_load_all(open('$yaml_file'))" 2>/dev/null; then
        echo "  OK: $(basename "$yaml_file")"
      else
        echo "  ERROR: Invalid YAML in $(basename "$yaml_file")"
        ERRORS=$((ERRORS + 1))
      fi
    elif command -v yq &>/dev/null; then
      if yq eval '.' "$yaml_file" >/dev/null 2>&1; then
        echo "  OK: $(basename "$yaml_file")"
      else
        echo "  ERROR: Invalid YAML in $(basename "$yaml_file")"
        ERRORS=$((ERRORS + 1))
      fi
    else
      echo "  SKIP: No YAML validator available (install python3+pyyaml or yq)"
      break
    fi
  fi
done
echo ""

# Check for archived/stale policies (older than 6 months)
echo "--- Checking policy freshness (6-month archive policy) ---"
SIX_MONTHS_AGO=$(date -d "6 months ago" +%s 2>/dev/null || date -v-6m +%s 2>/dev/null || echo "0")
if [ "$SIX_MONTHS_AGO" != "0" ]; then
  for yaml_file in "$ISTIO_DIR"/*.yaml; do
    if [ -f "$yaml_file" ]; then
      FILE_MTIME=$(stat -c %Y "$yaml_file" 2>/dev/null || stat -f %m "$yaml_file" 2>/dev/null || echo "0")
      if [ "$FILE_MTIME" -lt "$SIX_MONTHS_AGO" ] && [ "$FILE_MTIME" != "0" ]; then
        echo "  WARNING: $(basename "$yaml_file") is older than 6 months - review for staleness"
        WARNINGS=$((WARNINGS + 1))
      fi
    fi
  done
else
  echo "  SKIP: Cannot determine date threshold on this platform"
fi
echo ""

# Summary
echo "=== Validation Summary ==="
echo "Errors: $ERRORS"
echo "Warnings: $WARNINGS"

if [ "$ERRORS" -gt 0 ]; then
  echo ""
  echo "FAILED: Call graph validation found $ERRORS error(s)."
  echo "Update infra/k8s/base/istio/authorization-policies.yaml to match the current service call graph."
  exit 1
fi

if [ "$WARNINGS" -gt 0 ]; then
  echo ""
  echo "PASSED with $WARNINGS warning(s). Review warnings above."
fi

echo ""
echo "PASSED: Istio call graph is consistent."
exit 0
```

- [ ] **C4.7** Create Istio README

**File: `infra/k8s/base/istio/README.md`**
```markdown
# VNShop Istio Service Mesh Configuration

## Overview

This directory contains Istio service mesh manifests for VNShop, implementing
mutual TLS (mTLS) authentication between all services.

## Prerequisites

- Kubernetes 1.30+
- istioctl v1.22+ installed locally
- Cluster with at least 4 CPU and 4GB memory available for Istio components

## Installation

### 1. Install Istio

```bash
istioctl install -f istio-operator.yaml --verify
```

### 2. Enable sidecar injection for default namespace

```bash
kubectl label namespace default istio-injection=enabled --overwrite
```

### 3. Apply mesh policies

```bash
kubectl apply -f peer-authentication.yaml
kubectl apply -f destination-rules.yaml
kubectl apply -f authorization-policies.yaml
kubectl apply -f virtual-services.yaml
```

### 4. Restart all workloads to inject sidecars

```bash
kubectl rollout restart deployment -n default
```

## Service Call Graph

```
                    ┌──────────────────┐
                    │ istio-ingress-gw │
                    └────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │   api-gateway    │ (HTTP :8080)
                    └────────┬─────────┘
                             │
          ┌──────────────────┼──────────────────────────┐
          │                  │                              │
    ┌─────▼─────┐    ┌──────▼──────┐    ┌─────────────▼─┐
    │order-svc  │    │product-svc  │    │ user/cart/search/ │
    └─────┬─────┘    └─────────────┘    │ coupon/seller/    │
          │                              │ notification/     │
    ┌─────┼────────────┐                │ messaging/recs/   │
    │     │            │                │ config/monitoring │
┌───▼──┐ ┌▼────────┐ ┌▼────────┐       └───────────────────┘
│inv-  │ │payment- │ │shipping-│
│svc   │ │svc      │ │svc      │
│:9093 │ │:9094    │ │:9095    │
└──────┘ └─────────┘ └─────────┘

gRPC calls (solid lines from order-service):
  order-service -> inventory-service  :9093
  order-service -> payment-service    :9094
  order-service -> shipping-service   :9095

Async (Kafka, outside mesh):
  payment-service -> order-service (payment.callback topic)
```

## Validation

Run the call graph validation script in CI:

```bash
./infra/scripts/validate-istio-call-graph.sh
```

This script:
- Extracts gRPC dependencies from proto files and Java source
- Verifies AuthorizationPolicies cover all discovered call paths
- Checks all services have associated policies
- Validates YAML syntax
- Warns about policies older than 6 months (archive policy)

## Troubleshooting

### Check mTLS status

```bash
istioctl x describe pod <pod-name>
```

### View proxy config

```bash
istioctl proxy-config clusters <pod-name>
```

### Check authorization denials

```bash
kubectl logs <pod-name> -c istio-proxy | grep "RBAC: access denied"
```

### Temporarily disable mTLS for debugging

Apply PERMISSIVE mode to a specific service:

```yaml
apiVersion: security.istio.io/v1
kind: PeerAuthentication
metadata:
  name: debug-permissive
  namespace: default
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: <service-name>
  mtls:
    mode: PERMISSIVE
```

## Archive Policy

All Istio manifests should be reviewed every 6 months. The CI validation
script warns when files exceed this age. When the service call graph changes:

1. Update `authorization-policies.yaml` with new caller/callee pairs
2. Update `destination-rules.yaml` if new services are added
3. Update `virtual-services.yaml` for timeout/retry adjustments
4. Run the validation script to confirm consistency
5. Update this README's call graph diagram
```

- [ ] **C4.8** Create Istio kustomization

**File: `infra/k8s/base/istio/kustomization.yaml`**
```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - peer-authentication.yaml
  - destination-rules.yaml
  - authorization-policies.yaml
  - virtual-services.yaml

# Note: istio-operator.yaml is applied separately via istioctl
# It is NOT included in kustomize build to avoid conflicts
```

- [ ] **C4.9** Update base kustomization to include istio

Append to `infra/k8s/base/kustomization.yaml`:
```yaml
resources:
  # ... existing resources ...
  - istio/
```

- [ ] **C4.10** Add CI validation step to GitHub workflow

Add to `.github/workflows/ci.yml` (new job or step in existing job):
```yaml
  validate-istio:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Validate Istio call graph
        run: |
          chmod +x infra/scripts/validate-istio-call-graph.sh
          ./infra/scripts/validate-istio-call-graph.sh
      - name: Validate Kustomize build (with istio)
        run: |
          kubectl kustomize infra/k8s/base/
          kubectl kustomize infra/k8s/overlays/prod/
```

- [ ] **C4.11** Verify all manifests build cleanly

```powershell
kubectl kustomize infra/k8s/base/
bash infra/scripts/validate-istio-call-graph.sh
```

- [ ] **C4.12** Commit

```
feat(istio): add mTLS service mesh with AuthorizationPolicies

- IstioOperator with STRICT mTLS, tracing to Jaeger, REGISTRY_ONLY
- PeerAuthentication STRICT mesh-wide (PERMISSIVE for Kafka/Redis)
- DestinationRules with ISTIO_MUTUAL, connection pools, outlier detection
- AuthorizationPolicies encoding full service call graph
- VirtualServices with per-service timeouts and retry policies
- CI validation script detecting call graph staleness
- README with install instructions and 6-month archive policy
```

---

## Verification Checklist

After all tasks are complete:

1. `kubectl kustomize infra/k8s/base/` builds without errors
2. `kubectl kustomize infra/k8s/overlays/prod/` builds without errors
3. `docker compose --profile ha config` validates without errors
4. `bash -n infra/scripts/init-kafka-topics.sh` passes
5. `bash -n infra/scripts/kafka-partition-reassignment.sh` passes
6. `bash infra/scripts/validate-istio-call-graph.sh` passes
7. All Java services compile with new application.yml sentinel config
8. Cart-service TypeScript compiles with new redis.config.ts