import {
  ChevronRight,
  Star,
  Zap,
  Truck,
  Shield,
  RefreshCw,
  Headphones,
  TrendingUp,
  Award,
  Sparkles,
  Gift,
  BadgeCheck,
} from "lucide-react";
import { motion, AnimatePresence } from "motion/react";
import { useState, useMemo, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";

import { ImageWithFallback } from "../components/image-with-fallback";
import { useVNShop } from "../components/vnshop-context";
import { categoryDisplayLabel, useCategories } from "../hooks/use-categories";
import { useCountdown } from "../hooks/use-countdown";
import { useFlashSaleWithProducts, type FlashSaleItem } from "../hooks/use-flash-sale";
import { useProducts } from "../hooks/use-products";
import { formatPrice } from "../lib/format";
import type { Product } from "../types/ui";

// ─── Section Header ────────────────────────────────────────────────────────────
function SectionHeader({
  title,
  subtitle,
  ctaLabel,
  ctaPath,
  accent = "teal",
}: {
  title: string;
  subtitle?: string;
  ctaLabel?: string;
  ctaPath?: string;
  accent?: "teal" | "orange";
}) {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const color = accent === "teal" ? "#00BFB3" : "#FF6200";
  const cta = ctaLabel ?? t("home.viewAll");
  return (
    <div className="flex items-end justify-between mb-6">
      <div>
        <h2
          className="text-2xl md:text-[26px] font-bold tracking-tight text-gray-900 leading-tight"
          style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
        >
          {title}
        </h2>
        {subtitle ? <p className="text-sm text-gray-500 mt-1.5">{subtitle}</p> : null}
      </div>
      {ctaPath ? (
        <button
          onClick={() => navigate(ctaPath)}
          className="group flex items-center gap-1.5 text-sm font-semibold transition-all shrink-0 px-3 py-1.5 rounded-full hover:bg-gray-50"
          style={{ color }}
        >
          {cta}{" "}
          <ChevronRight size={15} className="group-hover:translate-x-0.5 transition-transform" />
        </button>
      ) : null}
    </div>
  );
}

// ─── Product Card ──────────────────────────────────────────────────────────────
function ProductCard({ product, index = 0 }: { product: Product; index?: number }) {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { addToCart, toggleWishlist, isWishlisted } = useVNShop();
  const loved = isWishlisted(product.id);

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: Math.min(index * 0.05, 0.4), duration: 0.3 }}
      className="group rounded-2xl overflow-hidden cursor-pointer bg-white border border-gray-100 hover:border-transparent hover:shadow-[0_20px_40px_-15px_rgba(0,0,0,0.15)] hover:-translate-y-1 transition-all duration-300"
      role="link"
      tabIndex={0}
      aria-label={product.name}
      data-testid="product-card"
      onClick={() => void navigate(`/product/${product.id}`)}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          void navigate(`/product/${product.id}`);
        }
      }}
    >
      <div className="relative overflow-hidden" style={{ aspectRatio: "1" }}>
        <ImageWithFallback
          src={product.image}
          alt={product.name}
          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
        />
        {/* Badges */}
        <div className="absolute top-2.5 left-2.5 flex flex-col gap-1">
          {product.discount ? (
            <span
              className="px-2 py-0.5 rounded-full text-white text-xs font-bold leading-tight"
              style={{ background: "#FF6200" }}
            >
              -{product.discount}%
            </span>
          ) : null}
          {product.badge === "flash" ? (
            <span
              className="flex items-center gap-0.5 px-2 py-0.5 rounded-full text-white text-xs font-bold leading-tight"
              style={{ background: "#E53E3E" }}
            >
              <Zap size={9} fill="white" /> Flash
            </span>
          ) : null}
          {product.badge === "new" ? (
            <span
              className="px-2 py-0.5 rounded-full text-white text-xs font-bold leading-tight"
              style={{ background: "#10B981" }}
            >
              {t("product.new")}
            </span>
          ) : null}
        </div>
        {product.shippingFee === 0 ? (
          <span
            className="absolute top-2.5 right-2.5 flex items-center gap-0.5 px-1.5 py-0.5 rounded-md text-white text-xs font-semibold"
            style={{ background: "rgba(0,0,0,0.45)", backdropFilter: "blur(4px)" }}
          >
            <Truck size={9} /> Free
          </span>
        ) : null}
        {/* Wishlist */}
        <button
          className="absolute bottom-2.5 right-2.5 w-8 h-8 rounded-full flex items-center justify-center shadow-md transition-all duration-200 opacity-0 group-hover:opacity-100 hover:scale-110"
          style={{ background: loved ? "#FF6200" : "rgba(255,255,255,0.95)" }}
          onClick={(e) => {
            e.stopPropagation();
            toggleWishlist(product.id);
          }}
        >
          <svg
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill={loved ? "white" : "none"}
            stroke={loved ? "white" : "#374151"}
            strokeWidth="2"
          >
            <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
          </svg>
        </button>
        {/* Add to cart overlay */}
        <button
          className="absolute bottom-0 left-0 right-0 py-2 text-white text-xs font-semibold translate-y-full group-hover:translate-y-0 transition-transform duration-300"
          style={{ background: "rgba(0,191,179,0.95)", backdropFilter: "blur(4px)" }}
          onClick={(e) => {
            e.stopPropagation();
            addToCart(product);
          }}
        >
          + {t("home.addToCart")}
        </button>
      </div>
      <div className="p-4">
        <p className="text-[11px] uppercase tracking-wider text-gray-400 mb-1 truncate font-medium">
          {product.sellerName}
        </p>
        <h3 className="text-sm font-medium text-gray-900 leading-snug line-clamp-2 mb-3 min-h-[2.5rem]">
          {product.name}
        </h3>
        <div className="flex items-baseline gap-2 mb-2.5">
          <span className="font-bold text-base" style={{ color: "#FF6200" }}>
            {formatPrice(product.price)}
          </span>
          {product.originalPrice ? (
            <span className="text-xs text-gray-400 line-through">
              {formatPrice(product.originalPrice)}
            </span>
          ) : null}
        </div>
        <div className="flex items-center gap-1.5 pt-2.5 border-t border-gray-100">
          <Star size={11} fill="#FF6200" stroke="#FF6200" />
          <span className="text-xs font-semibold text-gray-700">{product.rating}</span>
          <span className="text-xs text-gray-400">
            (
            {product.reviewCount >= 1000
              ? `${(product.reviewCount / 1000).toFixed(1)}k`
              : product.reviewCount}
            )
          </span>
          <span className="text-xs text-gray-400 ml-auto">
            {t("home.soldShort", {
              count: product.sold >= 1000 ? `${(product.sold / 1000).toFixed(0)}k` : product.sold,
            })}
          </span>
        </div>
      </div>
    </motion.div>
  );
}

// ─── Hero Section ─────────────────────────────────────────────────────────────
function ComingSoonCard({
  icon,
  title,
  description,
}: {
  icon: ReactNode;
  title: string;
  description: string;
}) {
  return (
    <div className="rounded-3xl border border-dashed border-gray-200 bg-white px-8 py-12 text-center">
      <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-gray-50 text-gray-400">
        {icon}
      </div>
      <p className="text-base font-semibold text-gray-700">{title}</p>
      <p className="mt-1 text-sm text-gray-500">{description}</p>
    </div>
  );
}

function HeroSection() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { isLoggedIn } = useVNShop();
  return (
    <div
      className="relative overflow-hidden rounded-3xl text-white shadow-[0_20px_60px_-20px_rgba(0,0,0,0.25)]"
      style={{ background: "linear-gradient(135deg, #006B65 0%, #009990 50%, #00BFB3 100%)" }}
    >
      <div
        className="absolute -right-24 -top-24 w-72 h-72 rounded-full opacity-20"
        style={{ background: "rgba(255,255,255,0.5)", filter: "blur(80px)" }}
      />
      <div
        className="absolute -left-24 -bottom-24 w-72 h-72 rounded-full opacity-15"
        style={{ background: "#FF6200", filter: "blur(80px)" }}
      />
      <div className="relative z-10 px-8 md:px-12 py-12 md:py-16 max-w-2xl">
        <span
          className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-full text-xs font-semibold mb-6 tracking-wide bg-white/15 border border-white/25 backdrop-blur-sm"
        >
          <Sparkles size={14} /> {t("home.hero.eyebrow")}
        </span>
        <h1
          className="text-3xl md:text-5xl font-black leading-tight tracking-tight mb-4"
          style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
        >
          {t("home.hero.title")}
        </h1>
        <p className="text-white/80 text-base md:text-lg mb-8 max-w-md font-light">
          {t("home.hero.subtitle")}
        </p>
        <div className="flex gap-3 flex-wrap">
          <button
            onClick={() => navigate("/search")}
            className="px-6 py-3 rounded-2xl font-bold text-sm shadow-lg hover:shadow-xl transition-all hover:-translate-y-0.5 flex items-center gap-2"
            style={{ background: "linear-gradient(135deg, #FF6200 0%, #FF8C00 100%)" }}
          >
            {t("home.hero.ctaShop")} <ChevronRight size={16} />
          </button>
          {!isLoggedIn ? (
            <button
              onClick={() => navigate("/login")}
              className="px-6 py-3 rounded-2xl font-semibold text-sm border border-white/40 hover:bg-white/10 transition-all"
            >
              {t("home.hero.ctaSignIn")}
            </button>
          ) : null}
        </div>
      </div>
    </div>
  );
}

// ─── Promo Strip ──────────────────────────────────────────────────────────────
function PromoStrip() {
  const { t } = useTranslation();
  return (
    <ComingSoonCard
      icon={<Gift size={20} />}
      title={t("home.suggestions")}
      description={t("home.comingSoon.promo")}
    />
  );
}

// ─── Flash Sale ────────────────────────────────────────────────────────────────
function pctOff(originalPrice: number, salePrice: number): number {
  if (originalPrice <= 0 || salePrice >= originalPrice) return 0;
  return Math.round(((originalPrice - salePrice) / originalPrice) * 100);
}

function FlashSaleSection() {
  const { items, isLoading } = useFlashSaleWithProducts();
  const navigate = useNavigate();
  const { t } = useTranslation();

  // Drive the countdown off the earliest active campaign so the chrome
  // disappears as soon as the first sale ends.
  const earliestEnd = useMemo(() => {
    if (items.length === 0) return null;
    const ms = items
      .map((item) => Date.parse(item.campaign.endsAt))
      .filter((n) => Number.isFinite(n));
    return ms.length > 0 ? Math.min(...ms) : null;
  }, [items]);

  const { h, m, s, isExpired } = useCountdown(earliestEnd ?? Date.now());
  const hasCampaigns = items.length > 0 && !isExpired;

  return (
    <section>
      <div
        className="rounded-2xl overflow-hidden"
        style={{ background: "linear-gradient(135deg, #7f0000 0%, #cc0000 35%, #FF6200 100%)" }}
      >
        <div className="px-6 pt-5 pb-4">
          <div className="flex flex-col sm:flex-row sm:items-center gap-4 mb-5">
            <div className="flex items-center gap-3">
              <div
                className="w-11 h-11 rounded-xl flex items-center justify-center"
                style={{ background: "rgba(255,255,255,0.15)" }}
              >
                <Zap size={24} fill="white" className="text-white" />
              </div>
              <div>
                <p
                  className="text-white font-black text-lg leading-tight"
                  style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
                >
                  {t("flashSale.title")}
                </p>
                <p className="text-white/60 text-xs">{t("flashSale.subtitle")}</p>
              </div>
            </div>
            {hasCampaigns ? (
              <div className="sm:ml-auto flex items-center gap-3">
                <span className="text-white/60 text-sm hidden sm:block">
                  {t("flashSale.endsIn")}
                </span>
                <div className="flex items-center gap-1.5">
                  {[
                    { v: h, l: t("flashSale.hours") },
                    { v: m, l: t("flashSale.minutes") },
                    { v: s, l: t("flashSale.seconds") },
                  ].map(({ v, l }, i) => (
                    <div key={l} className="flex items-center gap-1.5">
                      <div className="flex flex-col items-center">
                        <span
                          className="text-white font-black text-xl tabular-nums w-12 h-12 flex items-center justify-center rounded-xl"
                          style={{ background: "rgba(0,0,0,0.3)" }}
                        >
                          {v}
                        </span>
                        <span className="text-white/50 text-[10px] mt-0.5">{l}</span>
                      </div>
                      {i < 2 ? (
                        <span className="text-white/50 font-bold text-lg mb-4">:</span>
                      ) : null}
                    </div>
                  ))}
                </div>
                <button
                  onClick={() => navigate("/search?flash=true")}
                  className="ml-2 px-5 py-2.5 rounded-xl text-sm font-bold bg-white transition-opacity hover:opacity-90"
                  style={{ color: "#E53E3E" }}
                >
                  {t("flashSale.viewAll")}
                </button>
              </div>
            ) : null}
          </div>

          {hasCampaigns ? (
            <FlashSaleStrip items={items} />
          ) : (
            <FlashSaleEmpty isLoading={isLoading} />
          )}
        </div>
      </div>
    </section>
  );
}

function FlashSaleStrip({ items }: { items: FlashSaleItem[] }) {
  const navigate = useNavigate();
  const { t } = useTranslation();
  return (
    <div className="flex gap-3 overflow-x-auto pb-1 -mx-1 px-1 scrollbar-hide">
      {items.map(({ campaign: c, product, isLoading: productLoading, isError }, i) => {
        const discount = pctOff(c.originalPrice, c.salePrice);
        const remaining = c.stockRemaining;
        const soldPct =
          c.stockTotal > 0 && remaining !== null && remaining !== undefined
            ? Math.min(
                100,
                Math.max(0, Math.round(((c.stockTotal - remaining) / c.stockTotal) * 100)),
              )
            : null;
        const firstImage = product?.images?.[0];
        const firstImageUrl = typeof firstImage === "string" ? firstImage : firstImage?.url;
        const imageSrc = product?.image ?? firstImageUrl ?? "";
        const showProductImage = !!product && !productLoading && !isError && !!imageSrc;
        const productName = product?.name;
        return (
          <motion.button
            key={c.id}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.08 }}
            onClick={() => navigate(`/product/${c.productId}`)}
            className="shrink-0 group text-left"
            style={{ width: 130 }}
          >
            <div
              className="rounded-xl overflow-hidden mb-2 relative bg-white/10 flex items-center justify-center"
              style={{ aspectRatio: "1" }}
            >
              {showProductImage ? (
                <ImageWithFallback
                  src={imageSrc}
                  alt={productName ?? c.productId}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                  placeholder={<Zap size={36} className="text-white/70" />}
                />
              ) : (
                <Zap
                  size={36}
                  className="text-white/70 group-hover:scale-110 transition-transform"
                />
              )}
              {discount > 0 ? (
                <div
                  className="absolute bottom-0 left-0 right-0 py-1 text-center"
                  style={{ background: "rgba(229,53,62,0.9)" }}
                >
                  <span className="text-white font-black text-xs">-{discount}%</span>
                </div>
              ) : null}
            </div>
            <p
              className={`text-white text-xs font-medium line-clamp-1 mb-0.5 ${productName ? "" : "font-mono"}`}
              title={productName ?? c.productId}
            >
              {productName ??
                (isError ? `#${c.productId.slice(0, 8)} (${t("flashSale.loadError")})` : `#${c.productId.slice(0, 8)}`)}
            </p>
            <p className="text-yellow-300 font-bold text-sm">{formatPrice(c.salePrice)}</p>
            {c.originalPrice > c.salePrice ? (
              <p className="text-white/40 text-[10px] line-through">
                {formatPrice(c.originalPrice)}
              </p>
            ) : null}
            <div className="mt-1.5">
              <div
                className="h-1.5 rounded-full overflow-hidden"
                style={{ background: "rgba(255,255,255,0.2)" }}
              >
                <div
                  className="h-full rounded-full bg-yellow-300"
                  style={{ width: `${soldPct ?? 0}%` }}
                />
              </div>
              <p className="text-white/50 text-[10px] mt-0.5">
                {soldPct !== null
                  ? t("flashSale.soldPct", { pct: soldPct })
                  : t("flashSale.stockLeft", { n: c.stockTotal })}
              </p>
            </div>
          </motion.button>
        );
      })}
    </div>
  );
}

function FlashSaleEmpty({ isLoading }: { isLoading: boolean }) {
  const { t } = useTranslation();
  return (
    <div className="flex items-center justify-center py-10 text-center">
      <div>
        <p className="text-white font-bold text-base mb-1">
          {isLoading ? t("flashSale.loading") : t("flashSale.comingSoon")}
        </p>
        <p className="text-white/60 text-xs">
          {isLoading ? t("flashSale.loadingWait") : t("flashSale.expired")}
        </p>
      </div>
    </div>
  );
}

// ─── Categories ────────────────────────────────────────────────────────────────
function CategoriesSection() {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { data: categories = [], isLoading } = useCategories();
  return (
    <section>
      <SectionHeader
        title={t("home.categories")}
        ctaLabel={t("home.allCategoriesLabel")}
        ctaPath="/search"
      />
      {isLoading ? (
        <div className="grid grid-cols-4 md:grid-cols-8 gap-3">
          {Array.from({ length: 8 }).map((_, i) => (
            <div
              // eslint-disable-next-line react/no-array-index-key -- skeleton placeholder, no stable id
              key={i}
              className="h-24 rounded-2xl bg-gray-50 animate-pulse"
            />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-4 md:grid-cols-8 gap-3">
          {categories.map((cat, i) => (
            <motion.button
              key={cat.id}
              initial={{ opacity: 0, scale: 0.88 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ delay: i * 0.04, duration: 0.25 }}
              whileHover={{ y: -4, scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={() => navigate(`/search?cat=${cat.id}`)}
              className="flex flex-col items-center gap-2.5 py-5 px-2 rounded-2xl bg-white border border-gray-100 hover:border-transparent hover:shadow-[0_12px_28px_-12px_rgba(0,0,0,0.18)] transition-all cursor-pointer"
            >
              <span className="text-[13px] font-semibold text-gray-800 text-center leading-tight">
                {categoryDisplayLabel(cat)}
              </span>
            </motion.button>
          ))}
        </div>
      )}
    </section>
  );
}

// ─── Trust Bar ────────────────────────────────────────────────────────────────
function TrustBar() {
  const { t } = useTranslation();
  const items = [
    { icon: Truck, textKey: "trust.freeShipping", subKey: "trust.freeShippingSub", color: "#00BFB3" },
    { icon: Shield, textKey: "trust.authentic", subKey: "trust.authenticSub", color: "#3B82F6" },
    { icon: RefreshCw, textKey: "trust.returns", subKey: "trust.returnsSub", color: "#10B981" },
    { icon: Headphones, textKey: "trust.support247", subKey: "trust.support247Sub", color: "#F59E0B" },
  ];
  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-px bg-gray-100 rounded-2xl overflow-hidden border border-gray-100">
      {items.map((item) => (
        <div
          key={item.textKey}
          className="flex items-center gap-3.5 p-5 bg-white hover:bg-gray-50/50 transition-colors"
        >
          <div
            className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
            style={{ background: `${item.color}14` }}
          >
            <item.icon size={20} style={{ color: item.color }} strokeWidth={2.2} />
          </div>
          <div>
            <p className="text-sm font-semibold text-gray-900 leading-tight">{t(item.textKey)}</p>
            <p className="text-xs text-gray-500 mt-1">{t(item.subKey)}</p>
          </div>
        </div>
      ))}
    </div>
  );
}

// ─── Promo Banners ────────────────────────────────────────────────────────────
function PromoBanners() {
  const { t } = useTranslation();
  return (
    <ComingSoonCard
      icon={<BadgeCheck size={20} />}
      title={t("home.bestsellers")}
      description={t("home.comingSoon.promo")}
    />
  );
}

// ─── Seller Showcase ──────────────────────────────────────────────────────────
function SellerShowcase() {
  const { t } = useTranslation();
  return (
    <ComingSoonCard
      icon={<Award size={20} />}
      title={t("home.trustedSellers")}
      description={t("home.comingSoon.sellers")}
    />
  );
}

// ─── Trending ─────────────────────────────────────────────────────────────────
function TrendingBar() {
  const { t } = useTranslation();
  return (
    <div className="flex items-center gap-3 p-4 bg-white rounded-2xl border border-dashed border-gray-200">
      <div className="flex items-center gap-2 shrink-0 pr-3 border-r border-gray-100">
        <TrendingUp size={16} style={{ color: "#FF6200" }} />
        <span className="text-sm font-semibold text-gray-700 whitespace-nowrap">
          {t("home.trending")}
        </span>
      </div>
      <span className="text-sm text-gray-500">{t("home.comingSoon.trending")}</span>
    </div>
  );
}

// ─── Products Section ─────────────────────────────────────────────────────────
function ProductsSection() {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState("all");
  const { data: catalog = [] as Product[] } = useProducts();
  const tabs = [
    { id: "all", labelKey: "home.tabs.all", emoji: "✨" },
    { id: "electronics", labelKey: "home.tabs.electronics", emoji: "📱" },
    { id: "fashion", labelKey: "home.tabs.fashion", emoji: "👗" },
    { id: "beauty", labelKey: "home.tabs.beauty", emoji: "💄" },
    { id: "sports", labelKey: "home.tabs.sports", emoji: "⚽" },
  ];
  const filtered = useMemo(
    () => (activeTab === "all" ? catalog : catalog.filter((p) => p.category === activeTab)),
    [activeTab, catalog],
  );

  return (
    <section>
      <div className="flex items-end justify-between mb-6">
        <div>
          <div className="flex items-center gap-2 mb-1.5">
            <Sparkles size={18} style={{ color: "#00BFB3" }} />
            <span
              className="text-xs font-bold uppercase tracking-widest"
              style={{ color: "#00BFB3" }}
            >
              {t("home.forYou")}
            </span>
          </div>
          <h2
            className="text-2xl md:text-[26px] font-bold tracking-tight text-gray-900 leading-tight"
            style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
          >
            {t("home.suggestions")}
          </h2>
          <p className="text-sm text-gray-500 mt-1.5">{t("home.suggestionsSubtitle")}</p>
        </div>
        <button
          onClick={() => navigate("/search")}
          className="group flex items-center gap-1.5 text-sm font-semibold transition-all px-3 py-1.5 rounded-full hover:bg-gray-50"
          style={{ color: "#00BFB3" }}
        >
          {t("home.viewAll")}{" "}
          <ChevronRight size={15} className="group-hover:translate-x-0.5 transition-transform" />
        </button>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 mb-6 overflow-x-auto pb-1 scrollbar-hide">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className="shrink-0 flex items-center gap-1.5 px-4 py-2 rounded-full text-sm font-medium transition-all duration-200"
            style={
              activeTab === tab.id
                ? {
                    background: "#00BFB3",
                    color: "#fff",
                    boxShadow: "0 4px 12px rgba(0,191,179,0.35)",
                  }
                : { background: "#fff", color: "#6b7280", border: "1px solid #e5e7eb" }
            }
          >
            <span>{tab.emoji}</span> {t(tab.labelKey)}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-5 gap-4">
        <AnimatePresence mode="popLayout">
          {filtered.slice(0, 20).map((p, i) => (
            <ProductCard key={p.id} product={p} index={i} />
          ))}
        </AnimatePresence>
      </div>

      <div className="mt-8 text-center">
        <button
          onClick={() => navigate("/search")}
          className="px-8 py-3 rounded-full font-semibold text-sm border-2 transition-all hover:text-white hover:shadow-lg"
          style={{ borderColor: "#00BFB3", color: "#00BFB3" }}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = "#00BFB3";
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = "transparent";
          }}
        >
          {t("home.viewMore")} →
        </button>
      </div>
    </section>
  );
}

// ─── Bestsellers Sidebar ──────────────────────────────────────────────────────
function Bestsellers() {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { data: catalog = [] as Product[] } = useProducts();
  const items = useMemo(() => [...catalog].sort((a, b) => b.sold - a.sold).slice(0, 5), [catalog]);
  return (
    <section>
      <SectionHeader title={t("home.bestsellers")} accent="orange" />
      <div className="space-y-2.5">
        {items.map((p, i) => (
          <motion.div
            key={p.id}
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: i * 0.07 }}
            className="flex items-center gap-3 p-3 bg-white rounded-xl border border-gray-100 hover:shadow-md cursor-pointer group transition-all"
            onClick={() => navigate(`/product/${p.id}`)}
          >
            <span
              className="text-base font-black w-7 text-center shrink-0"
              style={{
                color: i < 3 ? "#FF6200" : "#D1D5DB",
                fontFamily: "'Be Vietnam Pro', sans-serif",
              }}
            >
              {i + 1}
            </span>
            <ImageWithFallback
              src={p.image}
              alt={p.name}
              className="w-12 h-12 rounded-lg object-cover shrink-0"
            />
            <div className="flex-1 min-w-0">
              <p className="text-xs font-medium text-gray-800 line-clamp-2 leading-tight group-hover:text-[#00BFB3] transition-colors">
                {p.name}
              </p>
              <p className="text-xs text-gray-400 mt-0.5">
                {t("home.soldShort", { count: p.sold.toLocaleString() })}
              </p>
            </div>
            <div className="shrink-0 text-right">
              <p className="text-xs font-bold" style={{ color: "#FF6200" }}>
                {formatPrice(p.price)}
              </p>
              {p.discount ? (
                <span
                  className="text-[10px] font-bold px-1 py-0.5 rounded"
                  style={{ background: "#FFF2EA", color: "#FF6200" }}
                >
                  -{p.discount}%
                </span>
              ) : null}
            </div>
          </motion.div>
        ))}
      </div>
    </section>
  );
}

// ─── App Download Banner ──────────────────────────────────────────────────────
function AppBanner() {
  const { t } = useTranslation();
  return (
    <div
      className="rounded-2xl overflow-hidden relative"
      style={{ background: "linear-gradient(135deg, #00BFB3 0%, #009990 100%)", minHeight: 120 }}
    >
      <div
        className="absolute right-0 top-0 bottom-0 w-64 opacity-10"
        style={{
          backgroundImage:
            "url(https://images.unsplash.com/photo-1512941937669-90a1b58e7e9c?w=300&q=50)",
          backgroundSize: "cover",
        }}
      />
      <div className="relative z-10 px-8 py-6 flex flex-col sm:flex-row items-start sm:items-center gap-5">
        <div className="w-12 h-12 rounded-2xl bg-white/20 flex items-center justify-center shrink-0">
          <span className="text-2xl">📱</span>
        </div>
        <div className="flex-1">
          <h3
            className="font-black text-white text-lg mb-0.5"
            style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
          >
            {t("home.downloadApp")}
          </h3>
          <p className="text-white/75 text-sm">{t("home.downloadAppSub")}</p>
        </div>
        <div className="flex gap-2.5 shrink-0">
          <button
            className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold bg-white hover:opacity-90 transition-opacity"
            style={{ color: "#009990" }}
          >
            <span>🍎</span> {t("home.appStore")}
          </button>
          <button
            className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold bg-white hover:opacity-90 transition-opacity"
            style={{ color: "#009990" }}
          >
            <span>🤖</span> {t("home.googlePlay")}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── User Sidebar Widget ──────────────────────────────────────────────────────
function UserWidget() {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { user, isLoggedIn, cartCount, wishlist } = useVNShop();
  return (
    <div className="space-y-4">
      {/* Account card */}
      <div className="bg-white rounded-2xl p-5 border border-gray-100 shadow-sm">
        {isLoggedIn && user ? (
          <>
            <div className="flex items-center gap-3 mb-4">
              <div
                className="w-11 h-11 rounded-xl flex items-center justify-center text-white font-bold"
                style={{ background: "linear-gradient(135deg, #00BFB3, #009990)" }}
              >
                {user.name.charAt(0)}
              </div>
              <div>
                <p className="font-semibold text-sm text-gray-900">{user.name}</p>
                <p className="text-xs text-gray-400">{user.email}</p>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-2">
              {[
                { label: t("home.userStats.cart"), value: cartCount, color: "#00BFB3", path: "/cart" },
                { label: t("home.userStats.wishlist"), value: wishlist.length, color: "#FF6200", path: "/wishlist" },
                { label: t("home.userStats.orders"), value: "—", color: "#3B82F6", path: "/orders" },
                { label: t("home.userStats.vouchers"), value: "—", color: "#10B981", path: "/profile" },
              ].map((item) => (
                <button
                  key={item.label}
                  onClick={() => navigate(item.path)}
                  className="text-center p-2.5 rounded-xl hover:opacity-80 transition-opacity"
                  style={{ background: `${item.color}10` }}
                >
                  <p className="font-bold text-base" style={{ color: item.color }}>
                    {item.value}
                  </p>
                  <p className="text-xs text-gray-500 mt-0.5">{item.label}</p>
                </button>
              ))}
            </div>
          </>
        ) : (
          <div className="text-center py-2">
            <div
              className="w-14 h-14 rounded-2xl mx-auto mb-3 flex items-center justify-center"
              style={{ background: "#E6FAF9" }}
            >
              <span className="text-3xl">👋</span>
            </div>
            <p className="font-semibold text-gray-800 mb-1">{t("home.greetingTitle")}</p>
            <p className="text-xs text-gray-500 mb-4">{t("home.greetingSubtitle")}</p>
            <div className="flex gap-2">
              <button
                onClick={() => navigate("/login")}
                className="flex-1 py-2 rounded-xl text-sm font-semibold text-white"
                style={{ background: "#00BFB3" }}
              >
                {t("home.signIn")}
              </button>
              <button
                onClick={() => navigate("/login")}
                className="flex-1 py-2 rounded-xl text-sm font-semibold border"
                style={{ color: "#00BFB3", borderColor: "#00BFB3" }}
              >
                {t("home.signUp")}
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Voucher */}
      <div
        className="rounded-2xl p-4 text-white relative overflow-hidden"
        style={{ background: "linear-gradient(135deg, #FF6200, #FF8C00)" }}
      >
        <div
          className="absolute -right-4 -top-4 w-24 h-24 rounded-full opacity-15"
          style={{ background: "rgba(255,255,255,0.4)" }}
        />
        <div className="flex items-center gap-2 mb-2">
          <Gift size={16} className="text-white" />
          <p className="font-bold text-sm">{t("home.voucherToday")}</p>
        </div>
        <p className="text-white/70 text-xs mb-3">{t("home.voucherSub")}</p>
        <div className="bg-white/20 rounded-xl px-3 py-2 text-center border border-white/20">
          <span className="font-black text-xl tracking-[0.2em]">VNSHOP50</span>
        </div>
        <p className="text-white/50 text-xs mt-2 text-center">{t("home.copyHint")}</p>
      </div>

      {/* Mini bestsellers */}
      <Bestsellers />
    </div>
  );
}

// ─── Homepage ─────────────────────────────────────────────────────────────────
export function HomePage() {
  return (
    <div className="min-h-screen" style={{ background: "#fafbfc" }}>
      <div className="max-w-7xl mx-auto px-4 md:px-6 py-6 md:py-8 space-y-12 md:space-y-16">
        {/* Hero */}
        <div className="grid lg:grid-cols-[1fr_300px] gap-6">
          <HeroSection />
          <UserWidget />
        </div>

        {/* Trust bar — single row, refined */}
        <TrustBar />

        {/* Flash Sale */}
        <FlashSaleSection />

        {/* Categories */}
        <CategoriesSection />

        {/* Trending bar */}
        <TrendingBar />

        {/* Promo banners */}
        <PromoBanners />

        {/* Sellers */}
        <SellerShowcase />

        {/* Quick promo strip */}
        <PromoStrip />

        {/* Products */}
        <ProductsSection />

        {/* App download */}
        <AppBanner />
      </div>
    </div>
  );
}
