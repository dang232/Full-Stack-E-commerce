# Feature Flag Guidelines

## When to Use Feature Flags

Use flags for:
- **Gradual rollouts** — release to 10% → 50% → 100% of users
- **A/B testing** — compare two implementations with real traffic
- **Kill switches** — instantly disable a feature without redeploying
- **Ops toggles** — enable debugging/profiling in production

Do NOT use flags for:
- **Incomplete features** — use feature branches instead
- **Configuration** — use Spring profiles or env vars
- **Permanent conditions** — if it will never be removed, it's not a flag

## Flag Lifecycle

1. **Create** — define in Unleash UI or API, assign owner
2. **Enable** — gradual rollout or full activation
3. **Remove** — delete from code + Unleash within 30 days of full activation

**Maximum active lifespan: 30 days.** Flags older than 30 days trigger a Slack alert.

## Naming Convention

Format: `<domain>.<feature-name>`

Examples:
- `checkout.new-flow`
- `search.enhanced-algorithm`
- `seller.analytics-v2`

## Pre-configured Flags

| Flag | Type | Purpose |
|------|------|---------|
| `checkout.new-flow` | Release | Gradual rollout of new checkout |
| `search.enhanced-algorithm` | Experiment | A/B test for search ranking |
| `seller.analytics-v2` | Release | New seller dashboard |

## Architecture

Each Java service integrates via a `FeatureFlagPort` interface in the domain layer, implemented by `UnleashFeatureFlagAdapter` in infrastructure. This keeps Unleash SDK dependency out of the domain.

```
domain/port/out/FeatureFlagPort.java
infrastructure/featureflag/UnleashFeatureFlagAdapter.java
```

## Unleash Access

- **Admin UI:** http://localhost:4242
- **Client API:** `http://unleash:4242/api` (internal Docker network)
- **API Token (dev):** `default:development.unleash-insecure-client-token`
