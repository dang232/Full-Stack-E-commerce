# Phase 7: Frontend Maturity & Developer Experience — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add SEO mitigation for the SPA, complete i18n adoption, improve accessibility, and add developer tooling (Makefile, hot-reload, devcontainer, PR templates).

**Architecture:** Add Vite SSR plugin or prerender for product/category pages, sweep remaining hardcoded strings into i18n, add aria-live regions, create Makefile for common tasks, add spring-boot-devtools, create devcontainer.

**Tech Stack:** Vite, React, i18next, Playwright, Docker, Make

**Depends on:** Phase 4 (security baseline). Can run parallel to Phases 5-6.

---

## What's Wrong (Evidence)

| # | Problem | Detail |
|---|---------|--------|
| 1 | No SSR/SSG for SEO | Pure SPA — product pages invisible to crawlers |
| 2 | Hardcoded Vietnamese strings | ErrorBoundary, vnshop-context addToCart, guest cart prompts skip `t()` |
| 3 | No React.StrictMode | main.tsx renders without StrictMode wrapper |
| 4 | Loading fallback is bare text | Suspense shows "Dang tai..." div — no skeleton |
| 5 | No aria-live for dynamic content | Cart badge, toasts, WS notifications not announced |
| 6 | No Makefile or task runner | 16-service project requires memorizing docker-compose commands |
| 7 | No hot-reload for Java | Each code change requires full container rebuild |
| 8 | No PR/issue templates | .github/ only has workflow files |
| 9 | No devcontainer | Windows/OneDrive reparse-point issues affect contributors |
| 10 | No bundle size CI gate | chunkSizeWarningLimit set but no CI enforcement |

---

## Stage 1: SEO Mitigation (Task 1)

### Task 1: Add prerender + meta-tag injection + sitemap for product pages

**Files:**
- Create: `fe/prerender.config.ts`
- Create: `fe/src/utils/meta-tags.ts`
- Create: `fe/public/sitemap.xml` (or dynamic generation script)
- Modify: `fe/vite.config.ts`
- Modify: `fe/index.html` (add meta placeholders)

- [ ] **Step 1: Install vite-plugin-prerender (or alternative: react-helmet-async)**

```bash
cd fe && npm install --save-dev vite-plugin-prerender puppeteer
npm install react-helmet-async
```

- [ ] **Step 2: Add react-helmet-async for dynamic meta tags**

Create `fe/src/utils/meta-tags.ts`:
```typescript
import { useEffect } from 'react';

export function usePageMeta(meta: { title: string; description: string; image?: string }) {
  useEffect(() => {
    document.title = `${meta.title} | VNShop`;
    
    const setMeta = (name: string, content: string) => {
      let el = document.querySelector(`meta[name="${name}"]`) as HTMLMetaElement;
      if (!el) {
        el = document.createElement('meta');
        el.name = name;
        document.head.appendChild(el);
      }
      el.content = content;
    };

    setMeta('description', meta.description);
    if (meta.image) {
      setMeta('og:image', meta.image);
    }
    setMeta('og:title', meta.title);
    setMeta('og:description', meta.description);
  }, [meta.title, meta.description, meta.image]);
}
```

- [ ] **Step 3: Add meta tags to product detail page**

In the ProductDetail page component, add:
```typescript
import { usePageMeta } from '@/utils/meta-tags';

// Inside component:
usePageMeta({
  title: product.name,
  description: product.description?.slice(0, 160) ?? '',
  image: product.imageUrls?.[0],
});
```

- [ ] **Step 4: Create sitemap generation script**

Create `fe/scripts/generate-sitemap.ts`:
```typescript
import fs from 'fs';

const BASE_URL = 'https://vnshop.vn';

async function generateSitemap() {
  // Fetch all product IDs from API
  const res = await fetch('http://localhost:8080/products?size=1000');
  const data = await res.json();
  const products = data.data?.content ?? [];

  const urls = [
    { loc: '/', priority: '1.0' },
    { loc: '/categories', priority: '0.8' },
    ...products.map((p: any) => ({ loc: `/products/${p.id}`, priority: '0.7' })),
  ];

  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${urls.map(u => `  <url><loc>${BASE_URL}${u.loc}</loc><priority>${u.priority}</priority></url>`).join('\n')}
</urlset>`;

  fs.writeFileSync('public/sitemap.xml', xml);
  console.log(`Generated sitemap with ${urls.length} URLs`);
}

generateSitemap();
```

- [ ] **Step 5: Add robots.txt**

Create `fe/public/robots.txt`:
```
User-agent: *
Allow: /
Sitemap: https://vnshop.vn/sitemap.xml
```

- [ ] **Step 6: Commit**

```bash
git add fe/
git commit -m "feat(seo): add meta-tag injection, sitemap generation, and robots.txt"
```

---

## Stage 2: i18n Completion & React.StrictMode (Tasks 2-3)

### Task 2: Sweep all hardcoded Vietnamese strings into i18n

**Files:**
- Modify: Multiple `fe/src/` components with hardcoded strings
- Modify: `fe/src/i18n/locales/vi.json`
- Modify: `fe/src/i18n/locales/en.json`

- [ ] **Step 1: Find all hardcoded Vietnamese strings**

Search for common patterns:
```bash
cd fe && grep -rn "Vui long\|Co loi\|Dang tai\|Them vao\|Xoa\|Sua\|Huy\|Luu\|Dong" src/ --include="*.tsx" --include="*.ts" | grep -v "i18n\|locales\|.spec\|.test"
```

- [ ] **Step 2: Add missing keys to vi.json and en.json**

Add to `fe/src/i18n/locales/vi.json`:
```json
{
  "errors": {
    "generic": "Có lỗi xảy ra. Vui lòng thử lại.",
    "network": "Không thể kết nối. Kiểm tra mạng.",
    "unauthorized": "Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại."
  },
  "cart": {
    "loginRequired": "Vui lòng đăng nhập để thêm vào giỏ hàng",
    "addSuccess": "Đã thêm vào giỏ hàng",
    "empty": "Giỏ hàng trống"
  },
  "loading": {
    "page": "Đang tải...",
    "more": "Đang tải thêm..."
  }
}
```

Add equivalent to `en.json`:
```json
{
  "errors": {
    "generic": "Something went wrong. Please try again.",
    "network": "Cannot connect. Check your network.",
    "unauthorized": "Session expired. Please log in again."
  },
  "cart": {
    "loginRequired": "Please log in to add items to cart",
    "addSuccess": "Added to cart",
    "empty": "Your cart is empty"
  },
  "loading": {
    "page": "Loading...",
    "more": "Loading more..."
  }
}
```

- [ ] **Step 3: Replace hardcoded strings in components**

For each found instance, replace with `t()` call:
```typescript
// OLD:
toast.error('Vui lòng đăng nhập để thêm vào giỏ hàng');
// NEW:
toast.error(t('cart.loginRequired'));

// OLD:
<div>Đang tải...</div>
// NEW:
<div>{t('loading.page')}</div>

// OLD:
<p>Có lỗi xảy ra</p>
// NEW:
<p>{t('errors.generic')}</p>
```

- [ ] **Step 4: Run i18n lint check**

```bash
cd fe && npx i18next-parser --config i18next-parser.config.js
```
Verify no missing keys reported.

- [ ] **Step 5: Commit**

```bash
git add fe/src/
git commit -m "feat(i18n): replace all hardcoded Vietnamese strings with t() translation calls"
```

### Task 3: Add React.StrictMode and loading skeletons

**Files:**
- Modify: `fe/src/main.tsx`
- Create: `fe/src/components/ui/page-skeleton.tsx`

- [ ] **Step 1: Wrap app in StrictMode**

In `fe/src/main.tsx`, wrap the root render:
```typescript
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
```

- [ ] **Step 2: Create skeleton component**

Create `fe/src/components/ui/page-skeleton.tsx`:
```typescript
export function PageSkeleton() {
  return (
    <div className="animate-pulse space-y-4 p-6" aria-busy="true" aria-label="Loading content">
      <div className="h-8 bg-muted rounded w-1/3" />
      <div className="h-4 bg-muted rounded w-2/3" />
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-6">
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} className="h-48 bg-muted rounded" />
        ))}
      </div>
    </div>
  );
}

export function ProductDetailSkeleton() {
  return (
    <div className="animate-pulse p-6 grid grid-cols-1 md:grid-cols-2 gap-8" aria-busy="true">
      <div className="h-96 bg-muted rounded" />
      <div className="space-y-4">
        <div className="h-8 bg-muted rounded w-3/4" />
        <div className="h-6 bg-muted rounded w-1/4" />
        <div className="h-4 bg-muted rounded w-full" />
        <div className="h-4 bg-muted rounded w-5/6" />
        <div className="h-12 bg-muted rounded w-1/3 mt-8" />
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Replace Suspense fallbacks**

In router/route definitions, replace:
```typescript
// OLD:
<Suspense fallback={<div>Đang tải...</div>}>
// NEW:
<Suspense fallback={<PageSkeleton />}>
```

For product detail route specifically:
```typescript
<Suspense fallback={<ProductDetailSkeleton />}>
```

- [ ] **Step 4: Run frontend tests**

```bash
cd fe && npm test
```

- [ ] **Step 5: Commit**

```bash
git add fe/src/
git commit -m "feat(ux): add React.StrictMode, skeleton loading states for all lazy routes"
```

---

## Stage 3: Accessibility (Task 4)

### Task 4: Add aria-live regions for dynamic content updates

**Files:**
- Modify: Cart badge component
- Modify: Toast/notification provider
- Create: `fe/src/components/ui/live-region.tsx`

- [ ] **Step 1: Create reusable live region component**

Create `fe/src/components/ui/live-region.tsx`:
```typescript
import { useEffect, useRef, useState } from 'react';

interface LiveRegionProps {
  message: string;
  politeness?: 'polite' | 'assertive';
}

/**
 * Announces dynamic content changes to screen readers.
 * Visually hidden but accessible.
 */
export function LiveRegion({ message, politeness = 'polite' }: LiveRegionProps) {
  const [announced, setAnnounced] = useState('');

  useEffect(() => {
    if (message) {
      // Clear then set to force re-announcement
      setAnnounced('');
      const timer = setTimeout(() => setAnnounced(message), 100);
      return () => clearTimeout(timer);
    }
  }, [message]);

  return (
    <div
      role="status"
      aria-live={politeness}
      aria-atomic="true"
      className="sr-only"
    >
      {announced}
    </div>
  );
}
```

- [ ] **Step 2: Add live region to cart badge**

In the cart badge/icon component:
```typescript
import { LiveRegion } from '@/components/ui/live-region';
import { useTranslation } from 'react-i18next';

// Inside component:
const { t } = useTranslation();
const cartCount = useCartCount();

return (
  <>
    <CartIcon count={cartCount} />
    <LiveRegion message={t('cart.itemCount', { count: cartCount })} />
  </>
);
```

- [ ] **Step 3: Add aria-live to toast notifications**

In the toast provider/container, add:
```typescript
<div aria-live="polite" aria-relevant="additions" className="sr-only">
  {latestToast?.message}
</div>
```

- [ ] **Step 4: Add aria-live to WebSocket notification badge**

When new notifications arrive via WebSocket:
```typescript
<LiveRegion
  message={newNotification ? t('notifications.new', { count: unreadCount }) : ''}
  politeness="polite"
/>
```

- [ ] **Step 5: Commit**

```bash
git add fe/src/
git commit -m "a11y(frontend): add aria-live regions for cart, toasts, and notification updates"
```

---

## Stage 4: Developer Experience — Makefile (Task 5)

### Task 5: Create Makefile with common developer commands

**Files:**
- Create: `Makefile`

- [ ] **Step 1: Create comprehensive Makefile**

```makefile
.PHONY: up down restart logs test seed clean help

# ─── Stack Management ─────────────────────────────────────
up: ## Start full stack
	docker compose --profile apps up -d

up-minimal: ## Start gateway + order-service + infra only
	docker compose up -d postgres-order kafka redis keycloak elasticsearch api-gateway order-service

down: ## Stop all containers
	docker compose --profile apps down

restart: ## Restart a specific service (usage: make restart s=order-service)
	docker compose restart $(s)

rebuild: ## Rebuild and restart a service (usage: make rebuild s=order-service)
	docker compose up -d --build $(s)

# ─── Logs ─────────────────────────────────────────────────
logs: ## Tail logs for a service (usage: make logs s=order-service)
	docker compose logs -f --tail=100 $(s)

logs-all: ## Tail all service logs
	docker compose logs -f --tail=50

# ─── Testing ──────────────────────────────────────────────
test-order: ## Run order-service tests
	cd services/order-service && mvn test -q

test-payment: ## Run payment-service tests
	cd services/payment-service && mvn test -q

test-cart: ## Run cart-service tests
	cd services/cart-service && npm test

test-fe: ## Run frontend tests
	cd fe && npm test

test-e2e: ## Run Playwright E2E tests
	cd fe && npx playwright test

test-all: ## Run all unit tests (Java + Node)
	@echo "Testing Java services..."
	cd services/order-service && mvn test -q
	cd services/payment-service && mvn test -q
	cd services/product-service && mvn test -q
	@echo "Testing Node services..."
	cd services/cart-service && npm test
	@echo "Testing frontend..."
	cd fe && npm test

# ─── Build ────────────────────────────────────────────────
build-java: ## Build all Java services
	cd services/order-service && mvn package -DskipTests -q
	cd services/payment-service && mvn package -DskipTests -q
	cd services/product-service && mvn package -DskipTests -q

compile-order: ## Quick compile check for order-service
	cd services/order-service && mvn compile -q

# ─── Database ─────────────────────────────────────────────
seed: ## Run Keycloak realm import + Kafka topic creation
	docker compose exec kafka /opt/scripts/init-kafka-topics.sh

migrate: ## Run Flyway migrations for all services (via app restart)
	docker compose --profile apps restart

psql: ## Connect to order-service database
	docker compose exec postgres-order psql -U vnshop -d order_svc

# ─── Utilities ────────────────────────────────────────────
certs: ## Generate Kafka SSL certificates
	cd infra/kafka/certs && ./generate-certs.sh

clean: ## Remove all containers, volumes, and build artifacts
	docker compose --profile apps down -v
	cd services/order-service && mvn clean -q

status: ## Show running containers and their health
	docker compose ps

# ─── Help ─────────────────────────────────────────────────
help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

.DEFAULT_GOAL := help
```

- [ ] **Step 2: Commit**

```bash
git add Makefile
git commit -m "dx(makefile): add task runner with up/down/test/logs/seed/clean targets"
```

---

## Stage 5: Hot-Reload, DevContainer, PR Templates (Tasks 6-8)

### Task 6: Add hot-reload profile for Java services

**Files:**
- Modify: `services/order-service/pom.xml` (add devtools)
- Create: `docker-compose.override.yml` (dev volumes + debug ports)

- [ ] **Step 1: Add spring-boot-devtools to Java service POMs**

In each Java service pom.xml, add (under dependencies):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

- [ ] **Step 2: Create docker-compose.override.yml for dev mode**

Create `docker-compose.override.yml`:
```yaml
# Dev overrides — auto-loaded by docker compose
# Adds JDWP debug ports and source mounts for hot-reload
services:
  order-service:
    environment:
      - JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
    ports:
      - "5005:5005"

  payment-service:
    environment:
      - JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5006
    ports:
      - "5006:5006"

  product-service:
    environment:
      - JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5007
    ports:
      - "5007:5007"
```

- [ ] **Step 3: Add to .gitignore note and commit**

```bash
git add services/*/pom.xml docker-compose.override.yml
git commit -m "dx(devtools): add spring-boot-devtools + JDWP debug ports for hot-reload"
```

### Task 7: Create devcontainer configuration

**Files:**
- Create: `.devcontainer/devcontainer.json`
- Create: `.devcontainer/Dockerfile`

- [ ] **Step 1: Create devcontainer.json**

```json
{
  "name": "VNShop Dev",
  "build": {
    "dockerfile": "Dockerfile"
  },
  "features": {
    "ghcr.io/devcontainers/features/java:1": { "version": "21" },
    "ghcr.io/devcontainers/features/node:1": { "version": "20" },
    "ghcr.io/devcontainers/features/docker-in-docker:2": {},
    "ghcr.io/devcontainers/features/kubectl-helm-minikube:1": {}
  },
  "forwardPorts": [3000, 5173, 8080, 8081, 8082, 8083, 8084, 9200],
  "postCreateCommand": "cd fe && npm install",
  "customizations": {
    "vscode": {
      "extensions": [
        "vscjava.vscode-java-pack",
        "dbaeumer.vscode-eslint",
        "esbenp.prettier-vscode",
        "ms-playwright.playwright",
        "bradlc.vscode-tailwindcss"
      ],
      "settings": {
        "java.configuration.updateBuildConfiguration": "automatic"
      }
    }
  },
  "remoteUser": "vscode"
}
```

- [ ] **Step 2: Create Dockerfile**

```dockerfile
FROM mcr.microsoft.com/devcontainers/base:ubuntu

# Maven
RUN apt-get update && apt-get install -y maven \
    && rm -rf /var/lib/apt/lists/*

# k6 for perf tests
RUN gpg -k && gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
    --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69 \
    && echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
    | tee /etc/apt/sources.list.d/k6.list \
    && apt-get update && apt-get install -y k6 \
    && rm -rf /var/lib/apt/lists/*
```

- [ ] **Step 3: Commit**

```bash
git add .devcontainer/
git commit -m "dx(devcontainer): add container-based dev environment with Java 21 + Node 20"
```

### Task 8: Add PR and issue templates

**Files:**
- Create: `.github/PULL_REQUEST_TEMPLATE.md`
- Create: `.github/ISSUE_TEMPLATE/bug.md`
- Create: `.github/ISSUE_TEMPLATE/feature.md`

- [ ] **Step 1: Create PR template**

```markdown
## Summary

<!-- One sentence: what does this PR do? -->

## Bounded Context

<!-- Which service(s) / domain(s) does this touch? -->

## Changes

- 

## Testing

- [ ] Unit tests pass (`make test-<service>`)
- [ ] E2E tests pass (if UI changes)
- [ ] Manual verification (describe what you checked)

## Checklist

- [ ] No hardcoded secrets or credentials
- [ ] No new infrastructure imports in application layer
- [ ] Migration added if DB schema changed
- [ ] i18n keys added for any new user-facing strings

## Deferred / Follow-up

<!-- Anything intentionally left for a separate PR? -->
```

- [ ] **Step 2: Create bug issue template**

```markdown
---
name: Bug Report
about: Report a bug or unexpected behavior
labels: bug
---

## Describe the bug

## Steps to reproduce
1. 
2. 
3. 

## Expected behavior

## Actual behavior

## Environment
- Branch: 
- Profile: apps / minimal / ha
- OS: 
```

- [ ] **Step 3: Create feature issue template**

```markdown
---
name: Feature Request
about: Suggest a new feature or improvement
labels: enhancement
---

## Problem

<!-- What problem does this solve? -->

## Proposed solution

## Bounded context

<!-- Which service(s) would this touch? -->

## Acceptance criteria
- [ ] 
```

- [ ] **Step 4: Add bundle size CI gate**

Add to `fe/package.json` scripts:
```json
{
  "scripts": {
    "size": "npx size-limit"
  }
}
```

Install and configure:
```bash
cd fe && npm install --save-dev size-limit @size-limit/esbuild @size-limit/file
```

Add `.size-limit.json`:
```json
[
  { "path": "dist/assets/*.js", "limit": "600 KB", "gzip": true }
]
```

- [ ] **Step 5: Commit**

```bash
git add .github/ fe/package.json fe/.size-limit.json
git commit -m "dx(templates): add PR/issue templates and bundle size limit config"
```

---

## Phase 7 Complete — Verification Checklist

- [ ] Product detail page renders `<meta>` tags for title/description/og:image
- [ ] `grep -rn "Vui long\|Co loi\|Dang tai" fe/src/ --include="*.tsx"` returns 0 (all in i18n)
- [ ] React.StrictMode active (check React DevTools)
- [ ] Suspense fallbacks show skeleton components (not bare text)
- [ ] Screen reader announces cart count changes (test with VoiceOver/NVDA)
- [ ] `make help` shows all available targets
- [ ] `make up` starts full stack
- [ ] `.devcontainer/devcontainer.json` validates (VS Code opens without errors)
- [ ] PR template appears when creating new PR on GitHub
- [ ] `cd fe && npm run size` passes under 600KB budget
