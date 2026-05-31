import { IconHeart, IconShoppingCart, IconStar, IconTrash, IconShare, IconFilter } from "@tabler/icons-react";

import { comingSoon } from "../lib/ui/coming-soon";
import { useQueries } from "@tanstack/react-query";
import { motion, AnimatePresence } from "motion/react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { toast } from "sonner";

import { ImageWithFallback } from "../components/image-with-fallback";
import { useAuth } from "../hooks/use-auth";
import { useCart } from "../hooks/use-cart";
import { useWishlist } from "../hooks/use-wishlist";
import { ApiError } from "../lib/api";
import { productById } from "../lib/api/endpoints/products";
import { formatPrice } from "../lib/format";

export function WishlistPage() {
  const navigate = useNavigate();
  const { ready, authenticated } = useAuth();
  const wishlist = useWishlist();
  const { addItem } = useCart();
  const { t } = useTranslation();

  // useWishlist() hydrates from localStorage internally, no effect needed here.

  const queries = useQueries({
    queries: wishlist.ids.map((id) => ({
      queryKey: ["catalog", "products", "detail", id],
      queryFn: () => productById(id),
      retry: false,
      staleTime: 5 * 60_000,
    })),
  });

  const products = useMemo(
    () =>
      queries
        .map((q, i) => ({ id: wishlist.ids[i], data: q.data, error: q.error }))
        .filter((p) => !!p.data),
    [queries, wishlist.ids],
  );

  const handleAddToCart = (productId: string) => {
    if (!authenticated) {
      toast.info(t("wishlist.loginToAdd"));
      return;
    }
    addItem(
      { productId, quantity: 1 },
      {
        onSuccess: () => toast.success(t("wishlist.addedOne")),
        onError: (err) =>
          toast.error(err instanceof ApiError ? err.message : t("wishlist.addError")),
      },
    );
  };

  const handleAddAll = () => {
    if (!authenticated) {
      toast.info(t("wishlist.loginToAdd"));
      return;
    }
    products.forEach((p) => addItem({ productId: p.id, quantity: 1 }));
    toast.success(t("wishlist.addedMany", { count: products.length }));
  };

  if (!ready) return null;

  return (
    <div className="max-w-5xl mx-auto px-4 py-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1
            className="text-2xl font-bold text-foreground"
            style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
          >
            {t("wishlist.pageTitle")}
          </h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            {t("wishlist.countLabel", { count: wishlist.count })}
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => {
              void navigator.clipboard.writeText(window.location.href);
              toast.success("Link copied!");
            }}
            className="p-2.5 rounded-xl border border-border bg-card text-muted-foreground hover:bg-muted"
          >
            <IconShare size={18} />
          </button>
          <button
            onClick={() => comingSoon("Wishlist filtering")}
            className="p-2.5 rounded-xl border border-border bg-card text-muted-foreground hover:bg-muted"
          >
            <IconFilter size={18} />
          </button>
        </div>
      </div>

      {wishlist.count === 0 ? (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="py-24 text-center bg-card rounded-2xl"
        >
          <IconHeart size={64} className="mx-auto mb-5 text-gray-200" />
          <h2 className="text-xl font-bold text-muted-foreground mb-3">{t("wishlist.emptyTitle")}</h2>
          <p className="text-sm text-muted-foreground mb-8 max-w-xs mx-auto">{t("wishlist.emptySub")}</p>
          <button
            onClick={() => navigate("/")}
            className="px-8 py-3 rounded-xl text-white font-semibold shadow-lg hover:opacity-90 transition-opacity"
            style={{ background: "linear-gradient(135deg, #EE4D2D, #FF6633)" }}
          >
            {t("wishlist.discover")}
          </button>
        </motion.div>
      ) : (
        <>
          <div className="flex gap-3 mb-6">
            <button
              onClick={handleAddAll}
              disabled={products.length === 0}
              className="flex items-center gap-2 px-5 py-2.5 rounded-xl text-white font-semibold text-sm shadow disabled:opacity-50"
              style={{ background: "#FF6200" }}
            >
              <IconShoppingCart size={16} /> {t("wishlist.addAll")}
            </button>
            <button
              onClick={() => {
                wishlist.clear();
                toast.info(t("wishlist.cleared"));
              }}
              className="flex items-center gap-2 px-5 py-2.5 rounded-xl font-semibold text-sm border border-border bg-card text-muted-foreground hover:border-red-300 hover:text-red-500 transition-colors"
            >
              <IconTrash size={16} /> {t("wishlist.clearAll")}
            </button>
          </div>

          <AnimatePresence>
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
              {products.map(({ id, data: p }, i) => {
                if (!p) return null;
                const original = p.originalPrice;
                const discount =
                  original && p.price && original > p.price
                    ? Math.round(((original - p.price) / original) * 100)
                    : null;
                return (
                  <motion.div
                    key={id}
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.9 }}
                    transition={{ delay: i * 0.05 }}
                    className="bg-card rounded-2xl overflow-hidden shadow-sm hover:shadow-lg transition-all group"
                  >
                    <div
                      role="button"
                      tabIndex={0}
                      aria-label={t("wishlist.viewAria", { name: p.name })}
                      className="relative overflow-hidden cursor-pointer bg-muted"
                      style={{ aspectRatio: "1" }}
                      onClick={() => navigate(`/product/${id}`)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          e.preventDefault();
                          void navigate(`/product/${id}`);
                        }
                      }}
                    >
                      <ImageWithFallback
                        src={p.image ?? ""}
                        alt={p.name}
                        className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                      />
                      {discount !== null ? (
                        <span
                          className="absolute top-2 left-2 px-2 py-0.5 rounded-full text-white text-[10px] font-bold"
                          style={{ background: "#FF6200" }}
                        >
                          -{discount}%
                        </span>
                      ) : null}
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          wishlist.toggle(id);
                          toast.info(t("wishlist.removed"));
                        }}
                        className="absolute top-2 right-2 w-8 h-8 rounded-full flex items-center justify-center shadow-md bg-card"
                      >
                        <IconHeart size={14} fill="#FF6200" className="text-[#FF6200]" />
                      </button>
                    </div>
                    <div className="p-3">
                      <button
                        onClick={() => navigate(`/product/${id}`)}
                        className="text-sm font-medium text-foreground line-clamp-2 text-left hover:underline"
                      >
                        {p.name}
                      </button>
                      {p.rating !== undefined || p.reviewCount !== undefined ? (
                        <div className="flex items-center gap-1 mt-1 mb-2">
                          <IconStar size={11} fill="#F59E0B" className="text-amber-400" />
                          <span className="text-xs text-muted-foreground">{p.rating ?? 0}</span>
                          <span className="text-xs text-muted-foreground">
                            ({(p.reviewCount ?? 0).toLocaleString()})
                          </span>
                        </div>
                      ) : null}
                      <div className="flex items-center gap-1 mb-3">
                        <span className="font-bold text-sm" style={{ color: "#FF6200" }}>
                          {formatPrice(p.price ?? 0)}
                        </span>
                        {original ? (
                          <span className="text-xs text-muted-foreground line-through">
                            {formatPrice(original)}
                          </span>
                        ) : null}
                      </div>
                      <button
                        onClick={() => handleAddToCart(id)}
                        className="w-full py-2 rounded-xl text-white text-xs font-semibold flex items-center justify-center gap-1.5 hover:opacity-90 transition-opacity"
                        style={{ background: "#EE4D2D" }}
                      >
                        <IconShoppingCart size={13} /> {t("wishlist.addToCart")}
                      </button>
                    </div>
                  </motion.div>
                );
              })}
            </div>
          </AnimatePresence>

          {queries.some((q) => q.isLoading) ? (
            <p className="text-xs text-muted-foreground text-center mt-6">{t("wishlist.loadingItems")}</p>
          ) : null}
          {queries.some((q) => q.error instanceof ApiError && q.error.status === 404) ? (
            <p className="text-xs text-amber-600 text-center mt-6">{t("wishlist.stalePrompt")}</p>
          ) : null}
        </>
      )}
    </div>
  );
}
