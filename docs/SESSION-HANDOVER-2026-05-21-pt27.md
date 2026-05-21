# Session handover — 2026-05-21 (pt27: i18n duplicate-key fix + Tabler icon migration)

**Last commit (HEAD):** `4cc9f886` (`docs(handoff): note live-stack verification of pt26 T1/T8 changes`)
**Uncommitted at session end:** all pt27 work — see "What changed" below.

**Gates (against the rebuilt FE container):**
- FE typecheck: 2 errors (pre-existing PayPalPaymentSection + CheckoutPage; baseline since pt24).
- Vitest: 156 / 156 (25 files).
- Playwright `e2e/day-simulation.spec.ts`: **15 / 15 in 17.4s** against live stack with the new bundle.
- `vnshop-frontend` rebuilt and healthy at `localhost:3000`.

## What changed

### 1. i18n duplicate `home` key
`fe/src/app/lib/i18n/{vi,en}.json` each had **two** top-level `"home": { ... }` blocks. JSON parsers keep the last occurrence, so the second `home` (only `sellersSection`) silently replaced the full `home` namespace at runtime. Result: `home.hero.*`, `home.greetingTitle`, `home.signIn`, `home.tabs.*`, `home.bestsellers`, `home.downloadApp`, etc. all rendered as raw keys.

**Fix.** Merged `sellersSection` into the primary `home` block and deleted the duplicate, in both files.

**Verified.** `vi.home.hero.title` → "Mua hàng chính hãng. Giao nhanh. Sống dễ.", `vi.home.greetingTitle` → "Chào bạn!", `vi.home.sellersSection.title` → "Shop Nổi Bật".

### 2. lucide-react → @tabler/icons-react migration
Codemod-driven swap across 39 FE files / 48 unique icons. Lockfile + package.json now point at `@tabler/icons-react@3.34.0`; `lucide-react` is removed.

**Codemod.** `fe/scripts/migrate-icons.mjs` — Node script that:
- Rewrites `import { ... } from "lucide-react"` → `from "@tabler/icons-react"`.
- Renames identifiers per a 50-entry RENAME table (most prefix `Icon`; non-1:1 names listed below).
- Updates JSX usages of every renamed identifier with word-boundary replace.
- Rewrites Lucide's `stroke="#color"` literal-string prop on `<Icon...>` elements → `color="#color"` because Tabler's `stroke` prop is the *width* number, not the colour.

**Non-1:1 renames worth knowing.**
| Lucide | Tabler |
|---|---|
| `Zap` | `IconBolt` |
| `Trash2` | `IconTrash` |
| `RefreshCw` | `IconRefresh` |
| `BadgeCheck` | `IconRosetteDiscountCheck` (Tabler has no `IconBadgeCheck`) |
| `ImageIcon` / `ImageOff` | `IconPhoto` / `IconPhotoOff` |
| `SlidersHorizontal` | `IconAdjustmentsHorizontal` |
| `Grid3X3` | `IconLayoutGrid` |
| `MessageSquare` | `IconMessage` |
| `Edit3` | `IconEdit` |
| `Save` | `IconDeviceFloppy` |
| `Menu` | `IconMenu2` |
| `Store` | `IconBuildingStore` |
| `Info` | `IconInfoCircle` |
| `XCircle` | `IconCircleX` |
| `CheckCircle` / `CheckCircle2` | `IconCircleCheck` |
| `RotateCcw` | `IconRotate` |
| `LogIn` / `LogOut` | `IconLogin` / `IconLogout` |

**Left alone.** A local helper named `Sparkles` in `pages/DesignSystemPage.tsx` (line 1257) is a custom inline SVG, not a library import — kept as-is.

## Operational gotcha added this block

**54. `git checkout -- <dir>` discards every unstaged edit under that dir, not just the codemod's output.**
Hit this twice. First codemod pass had unmapped identifiers, so I ran `git checkout -- fe/src/app` to roll back. That path also contained the (uncommitted) i18n fix, which got destroyed silently. Same regression appeared on the very next browser screenshot.

**Lesson.** Before `git checkout -- <path>` or `git restore <path>`:
1. `git status -- <path>` and confirm the list is exactly what you expect.
2. If unrelated unstaged edits live there, either `git stash push -- <only the codemod paths>`, restore *only* the codemod-touched files explicitly, or commit the unrelated work first as a checkpoint.

Memory file: `feedback_git_checkout_scope_creep.md`.

## Files touched (pt27, all uncommitted)

```
M fe/package.json                               # +@tabler/icons-react, -lucide-react
M fe/package-lock.json
M fe/src/app/lib/i18n/en.json                   # duplicate home key removed
M fe/src/app/lib/i18n/vi.json                   # duplicate home key removed
M fe/src/app/components/{form-dialog,image-with-fallback,kpi-card,notification-bell,search-autocomplete,seller-product-modal}.tsx
M fe/src/app/components/ui/modal.tsx
M fe/src/app/pages/{CartPage,DesignSystemPage,HomePage,LoginPage,MessagesPage,OrdersPage,PasswordResetPage,PaymentReturnPage,ProductPage,ProfilePage,RegisterPage,Root,SearchPage,SellerDetailPage,WishlistPage}.tsx
M fe/src/app/pages/admin/{AdminDashboard,AdminPage,ReviewsModeration,SellersApproval}.tsx
M fe/src/app/pages/checkout/{CheckoutAddressStep,CheckoutPage,CheckoutPaymentStep,CheckoutReviewStep,CheckoutShippingStep,CheckoutSuccess,CheckoutSummary,types}.tsx
M fe/src/app/pages/seller/{SellerDashboard,SellerOrders,SellerPage,SellerProducts,SellerReviews}.tsx
?? fe/scripts/migrate-icons.mjs                  # the codemod (kept for replay/audit)
```

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should still show `4cc9f886`. The pt27 work is uncommitted — `git status -s` should match the file list above.
2. **Smoke gates.**
   - `cd fe && npm run typecheck` → 2 errors only (baseline).
   - `cd fe && npm test` → 156 / 156.
   - `cd fe && npx playwright test e2e/day-simulation.spec.ts --project=chromium` → 15 / 15 against the live stack.
3. **Container.** `docker compose ps frontend` should show `vnshop-frontend` healthy. The bundle on `localhost:3000` already includes both fixes from this session.
4. **Commit suggestion.** Two logical commits — `fix(fe-i18n): merge duplicate home key` and `refactor(fe): swap lucide-react for @tabler/icons-react`. The codemod script (`fe/scripts/migrate-icons.mjs`) is worth keeping for audit.

## What's still open

Same as pt26: PayPal capture round-trip (manual browser test) and shipping tracking ownership check (deferred with three documented reasons in pt22). Neither is blocked by this session's work.
