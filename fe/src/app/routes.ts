import { createElement, lazy, Suspense, type ReactNode } from "react";
import { createBrowserRouter } from "react-router";

import { RequireAuth, RequireRole } from "./lib/auth/role-guard";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./pages/LoginPage";
import { Root } from "./pages/Root";

const SearchPage = lazy(() =>
  import("./pages/SearchPage").then((m) => ({ default: m.SearchPage })),
);
const ProductPage = lazy(() =>
  import("./pages/ProductPage").then((m) => ({ default: m.ProductPage })),
);
const CartPage = lazy(() => import("./pages/CartPage").then((m) => ({ default: m.CartPage })));
const CheckoutPage = lazy(() =>
  import("./pages/CheckoutPage").then((m) => ({ default: m.CheckoutPage })),
);
const OrdersPage = lazy(() =>
  import("./pages/OrdersPage").then((m) => ({ default: m.OrdersPage })),
);
const ProfilePage = lazy(() =>
  import("./pages/ProfilePage").then((m) => ({ default: m.ProfilePage })),
);
const WishlistPage = lazy(() =>
  import("./pages/WishlistPage").then((m) => ({ default: m.WishlistPage })),
);
const SellerPage = lazy(() =>
  import("./pages/SellerPage").then((m) => ({ default: m.SellerPage })),
);
const AdminPage = lazy(() => import("./pages/AdminPage").then((m) => ({ default: m.AdminPage })));
const DesignSystemPage = lazy(() =>
  import("./pages/DesignSystemPage").then((m) => ({ default: m.DesignSystemPage })),
);
const PaymentReturnPage = lazy(() =>
  import("./pages/PaymentReturnPage").then((m) => ({ default: m.PaymentReturnPage })),
);

const Fallback = () =>
  createElement(
    "div",
    { className: "max-w-7xl mx-auto px-4 py-24 text-center text-sm text-gray-500" },
    "Đang tải...",
  );

/* eslint-disable react/no-children-prop -- createElement passes children via props by design */
const lazyRoute = (el: ReactNode) =>
  createElement(Suspense, { fallback: createElement(Fallback) }, el);
const guarded = (el: ReactNode) => createElement(RequireAuth, { children: lazyRoute(el) });
const sellerOnly = (el: ReactNode) =>
  createElement(RequireRole, { role: "SELLER", children: lazyRoute(el) });
const adminOnly = (el: ReactNode) =>
  createElement(RequireRole, { role: "ADMIN", children: lazyRoute(el) });
/* eslint-enable react/no-children-prop */

export const router = createBrowserRouter([
  {
    path: "/",
    Component: Root,
    children: [
      { index: true, Component: HomePage },
      { path: "search", element: lazyRoute(createElement(SearchPage)) },
      { path: "product/:id", element: lazyRoute(createElement(ProductPage)) },
      { path: "cart", element: lazyRoute(createElement(CartPage)) },
      { path: "checkout", element: guarded(createElement(CheckoutPage)) },
      { path: "orders", element: guarded(createElement(OrdersPage)) },
      { path: "profile", element: guarded(createElement(ProfilePage)) },
      { path: "wishlist", element: guarded(createElement(WishlistPage)) },
      { path: "login", Component: LoginPage },
      { path: "seller/*", element: sellerOnly(createElement(SellerPage)) },
      { path: "admin/*", element: adminOnly(createElement(AdminPage)) },
      { path: "design-system", element: lazyRoute(createElement(DesignSystemPage)) },
      { path: "payment/return/:provider", element: lazyRoute(createElement(PaymentReturnPage)) },
    ],
  },
]);
