/* eslint-disable react-refresh/only-export-components --
 * VNShopProvider keeps the legacy useVNShop hook colocated to avoid touching
 * every importing page during the live-API migration. The shape exposed here is
 * the minimum still consumed by the AI-generated UI; new code should pull from
 * useAuth / useCart / useWishlist directly.
 */
import { createContext, useContext, useCallback, useMemo, useState, type ReactNode } from "react";
import { toast } from "sonner";

import { useAuth } from "../hooks/use-auth";
import { useCart } from "../hooks/use-cart";
import { useWishlist } from "../hooks/use-wishlist";

import type { Product } from "./vnshop-data";

interface User {
  id: string;
  name: string;
  email: string;
  phone: string;
  avatar: string;
  role: "buyer" | "seller" | "admin";
}

interface VNShopContextType {
  // Cart actions still wired through here so the existing product cards keep working.
  cartCount: number;
  addToCart: (product: Product, quantity?: number) => void;
  // Wishlist — backed by /users/me/wishlist (user-service BE-8).
  wishlist: string[];
  toggleWishlist: (productId: string) => void;
  isWishlisted: (productId: string) => boolean;
  // Auth — backed by Keycloak.
  user: User | null;
  isLoggedIn: boolean;
  logout: () => void;
  // Theme.
  isDark: boolean;
  toggleTheme: () => void;
}

const VNShopContext = createContext<VNShopContextType | null>(null);

function pickRole(roles: string[]): "buyer" | "seller" | "admin" {
  if (roles.includes("ADMIN")) return "admin";
  if (roles.includes("SELLER")) return "seller";
  return "buyer";
}

export function VNShopProvider({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const wishlistStore = useWishlist();
  const cart = useCart();
  const [isDark, setIsDark] = useState(false);

  const cartCount = cart.itemCount;

  const addToCart = useCallback(
    (product: Product, quantity = 1) => {
      if (!auth.authenticated) {
        toast.info("Vui lòng đăng nhập để thêm vào giỏ hàng");
        return;
      }
      cart.addItem(
        { productId: product.id, quantity },
        {
          onSuccess: () =>
            toast.success(
              `Đã thêm "${product.name.slice(0, 30)}${product.name.length > 30 ? "..." : ""}" vào giỏ hàng`,
              { description: `Số lượng: ${quantity}` },
            ),
        },
      );
    },
    [auth.authenticated, cart],
  );

  const toggleWishlist = useCallback(
    (productId: string) => {
      const added = wishlistStore.toggle(productId);
      if (added) toast.success("Đã thêm vào danh sách yêu thích ❤️");
      else toast.info("Đã xóa khỏi danh sách yêu thích");
    },
    [wishlistStore],
  );

  const isWishlisted = useCallback(
    (productId: string) => wishlistStore.has(productId),
    [wishlistStore],
  );

  const logout = useCallback(() => {
    toast.info("Đã đăng xuất khỏi tài khoản");
    auth.logout();
  }, [auth]);

  const toggleTheme = useCallback(() => {
    setIsDark((prev) => {
      const next = !prev;
      document.documentElement.classList.toggle("dark", next);
      return next;
    });
  }, []);

  const user = useMemo<User | null>(() => {
    if (!auth.authenticated) return null;
    const profile = auth.profile;
    const fullName =
      profile?.firstName || profile?.lastName
        ? [profile.firstName, profile.lastName].filter(Boolean).join(" ").trim()
        : (profile?.username ?? profile?.email ?? "Người dùng");
    return {
      id: auth.subject ?? profile?.id ?? "",
      name: fullName,
      email: profile?.email ?? "",
      phone:
        (profile?.attributes as Record<string, string[] | undefined> | undefined)?.phone?.[0] ?? "",
      avatar: "",
      role: pickRole(auth.roles),
    };
  }, [auth.authenticated, auth.profile, auth.roles, auth.subject]);

  const value = useMemo<VNShopContextType>(
    () => ({
      cartCount,
      addToCart,
      wishlist: wishlistStore.ids,
      toggleWishlist,
      isWishlisted,
      user,
      isLoggedIn: !!user,
      logout,
      isDark,
      toggleTheme,
    }),
    [
      cartCount,
      addToCart,
      wishlistStore.ids,
      toggleWishlist,
      isWishlisted,
      user,
      logout,
      isDark,
      toggleTheme,
    ],
  );

  return <VNShopContext.Provider value={value}>{children}</VNShopContext.Provider>;
}

export function useVNShop() {
  const ctx = useContext(VNShopContext);
  if (!ctx) throw new Error("useVNShop must be used within VNShopProvider");
  return ctx;
}
