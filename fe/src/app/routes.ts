import { createElement, lazy, Suspense, type ReactNode } from "react";
import { createBrowserRouter } from "react-router";

import { myOrdersOptions } from "./hooks/use-orders";
import { productDetailOptions } from "./hooks/use-products";
import { profileOptions } from "./hooks/use-profile";
import { sellerDetailOptions, sellerProductsOptions } from "./hooks/use-sellers";
import { RequireAuth, RequireRole } from "./lib/auth/role-guard";
import { queryClient } from "./lib/query-client";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { Root } from "./pages/Root";

const SearchPage = lazy(() =>
  import("./pages/SearchPage").then((m) => ({ default: m.SearchPage })),
);
const ProductPage = lazy(() =>
  import("./pages/ProductPage").then((m) => ({ default: m.ProductPage })),
);
const CartPage = lazy(() => import("./pages/CartPage").then((m) => ({ default: m.CartPage })));
const CheckoutPage = lazy(() =>
  import("./pages/checkout").then((m) => ({ default: m.CheckoutPage })),
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
  import("./pages/seller").then((m) => ({ default: m.SellerPage })),
);
const AdminPage = lazy(() => import("./pages/admin").then((m) => ({ default: m.AdminPage })));
const DesignSystemPage = lazy(() =>
  import("./pages/DesignSystemPage").then((m) => ({ default: m.DesignSystemPage })),
);
const PaymentReturnPage = lazy(() =>
  import("./pages/PaymentReturnPage").then((m) => ({ default: m.PaymentReturnPage })),
);
const MessagesPage = lazy(() =>
  import("./pages/MessagesPage").then((m) => ({ default: m.MessagesPage })),
);
const SellerDetailPage = lazy(() =>
  import("./pages/SellerDetailPage").then((m) => ({ default: m.SellerDetailPage })),
);
const PasswordResetPage = lazy(() =>
  import("./pages/PasswordResetPage").then((m) => ({ default: m.PasswordResetPage })),
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
      { path: "product/:id", element: lazyRoute(createElement(ProductPage)), loader: ({ params }) => {
        const id = params.id ?? "";
        // Prefetch in parallel — loader doesn't block render, just primes the cache.
        void queryClient.prefetchQuery(productDetailOptions(id));
        return null;
      }},
      { path: "cart", element: lazyRoute(createElement(CartPage)) },
      { path: "checkout", element: guarded(createElement(CheckoutPage)) },
      { path: "orders", element: guarded(createElement(OrdersPage)), loader: () => {
        void queryClient.prefetchQuery(myOrdersOptions());
        return null;
      }},
      { path: "profile", element: guarded(createElement(ProfilePage)), loader: () => {
        void queryClient.prefetchQuery(profileOptions());
        return null;
      }},
      { path: "wishlist", element: guarded(createElement(WishlistPage)) },
      { path: "login", Component: LoginPage },
      { path: "register", Component: RegisterPage },
      { path: "password-reset", element: lazyRoute(createElement(PasswordResetPage)) },
      { path: "seller/*", element: sellerOnly(createElement(SellerPage)) },
      { path: "admin/*", element: adminOnly(createElement(AdminPage)) },
      { path: "design-system", element: lazyRoute(createElement(DesignSystemPage)) },
      { path: "payment/return/:provider", element: lazyRoute(createElement(PaymentReturnPage)) },
      { path: "messages", element: guarded(createElement(MessagesPage)) },
      { path: "sellers/:id", element: lazyRoute(createElement(SellerDetailPage)), loader: ({ params }) => {
        const id = params.id ?? "";
        void queryClient.prefetchQuery(sellerDetailOptions(id));
        void queryClient.prefetchQuery(sellerProductsOptions(id));
        return null;
      }},
    ],
  },
]);
