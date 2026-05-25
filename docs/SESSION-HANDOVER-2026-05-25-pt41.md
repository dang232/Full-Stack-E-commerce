# Session handover — 2026-05-25 (pt41: Kafka env-override audit closes the audit chapter)

**Last commit (HEAD before this block):** `5da49ac5` (`docs(pt40): status-code oracle closed; audit chapter wraps`)

**Gates (live stack):**
- order-service mvn: 122 / 122 (untouched).
- payment-service mvn: 76 / 76 (untouched).
- FE typecheck clean. vitest 165 / 165 (untouched).
- Workday suite: 3 / 3 in 23.9 s.
- Journey suite: 7 / 7 in 33.5 s.
- **Search-service kafka log clean** — `bootstrap.servers = [kafka:9092]` + clean `Started SearchServiceApplication` instead of the prior continuous `Bootstrap broker localhost:9092 ... disconnected` spam.

## What this block was

Pt32-pt34 listed "Kafka env-override audit on the other six services" as a carryover from pt31's `f551588d` fix on seller-finance-service. The user picked it as the next thread; the fix is six identical YAML edits, structurally cheaper than the pt37-pt40 audit arc, but the same shape of finding — a silent infrastructure bug masked by a passing journey suite.

## The finding

Six services had hard-coded `bootstrap-servers: localhost:9092` with no `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}` substitution:

```
order-service, payment-service, shipping-service,
inventory-service, search-service, review-service
```

Each docker-compose block exports `KAFKA_BOOTSTRAP_SERVERS=kafka:9092` already; the env was being silently ignored because Spring's relaxed-binding only takes effect on a property that uses placeholder substitution. A hard-coded literal value wins, and the env is invisible.

**Live-stack evidence**: search-service (consumer) was actively logging the same disconnect-spam pattern that pt31 found on seller-finance — `Bootstrap broker localhost:9092 ... disconnected`, retried every second since container boot. The other five services were quieter because they're producer-side: failed publishes don't loop, they just silently drop.

**Why the journey passed anyway**: chapters 1-4 don't exercise the broken consumers' downstream features. Chapter 5-6 are kafka-dependent but go through the order.created → seller-finance wallet credit path that pt31 already fixed. Search consumer feeds the catalog index — broken since the suite came up but never proven by an assertion that "this freshly-published product appears in search results." That's a coverage gap worth flagging in the next audit, not this one.

## Files touched this block

```
M  services/order-service/src/main/resources/application.yml
M  services/payment-service/src/main/resources/application.yml
M  services/shipping-service/src/main/resources/application.yml
M  services/inventory-service/src/main/resources/application.yml
M  services/search-service/src/main/resources/application.yml
M  services/review-service/src/main/resources/application.yml
A  docs/SESSION-HANDOVER-2026-05-25-pt41.md
```

Each fix is a single-line edit:
```
- bootstrap-servers: localhost:9092
+ bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
```

## Gotchas this block (extends pt40 list, #101-107)

**108. Hard-coded YAML wins over env vars unless `${...}` substitution is explicit.** Spring's relaxed-binding (`SPRING_KAFKA_BOOTSTRAP_SERVERS` env auto-mapping to `spring.kafka.bootstrap-servers`) is sometimes confused as universal — but it only fires when the property's resolved value goes through the substitution pipeline. A hard-coded literal in `application.yml` short-circuits the lookup, the env is read but discarded silently, and the result is a service that LOOKS configured (compose has the env, the container has the env) but actually isn't. Audit signal: grep all `application.yml` for `bootstrap-servers:` and ensure every match uses `${...}`.

**109. Producer-side kafka failures are quiet; consumer-side failures are loud.** Search-service was the only one logging the disconnect because it's the only consumer of the six. The five producer-only services had been silently dropping events since container boot — visible only as "downstream feature X doesn't work" rather than "service Y is unhealthy." When auditing kafka connectivity, don't trust silence; check producer-side delivery via topic offsets or downstream effects, not log noise.

## Cloud-stub gotcha #91 came up again

626 cloud-stubs across the six service `src` trees blocked the docker build with `invalid file request`. Same fix as pt34 — copy-delete-rename in PowerShell to force OneDrive to fully materialize each file. Worth flagging in the auto-memory: this happens on cold service rebuilds after a long quiet period in the working tree.

## Audit arc — fully closed

```
pt35  payout audit trail (data layer)               — gotchas #94-96
pt36  avatar upload (object storage)                — gotchas #97-100
pt37  Ship/Accept access control                    — gotchas #101-102
pt38  order-service IAE-as-403 sweep                — gotchas #103-104
pt39  payment-service sweep + missing 403 handler   — gotchas #105-106
pt40  status-code oracle on lookup misses           — gotcha  #107
pt41  kafka env-override sweep                      — gotchas #108-109
```

Seven blocks, fifteen gotchas, every grep-able anti-pattern across the seven services now slots into a named pattern. The audit chapter is done.

## Open thread for the next session

**Carryover from pt32-pt34 (only two left):**
- **PayPal capture round-trip** — wire the FE Smart Buttons → BE capture → order-status flip → refund hook. Largest of the carryover threads.
- **VNPay/MoMo `redirectUrl` from PaymentResponse** — surface the field cleanly so the FE drives the gateway redirect.
- **R2 swap for avatar storage** — gated on R2 credentials.

**New from this block (lower priority):**
- **Search-index integration coverage gap.** The journey suite never asserts "product X published in chapter 1 appears in search results." Search-service was disconnected from kafka the whole time and the suite couldn't see it. Add an AC that drives the search endpoint with the chapter-1-published product after a settle-poll on kafka lag.
- **Producer-side kafka health probe.** Audit gotcha #109 implies we'd benefit from a healthcheck or actuator probe that surfaces "I have published N events since boot, M acknowledged" so producer-side disconnects show up before they break a downstream feature.

## How to resume

1. **Verify HEAD.** `git log --oneline -3` shows pt41 commits at the top.
2. **Cloud-stub sanity check.** `Get-ChildItem -Force -Recurse services/*/src -File | Where-Object { $_.Mode -like '*l' }` — should be empty.
3. **Smoke gates:**
   - `cd services/order-service; ./mvnw test` → 122 / 122.
   - `cd services/payment-service; ./mvnw test` → 76 / 76.
   - `cd fe; npx tsc --noEmit` → 0 errors.
   - `cd fe; npm test -- --run` → 165 / 165.
   - Workday suite → 3 / 3.
   - Journey suite → 7 / 7.
4. **Kafka health probe (worth a manual check):**
   - `docker compose logs --since 1m search-service | grep -iE "bootstrap|disconnect"` — should show only `bootstrap.servers = [kafka:9092]` + no disconnect lines.
   - `docker compose exec kafka kafka-topics --bootstrap-server kafka:9092 --list` — should list every topic the platform writes to (order.created, etc.).

## Final session ledger (pt27 → pt41)

- **pt27-pt34**: i18n, dark-mode, BA-grade journey suite, chapter-6 flake root-cause.
- **pt35**: payout audit trail. Gotchas #94-96.
- **pt36**: avatar upload, MinIO+R2-swap. Gotchas #97-100.
- **pt37**: Ship/Accept access-control. Gotchas #101-102.
- **pt38**: order-service IAE-as-403 sweep. Gotchas #103-104.
- **pt39**: payment-service sweep + missing 403 handler. Gotchas #105-106.
- **pt40**: status-code oracle close on lookup misses. Gotcha #107.
- **pt41 (this)**: kafka env-override sweep. Gotchas #108-109.

The pt35-pt41 arc is the platform's "audit chapter" — every block found one or more silent infrastructure or security bug that the journey suite couldn't see, named the failure mode, and fixed every instance of it in one pass. The discipline that made the arc cheap is the auto-memory's "post-agent quality pass" preference: writing the gotcha at the moment of the first fix means the sweep is mechanical when it lands. By pt41, gotcha #108 (hard-coded YAML wins over env) is just a one-line grep + a six-line edit, where on its own it could have been a multi-day investigation.

Both halves of the arc — security (pt37-40) and infrastructure (pt35, 36, 41) — share the same play: spot the anti-pattern in one place, write it down, sweep the codebase, close the chapter.
