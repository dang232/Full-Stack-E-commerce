from pathlib import Path

root = Path('infra/k8s')
for directory in [root / 'base', root / 'overlays/dev', root / 'overlays/staging', root / 'overlays/prod']:
    directory.mkdir(parents=True, exist_ok=True)

services = [
    ('api-gateway', 8080, 'spring'),
    ('user', 8081, 'spring'),
    ('product', 8082, 'spring'),
    ('inventory', 8083, 'spring'),
    ('search', 8086, 'spring'),
    ('cart', 8084, 'spring'),
    ('order', 8091, 'spring'),
    ('payment', 8092, 'spring'),
    ('shipping', 8093, 'spring'),
    ('notification', 8087, 'nest'),
    ('coupon', 8088, 'spring'),
    ('review', 8089, 'spring'),
    ('seller-finance', 8090, 'spring'),
]

(root / 'base/configmap.yaml').write_text('''apiVersion: v1
kind: ConfigMap
metadata:
  name: vnshop-app-config
data:
  REDIS_HOST: redis
  REDIS_PORT: "6379"
  KAFKA_BOOTSTRAP_SERVERS: kafka:9092
  KEYCLOAK_ISSUER_URI: http://keycloak:8085/realms/vnshop
  MANAGEMENT_OTLP_TRACING_ENDPOINT: http://jaeger:4318/v1/traces
  OTEL_EXPORTER_OTLP_TRACES_ENDPOINT: http://jaeger:4318/v1/traces
  DB_HOST: postgres
  DB_PORT: "5432"
  DB_NAME: vnshop
''', encoding='utf-8')

(root / 'base/secret.yaml').write_text('''apiVersion: v1
kind: Secret
metadata:
  name: vnshop-app-secret
type: Opaque
stringData:
  SPRING_DATASOURCE_USERNAME: vnshop
  SPRING_DATASOURCE_PASSWORD: change-me
  DB_USERNAME: vnshop
  DB_PASSWORD: change-me
  GATEWAY_OAUTH2_CLIENT_SECRET: change-me
''', encoding='utf-8')

(root / 'base/serviceaccount.yaml').write_text('''apiVersion: v1
kind: ServiceAccount
metadata:
  name: vnshop-workload
automountServiceAccountToken: false
''', encoding='utf-8')

parts = []
for name, port, kind in services:
    app = f'vnshop-{name}'
    image = 'vnshop/api-gateway:latest' if name == 'api-gateway' else f'vnshop/{name}-service:latest'
    schema = f"{name.replace('-', '_')}_svc"
    env = [('SERVER_PORT', str(port))]
    if name == 'notification':
        env = [('PORT', str(port))]
    if name != 'api-gateway':
        env.append(('SPRING_DATASOURCE_URL', f'jdbc:postgresql://postgres:5432/vnshop?currentSchema={schema}'))
    env_lines = '\n'.join([f'        - name: {key}\n          value: "{value}"' for key, value in env])
    probe = '/health' if kind == 'nest' else '/actuator/health'
    readiness = '/health' if kind == 'nest' else '/actuator/health/readiness'
    liveness = '/health' if kind == 'nest' else '/actuator/health/liveness'
    service_type = 'NodePort' if name == 'api-gateway' else 'ClusterIP'
    if name == 'api-gateway':
        ingress = '''  ingress:
  - from:
    - namespaceSelector: {}
    ports:
    - protocol: TCP
      port: 8080'''
    else:
        ingress = f'''  ingress:
  - from:
    - podSelector:
        matchLabels:
          app.kubernetes.io/name: vnshop-api-gateway
    ports:
    - protocol: TCP
      port: {port}'''
    parts.append(f'''---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {app}
  labels:
    app.kubernetes.io/name: {app}
    app.kubernetes.io/part-of: vnshop
spec:
  replicas: 2
  selector:
    matchLabels:
      app.kubernetes.io/name: {app}
  template:
    metadata:
      labels:
        app.kubernetes.io/name: {app}
        app.kubernetes.io/part-of: vnshop
    spec:
      serviceAccountName: vnshop-workload
      automountServiceAccountToken: false
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        runAsGroup: 10001
        fsGroup: 10001
        seccompProfile:
          type: RuntimeDefault
      containers:
      - name: {name}
        image: {image}
        imagePullPolicy: IfNotPresent
        ports:
        - name: http
          containerPort: {port}
        env:
{env_lines}
        envFrom:
        - configMapRef:
            name: vnshop-app-config
        - secretRef:
            name: vnshop-app-secret
        readinessProbe:
          httpGet:
            path: {readiness}
            port: http
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 6
        livenessProbe:
          httpGet:
            path: {liveness}
            port: http
          periodSeconds: 20
          timeoutSeconds: 3
          failureThreshold: 3
        startupProbe:
          httpGet:
            path: {probe}
            port: http
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 30
        resources:
          requests:
            cpu: 100m
            memory: 256Mi
          limits:
            cpu: 500m
            memory: 768Mi
        securityContext:
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: false
          capabilities:
            drop:
            - ALL
---
apiVersion: v1
kind: Service
metadata:
  name: {app}
  labels:
    app.kubernetes.io/name: {app}
    app.kubernetes.io/part-of: vnshop
spec:
  type: {service_type}
  selector:
    app.kubernetes.io/name: {app}
  ports:
  - name: http
    port: {port}
    targetPort: http
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {app}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {app}
  minReplicas: 2
  maxReplicas: 6
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: {app}
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: {app}
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: {app}
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/name: {app}
  policyTypes:
  - Ingress
  - Egress
{ingress}
  egress:
  - to:
    - podSelector: {{}}
  - to:
    - namespaceSelector: {{}}
    ports:
    - protocol: TCP
      port: 5432
    - protocol: TCP
      port: 6379
    - protocol: TCP
      port: 9092
    - protocol: TCP
      port: 8085
    - protocol: TCP
      port: 4318
  - to:
    - namespaceSelector: {{}}
    ports:
    - protocol: UDP
      port: 53
    - protocol: TCP
      port: 53
''')

(root / 'base/workloads.yaml').write_text('\n'.join(parts), encoding='utf-8')
(root / 'base/kustomization.yaml').write_text('''apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
- serviceaccount.yaml
- configmap.yaml
- secret.yaml
- workloads.yaml
''', encoding='utf-8')

for env, namespace, replicas, max_replicas, tag in [
    ('dev', 'vnshop-dev', 1, 3, 'dev'),
    ('staging', 'vnshop-staging', 2, 6, 'staging'),
    ('prod', 'vnshop-prod', 3, 10, 'prod'),
]:
    overlay = root / 'overlays' / env
    (overlay / 'namespace.yaml').write_text(f'''apiVersion: v1
kind: Namespace
metadata:
  name: {namespace}
  labels:
    app.kubernetes.io/part-of: vnshop
    environment: {env}
''', encoding='utf-8')
    replicas_yaml = '\n'.join([f'- name: vnshop-{name}\n  count: {replicas}' for name, _, _ in services])
    images_yaml = '\n'.join([
        f"- name: {'vnshop/api-gateway' if name == 'api-gateway' else 'vnshop/' + name + '-service'}\n  newTag: {tag}"
        for name, _, _ in services
    ])
    hpa_patches = '\n'.join([
        f'''- target:
    kind: HorizontalPodAutoscaler
    name: vnshop-{name}
  patch: |-
    - op: replace
      path: /spec/maxReplicas
      value: {max_replicas}'''
        for name, _, _ in services
    ])
    cm_extra = {
        'dev': '  SPRING_PROFILES_ACTIVE: dev\n  NODE_ENV: development\n',
        'staging': '  SPRING_PROFILES_ACTIVE: staging\n  NODE_ENV: production\n',
        'prod': '  SPRING_PROFILES_ACTIVE: prod\n  NODE_ENV: production\n',
    }[env]
    (overlay / 'configmap-env.yaml').write_text(f'''apiVersion: v1
kind: ConfigMap
metadata:
  name: vnshop-app-config
data:
{cm_extra}''', encoding='utf-8')
    (overlay / 'kustomization.yaml').write_text(f'''apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
namespace: {namespace}
resources:
- ../../base
- namespace.yaml
replicas:
{replicas_yaml}
images:
{images_yaml}
patchesStrategicMerge:
- configmap-env.yaml
patches:
{hpa_patches}
''', encoding='utf-8')

print(f'created infra/k8s manifests for {len(services)} services')
