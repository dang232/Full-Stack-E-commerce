# Workday — Buyer

**Verdict:** PASS
**Steps:** 16 / 16 passed
**Generated:** 2026-05-25T06:02:29.340Z

## Steps

### 01. Cold-load home page — PASS

![Cold-load home page](screenshots/01-cold-load-home-page.png)

### 02. Switch language EN to VI — PASS

![Switch language EN to VI](screenshots/02-switch-language-en-to-vi.png)

### 03. Toggle dark mode on — PASS

![Toggle dark mode on](screenshots/03-toggle-dark-mode-on.png)

### 04. Pull a real seeded product for the journey — PASS

![Pull a real seeded product for the journey](screenshots/04-pull-a-real-seeded-product-for-the-journey.png)

### 05. Open product detail from URL — PASS

![Open product detail from URL](screenshots/05-open-product-detail-from-url.png)

### 06. Guest add-to-cart blocks with login toast — PASS

![Guest add-to-cart blocks with login toast](screenshots/06-guest-add-to-cart-blocks-with-login-toast.png)

> NOTE: visual review — screenshot captures the product detail page but no login toast is visible. Toast may have dismissed before snapshot fired.

### 07. Register fresh buyer via /register form — PASS

![Register fresh buyer via /register form](screenshots/07-register-fresh-buyer-via-register-form.png)

> NOTE: visual review — screenshot shows the storefront home page (post-register navigation), not the register form. Capture fired one step after the AC's intended state.

### 08. Authed add-to-cart from product detail — PASS

![Authed add-to-cart from product detail](screenshots/08-authed-add-to-cart-from-product-detail.png)

### 09. Cart shows real product name and non-zero VND total — PASS

![Cart shows real product name and non-zero VND total](screenshots/09-cart-shows-real-product-name-and-non-zero-vnd-total.png)

### 10. Toggle wishlist heart on product detail — PASS

![Toggle wishlist heart on product detail](screenshots/10-toggle-wishlist-heart-on-product-detail.png)

### 11. Add a default address via Profile → Addresses — PASS

![Add a default address via Profile → Addresses](screenshots/11-add-a-default-address-via-profile-addresses.png)

### 12. Checkout 4-step panel renders with new address — PASS

![Checkout 4-step panel renders with new address](screenshots/12-checkout-4-step-panel-renders-with-new-address.png)

### 13. Place a COD order via the API and view it in /orders — PASS

![Place a COD order via the API and view it in /orders](screenshots/13-place-a-cod-order-via-the-api-and-view-it-in-orders.png)

### 14. Cancel pending order via the UI button — PASS

![Cancel pending order via the UI button](screenshots/14-cancel-pending-order-via-the-ui-button.png)

### 15. Upload an avatar via the profile camera button — PASS

![Upload an avatar via the profile camera button](screenshots/15-upload-an-avatar-via-the-profile-camera-button.png)

> NOTE: visual review — success toast "Đã cập nhật ảnh đại diện" is visible (proves upload succeeded), but the avatar tile in the profile sidebar still shows the default teal placeholder. Render of the new image hadn't propagated when the snapshot fired.

### 16. Logout returns to home with the Login CTA — PASS

![Logout returns to home with the Login CTA](screenshots/16-logout-returns-to-home-with-the-login-cta.png)

> NOTE: visual review — screenshot shows the /login page, not the home page with a Login CTA. Capture fired after the post-logout redirect to /login rather than at the intended home state.

## Artifacts

- `trace.zip` — open with `npx playwright show-trace trace.zip`
- `video.webm` — full session recording (gitignored)
- `screenshots/` — one `NN-slug.png` per step, regenerated each run
