# VNShop Marketplace — Frontend Visual Redesign

**Date:** 2026-06-06  
**Scope:** Full visual overhaul of React frontend  
**Stack:** React 18 + TypeScript + Vite + Tailwind CSS 4 + Lucide Icons

---

## Design Decisions

| Aspect | Choice | Reference |
|--------|--------|-----------|
| Direction | Clean & Trustworthy marketplace | Mercari, modern Poshmark |
| Primary | Deep Indigo `#4F46E5` | Linear, Stripe |
| Sales Accent | Amber Orange `#F59E0B` | Deals, flash sales, urgency |
| Typography | Inter (single font, all weights) | GitHub, Linear |
| Dark Mode | Slate Navy `#0F172A → #1E293B` | GitHub, Discord |
| Icons | Lucide React | Consistent stroke-based icons |
| Cards | Rounded (`14px`), bordered, elevated on hover | |
| Layout | Generous padding, 5-col product grid | |

---

## Color Palette

### Light Mode (Default)
```
--bg-primary:      #ffffff
--bg-secondary:    #f8fafc
--bg-card:         #ffffff
--bg-elevated:     #f1f5f9
--border:          #e2e8f0
--border-hover:    #cbd5e1
--text-primary:    #0f172a
--text-secondary:  #64748b
--text-muted:      #94a3b8
--primary:         #4f46e5
--primary-light:   #eef2ff
--primary-hover:   #4338ca
--accent:          #f59e0b
--accent-light:    #fffbeb
--success:         #10b981
--error:           #ef4444
```

### Dark Mode (Slate Navy)
```
--bg-primary:      #0f172a
--bg-secondary:    #020617
--bg-card:         #1e293b
--bg-elevated:     #334155
--border:          #334155
--border-hover:    #475569
--text-primary:    #f8fafc
--text-secondary:  #94a3b8
--text-muted:      #64748b
--primary:         #818cf8
--primary-light:   rgba(99, 102, 241, 0.1)
--primary-hover:   #a5b4fc
--accent:          #fbbf24
--accent-light:    rgba(251, 191, 36, 0.1)
--success:         #34d399
--error:           #f87171
```

---

## Typography Scale

- Font: Inter (400, 500, 600, 700, 800)
- Base: 14px body
- Headings: 32px (h1), 24px (h2), 20px (h3), 16px (h4)
- Labels/badges: 11-12px, letter-spacing 0.5px, uppercase
- Line height: 1.5 body, 1.2 headings

---

## Spacing & Radius

- Radius: sm=6px, md=10px, lg=14px, xl=18px, full=9999px
- Shadows: sm (0 1px 2px 0.04), md (0 4px 12px 0.06), lg (0 8px 24px 0.08)
- Transitions: fast=150ms, normal=200ms, slow=300ms

---

## Component Design Language

### Product Card
- Rounded-lg border, 1px border-color
- Hover: border-hover, shadow-md, translateY(-2px)
- Image: aspect-ratio 1:1, bg-elevated placeholder
- Wishlist heart: top-right, appears on hover
- Badge: top-left (sale=amber, new=primary)
- Price: primary color, original strikethrough in muted
- Rating: filled amber star + number
- Meta: "· X sold" in muted

### Navigation
- Sticky, white bg, border-bottom
- Logo: font-weight-800, primary color
- Search: rounded-lg input, border focus → primary ring
- Actions: icon buttons (bell, heart, bag), primary Sign In CTA
- Cart badge: amber circle with count

### Categories
- Pill navigation bar under nav
- Active pill: primary bg + white text
- Category grid: icon + label cards, hover → primary border + primary-light bg

### Hero Banner
- Rounded-xl, gradient (indigo → violet)
- Badge with backdrop-blur
- White CTA button with shadow

### Flash Sale
- Rounded-xl card, bordered
- Amber timer boxes
- Zap icon in amber-light box
- Product row (5-col grid)

### Trust Bar
- 4-col grid, bordered cards
- Icon in primary-light box
- Title + subtitle

### Buttons
- Primary: bg-primary, white text, rounded-md
- Secondary: bg-primary-light, primary text
- Ghost: transparent, text-secondary, hover bg-secondary

---

## Pages to Redesign

1. **HomePage** — hero, categories, flash sale, products, trust bar
2. **SearchPage** — filters sidebar, product grid, sort controls
3. **ProductPage** — gallery, info panel, reviews, related products
4. **CartPage** — item list, summary sidebar, coupon input
5. **CheckoutPage** — steps UI, address/payment/review
6. **OrdersPage** — order cards, status badges
7. **SellerDashboard** — stats cards, charts, order table
8. **AdminDashboard** — approval queues, analytics

---

## Implementation Strategy

1. Replace CSS variables in `globals.css` / theme
2. Install `lucide-react`, remove emoji icons
3. Update `tailwind.config` with new design tokens
4. Redesign shared components (nav, footer, cards, buttons)
5. Update each page to match new design language
6. Add dark mode variant tokens
7. Verify `tsc --noEmit` + `npm run build` pass

---

## Icon Library

Replace all emoji and inline SVG with `lucide-react`:
- Nav: Search, Bell, Heart, ShoppingBag, User, Menu
- Categories: Smartphone, Shirt, Sofa, Code, Sparkles, Dumbbell, Book, Car, Download, UtensilsCrossed
- Products: Star (filled for ratings), Heart (wishlist), Zap (flash)
- Trust: Truck, ShieldCheck, BadgeCheck, Lock
- Actions: Plus, Minus, Trash2, ChevronRight, ArrowRight, X
