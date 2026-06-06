# CORS Configuration — API Gateway

## Overview

Cross-Origin Resource Sharing is configured entirely through Spring Cloud Gateway's `globalcors`
block in `services/api-gateway/src/main/resources/application.yml`, plus a mirroring
`CorsConfigurationSource` bean in `SecurityConfig` that makes the same policy visible to the
Spring Security WebFlux filter chain (required so OPTIONS preflights receive `Access-Control-*`
headers before the security chain finishes).

The allowed-origins list is controlled by a single environment variable:

```
GATEWAY_CORS_ALLOWED_ORIGINS=<comma-separated list of origins>
```

---

## Audit Findings (2026-06-07)

### Default value includes localhost — production risk

`application.yml` line 28 and `SecurityConfig.java` line 46 share the same fallback:

```yaml
allowed-origins: ${GATEWAY_CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173}
```

**If `GATEWAY_CORS_ALLOWED_ORIGINS` is not set at runtime, localhost origins are allowed.**
In production this would permit any browser-side JavaScript running on localhost to make
credentialled cross-origin requests to the live gateway. The environment variable MUST be
set to production origin(s) before deployment.

### No production profile overrides CORS

Only a `dev` profile exists (changes OTel sampling probability). There is no `prod` Spring
profile that narrows the CORS policy. The env-var override is the sole guard.

### allow-credentials discrepancy

The `globalcors` YAML block sets `allow-credentials: false`. The `SecurityConfig`
`CorsConfigurationSource` bean (which drives Spring Security's filter chain) sets
`allowCredentials(true)`. The Security bean takes precedence for requests that pass through
the security chain. Preflight/OPTIONS requests that hit the `CorsWebFilter` (registered by
Spring Cloud Gateway) use the YAML value. The mismatch is a minor inconsistency; the more
permissive value (`true`) is what authenticated browsers actually see.

---

## Configuration Reference

### Local development

Set in `.env` (or export before starting Docker Compose):

```bash
GATEWAY_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
```

This matches the default fallback in `application.yml`, so for local development the variable
can be omitted entirely.

### Production

Set the env var to the canonical frontend origin only. Multiple origins are comma-separated
with no spaces:

```bash
GATEWAY_CORS_ALLOWED_ORIGINS=https://vnshop.vn
```

Never include localhost in the production value.

### Docker Compose / Kubernetes

```yaml
# docker-compose.yml
environment:
  GATEWAY_CORS_ALLOWED_ORIGINS: "https://vnshop.vn"
```

```yaml
# Kubernetes deployment env
- name: GATEWAY_CORS_ALLOWED_ORIGINS
  valueFrom:
    secretKeyRef:
      name: gateway-config
      key: corsAllowedOrigins
```

---

## Allowed methods, headers, and caching

These values are hardcoded and are not overridable via environment variable:

| Setting           | Value                                        |
|-------------------|----------------------------------------------|
| Allowed methods   | GET, POST, PUT, PATCH, DELETE, OPTIONS       |
| Allowed headers   | `*` (all)                                    |
| Exposed headers   | `X-Correlation-Id`                           |
| Max age (preflight cache) | 3600 s (1 hour)                     |

---

## Where the configuration lives

| File | Purpose |
|------|---------|
| `services/api-gateway/src/main/resources/application.yml` | Spring Cloud Gateway `globalcors` block; drives `CorsWebFilter` for non-security requests |
| `services/api-gateway/src/main/java/com/vnshop/apigateway/infrastructure/config/SecurityConfig.java` | `CorsConfigurationSource` bean; drives Spring Security WebFlux CORS for all security-chain requests |
| `.env.example` | Documents `GATEWAY_CORS_ALLOWED_ORIGINS` with local dev defaults |
