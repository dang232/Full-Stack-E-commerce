import { IconStar, IconTruck, IconShield, IconRefresh, IconChevronRight, IconChevronLeft, IconHeart, IconShare, IconBuildingStore, IconMessage, IconThumbUp } from "@tabler/icons-react";
import { useMutation, useQuery, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { motion, AnimatePresence } from "motion/react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams, useNavigate, Link } from "react-router";
import { toast } from "sonner";

import { ImageWithFallback } from "../components/image-with-fallback";
import { useVNShop } from "../components/vnshop-context";
import { useAuth } from "../hooks/use-auth";
import { useProductReviews } from "../hooks/use-product-reviews";
import { productDetailOptions } from "../hooks/use-products";
import { useFrequentlyBoughtTogether, useYouMayAlsoLike } from "../hooks/use-recommendations";
import { useSellerDetail } from "../hooks/use-sellers";
import { ApiError } from "../lib/api";
import { askQuestion, questionsByProduct } from "../lib/api/endpoints/questions";
import type { RecommendationItem } from "../lib/api/endpoints/recommendations";
import { createReview, voteReviewHelpful } from "../lib/api/endpoints/reviews";
import { formatPrice } from "../lib/format";

function StarRating({ value, max = 5, size = 16 }: { value: number; max?: number; size?: number }) {
  return (
    <div className="flex items-center gap-0.5">
      {Array.from({ length: max }).map((_, i) => {
        const filled = i < Math.floor(value);
        const half = !filled && i < value;
        return (
          // eslint-disable-next-line react/no-array-index-key -- decorative star rating, no stable id
          <svg key={i} width={size} height={size} viewBox="0 0 24 24">
            <defs>
              <linearGradient id={`half-${i}`} x1="0%" y1="0%" x2="100%" y2="0%">
                <stop offset="50%" stopColor="#F59E0B" />
                <stop offset="50%" stopColor="#E5E7EB" />
              </linearGradient>
            </defs>
            <path
              d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"
              fill={filled ? "#F59E0B" : half ? `url(#half-${i})` : "#E5E7EB"}
            />
          </svg>
        );
      })}
    </div>
  );
}

function SellerCard({ sellerId }: { sellerId?: string }) {
  const { t } = useTranslation();

  const sellerQuery = useSellerDetail(sellerId);

  if (!sellerId) return null;

  if (sellerQuery.isLoading) {
    return (
      <div className="mt-8 bg-card rounded-2xl p-5 shadow-sm animate-pulse">
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 rounded-xl bg-gray-200" />
          <div className="flex-1 space-y-2">
            <div className="h-4 bg-gray-200 rounded w-32" />
            <div className="h-3 bg-muted rounded w-24" />
          </div>
        </div>
      </div>
    );
  }

  if (sellerQuery.isError || !sellerQuery.data) {
    return (
      <div className="mt-8 bg-card rounded-2xl p-5 shadow-sm">
        <div className="flex items-center gap-3 text-sm text-muted-foreground">
          <IconBuildingStore size={18} className="text-gray-300" />
          <span>{t("product.seller.comingSoon")}</span>
        </div>
      </div>
    );
  }

  const seller = sellerQuery.data;
  const initial = seller.shopName.charAt(0).toUpperCase();

  return (
    <div className="mt-8 bg-card rounded-2xl p-5 shadow-sm">
      <div className="flex items-center gap-4">
        <div className="w-12 h-12 rounded-xl overflow-hidden bg-muted flex items-center justify-center shrink-0">
          {seller.logoUrl ? (
            <img src={seller.logoUrl} alt={seller.shopName} className="w-full h-full object-cover" />
          ) : (
            <span
              className="text-lg font-black text-white w-full h-full flex items-center justify-center"
              style={{ background: "#00BFB3" }}
            >
              {initial}
            </span>
          )}
        </div>
        <div className="flex-1 min-w-0">
          <p className="font-semibold text-foreground truncate">{seller.shopName}</p>
          <div className="flex items-center gap-3 mt-0.5 text-xs text-muted-foreground flex-wrap">
            {seller.ratingAvg !== null && seller.ratingAvg !== undefined ? (
              <span className="flex items-center gap-1">
                <IconStar size={11} fill="#FF6200" color="#FF6200" />
                <span className="font-semibold text-foreground">{seller.ratingAvg.toFixed(1)}</span>
              </span>
            ) : null}
            <span>{t("product.seller.products", { count: seller.totalProducts })}</span>
          </div>
        </div>
        <Link
          to={`/sellers/${seller.id}`}
          className="shrink-0 px-4 py-2 rounded-xl text-sm font-semibold border transition-all hover:bg-muted"
          style={{ borderColor: "#00BFB3", color: "#00BFB3" }}
        >
          {t("sellerDetail.visitShop")}
        </Link>
      </div>
    </div>
  );
}

export function ProductPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { addToCart, toggleWishlist, isWishlisted } = useVNShop();
  const { t } = useTranslation();

  const { data: product } = useSuspenseQuery(productDetailOptions(id ?? ""));
  // Recommendations come from the recommendations-service (BE) — see
  // services/recommendations-service. The previous incarnation filtered the
  // full catalog by category client-side (`useProducts()` -> `.filter(...)`)
  // which neither scaled nor reflected real co-purchase signal.
  const fbtQuery = useFrequentlyBoughtTogether(id);
  const ymalQuery = useYouMayAlsoLike(id);
  const { authenticated, login } = useAuth();
  const qc = useQueryClient();

  const liveReviewsQuery = useProductReviews(id ?? "");

  const liveQuestionsQuery = useQuery({
    queryKey: ["questions", "product", id],
    queryFn: () => questionsByProduct(id!),
    enabled: !!id,
    retry: false,
  });

  const submitReview = useMutation({
    mutationFn: (input: { rating: number; comment: string }) =>
      createReview({ productId: id!, ...input }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["catalog", "reviews", "product", id] });
      toast.success(t("product.reviews.submitOk"));
      setReviewDraft({ rating: 5, comment: "" });
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("product.reviews.submitErr")),
  });

  const voteHelpful = useMutation({
    mutationFn: (reviewId: string) => voteReviewHelpful(reviewId),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["catalog", "reviews", "product", id] }),
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("product.reviews.voteErr")),
  });

  const submitQuestion = useMutation({
    mutationFn: (question: string) => askQuestion({ productId: id!, question }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["questions", "product", id] });
      toast.success(t("product.qa.submitOk"));
      setQuestionDraft("");
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("product.qa.submitErr")),
  });

  const [reviewDraft, setReviewDraft] = useState({ rating: 5, comment: "" });
  const [questionDraft, setQuestionDraft] = useState("");

  const [imageIdx, setImageIdx] = useState(0);
  const [selectedColor, setSelectedColor] = useState(product.colors?.[0] ?? "");
  const [selectedSize, setSelectedSize] = useState(product.sizes?.[0] ?? "");
  const [quantity, setQuantity] = useState(1);
  const [activeTab, setActiveTab] = useState<"desc" | "reviews" | "qa">("desc");
  const loved = isWishlisted(product.id);

  const variant = { color: selectedColor || undefined, size: selectedSize || undefined };
  const handleAddToCart = () => addToCart(product, quantity, variant);
  const handleBuyNow = () => {
    addToCart(product, quantity, variant);
    void navigate("/checkout");
  };

  const images = product.images.length > 0 ? product.images : [product.image];
  const savings = product.originalPrice ? product.originalPrice - product.price : 0;

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-sm text-muted-foreground mb-6">
        <button onClick={() => navigate("/")} className="hover:text-foreground">
          {t("product.breadcrumbHome")}
        </button>
        <IconChevronRight size={14} />
        <button
          onClick={() => navigate(`/search?cat=${product.category}`)}
          className="hover:text-foreground"
        >
          {product.categoryLabel}
        </button>
        <IconChevronRight size={14} />
        <span className="text-foreground truncate max-w-xs">{product.name}</span>
      </nav>

      <div className="grid lg:grid-cols-[500px_1fr] gap-8">
        {/* Image Gallery */}
        <div className="space-y-3">
          <div
            className="relative bg-card rounded-2xl overflow-hidden shadow-sm"
            style={{ aspectRatio: "1" }}
          >
            <AnimatePresence mode="wait">
              <motion.img
                key={imageIdx}
                src={images[imageIdx]}
                alt={product.name}
                className="w-full h-full object-cover"
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -20 }}
                transition={{ duration: 0.25 }}
              />
            </AnimatePresence>
            {images.length > 1 ? (
              <>
                <button
                  onClick={() => setImageIdx((i) => (i - 1 + images.length) % images.length)}
                  className="absolute left-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-white/80 shadow-md flex items-center justify-center hover:bg-card transition-colors"
                >
                  <IconChevronLeft size={18} className="text-foreground" />
                </button>
                <button
                  onClick={() => setImageIdx((i) => (i + 1) % images.length)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-white/80 shadow-md flex items-center justify-center hover:bg-card transition-colors"
                >
                  <IconChevronRight size={18} className="text-foreground" />
                </button>
              </>
            ) : null}
            {product.badge ? (
              <span
                className="absolute top-3 left-3 px-2.5 py-1 rounded-full text-white text-xs font-bold"
                style={{
                  background:
                    product.badge === "flash"
                      ? "#E53E3E"
                      : product.badge === "new"
                        ? "#10B981"
                        : "#FF6200",
                }}
              >
                {product.badge === "flash"
                  ? t("product.badge.flash")
                  : product.badge === "new"
                    ? t("product.badge.new")
                    : product.badge === "bestseller"
                      ? t("product.badge.bestseller")
                      : t("product.badge.hot")}
              </span>
            ) : null}
          </div>
          <div className="flex gap-2 overflow-x-auto pb-1 scrollbar-hide">
            {images.map((img, i) => (
              <button
                key={img}
                onClick={() => setImageIdx(i)}
                className="shrink-0 w-18 h-18 w-[72px] h-[72px] rounded-xl overflow-hidden border-2 transition-colors"
                style={{ borderColor: i === imageIdx ? "#00BFB3" : "#e5e7eb" }}
              >
                <ImageWithFallback src={img} alt="" className="w-full h-full object-cover" />
              </button>
            ))}
          </div>
        </div>

        {/* Product Info */}
        <div className="space-y-5">
          <div>
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-xs text-muted-foreground mb-1 uppercase tracking-wider">
                  {product.categoryLabel} · {product.sellerName}
                </p>
                <h1
                  className="text-2xl font-bold text-foreground leading-snug"
                  style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
                >
                  {product.name}
                </h1>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                <button
                  onClick={() => toggleWishlist(product.id)}
                  className="p-2.5 rounded-xl border transition-all"
                  style={{
                    borderColor: loved ? "#FF6200" : "#e5e7eb",
                    background: loved ? "#FFF0E6" : "transparent",
                    color: loved ? "#FF6200" : "#6b7280",
                  }}
                >
                  <IconHeart size={18} fill={loved ? "currentColor" : "none"} />
                </button>
                <button className="p-2.5 rounded-xl border border-border text-muted-foreground hover:bg-muted">
                  <IconShare size={18} />
                </button>
              </div>
            </div>

            {/* Rating */}
            <div className="flex items-center gap-3 mt-3">
              <StarRating value={product.rating} />
              <span className="font-semibold text-foreground">{product.rating}</span>
              <button className="text-sm text-muted-foreground underline">
                {t("product.reviewsCount", { count: product.reviewCount })}
              </button>
              <span className="text-gray-300">·</span>
              <span className="text-sm text-muted-foreground">
                {t("product.soldCount", { count: product.sold })}
              </span>
            </div>
          </div>

          {/* Price */}
          <div
            className="rounded-2xl p-5"
            style={{
              background: "linear-gradient(135deg, rgba(0,191,179,0.06), rgba(255,98,0,0.04))",
            }}
          >
            <div className="flex items-end gap-3 mb-2">
              <span className="text-4xl font-black" style={{ color: "#FF6200" }}>
                {formatPrice(product.price)}
              </span>
              {product.originalPrice ? (
                <span className="text-lg text-muted-foreground line-through mb-0.5">
                  {formatPrice(product.originalPrice)}
                </span>
              ) : null}
              {product.discount ? (
                <span
                  className="px-2.5 py-0.5 rounded-full text-sm font-bold text-white mb-1"
                  style={{ background: "#FF6200" }}
                >
                  -{product.discount}%
                </span>
              ) : null}
            </div>
            {savings > 0 ? (
              <p className="text-sm font-medium" style={{ color: "#00BFB3" }}>
                {t("product.savings", { amount: formatPrice(savings) })}
              </p>
            ) : null}
          </div>

          {/* Colors */}
          {product.colors && product.colors.length > 0 ? (
            <div>
              <p className="text-sm font-semibold text-foreground mb-2.5">
                {t("product.colorsLabel")}: <span className="font-normal text-muted-foreground">{selectedColor}</span>
              </p>
              <div className="flex flex-wrap gap-2">
                {product.colors.map((color) => (
                  <button
                    key={color}
                    onClick={() => setSelectedColor(color)}
                    className="px-4 py-2 rounded-xl border-2 text-sm font-medium transition-all"
                    style={{
                      borderColor: selectedColor === color ? "#00BFB3" : "#e5e7eb",
                      background: selectedColor === color ? "rgba(0,191,179,0.08)" : "transparent",
                      color: selectedColor === color ? "#00BFB3" : "#374151",
                    }}
                  >
                    {color}
                  </button>
                ))}
              </div>
            </div>
          ) : null}

          {/* Sizes */}
          {product.sizes && product.sizes.length > 0 ? (
            <div>
              <div className="flex items-center justify-between mb-2.5">
                <p className="text-sm font-semibold text-foreground">
                  {t("product.sizesLabel")}: <span className="font-normal text-muted-foreground">{selectedSize}</span>
                </p>
                <button className="text-xs font-medium underline" style={{ color: "#00BFB3" }}>
                  {t("product.sizeGuide")}
                </button>
              </div>
              <div className="flex flex-wrap gap-2">
                {product.sizes.map((size) => (
                  <button
                    key={size}
                    onClick={() => setSelectedSize(size)}
                    className="w-12 h-10 rounded-lg border-2 text-sm font-medium transition-all"
                    style={{
                      borderColor: selectedSize === size ? "#00BFB3" : "#e5e7eb",
                      background: selectedSize === size ? "rgba(0,191,179,0.08)" : "transparent",
                      color: selectedSize === size ? "#00BFB3" : "#374151",
                    }}
                  >
                    {size}
                  </button>
                ))}
              </div>
            </div>
          ) : null}

          {/* Quantity */}
          <div>
            <p className="text-sm font-semibold text-foreground mb-2.5">
              {t("product.quantityLabel")}{" "}
              <span className="font-normal text-muted-foreground">
                {t("product.stockAvailable", { count: product.stock })}
              </span>
            </p>
            <div className="flex items-center gap-3">
              <div className="flex items-center border border-border rounded-xl overflow-hidden">
                <button
                  onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                  className="w-10 h-10 text-muted-foreground hover:bg-muted flex items-center justify-center font-bold transition-colors"
                >
                  −
                </button>
                <span className="w-12 text-center font-medium">{quantity}</span>
                <button
                  onClick={() => setQuantity((q) => Math.min(product.stock, q + 1))}
                  className="w-10 h-10 text-muted-foreground hover:bg-muted flex items-center justify-center font-bold transition-colors"
                >
                  +
                </button>
              </div>
              <span className="text-sm text-muted-foreground">
                {t("product.totalLabel")}{" "}
                <span className="font-bold" style={{ color: "#FF6200" }}>
                  {formatPrice(product.price * quantity)}
                </span>
              </span>
            </div>
          </div>

          {/* CTA Buttons */}
          <div className="flex gap-3">
            <button
              onClick={handleAddToCart}
              className="flex-1 py-3.5 rounded-xl border-2 font-bold text-sm transition-all hover:bg-[rgba(0,191,179,0.06)]"
              style={{ borderColor: "#00BFB3", color: "#00BFB3" }}
            >
              {t("product.addToCart")}
            </button>
            <button
              onClick={handleBuyNow}
              className="flex-1 py-3.5 rounded-xl font-bold text-sm text-white shadow-lg hover:opacity-90 transition-opacity"
              style={{ background: "linear-gradient(135deg, #FF6200, #ff8a40)" }}
            >
              {t("product.buyNow")}
            </button>
          </div>

          {/* Trust indicators */}
          <div className="grid grid-cols-3 gap-3">
            {[
              {
                icon: IconTruck,
                text:
                  product.shippingFee === 0
                    ? t("product.trust.freeShipping")
                    : t("product.trust.shipFee", { fee: formatPrice(product.shippingFee) }),
                sub: t("product.trust.shipVia", { method: product.shipping }),
              },
              {
                icon: IconShield,
                text: t("product.trust.protection"),
                sub: t("product.trust.protectionSub"),
              },
              {
                icon: IconRefresh,
                text: t("product.trust.returns"),
                sub: t("product.trust.returnsSub"),
              },
            ].map((item) => (
              <div
                key={item.text}
                className="flex flex-col items-center text-center p-3 rounded-xl bg-muted"
              >
                <item.icon size={20} className="mb-1.5" style={{ color: "#00BFB3" }} />
                <p className="text-xs font-semibold text-foreground">{item.text}</p>
                <p className="text-[10px] text-muted-foreground">{item.sub}</p>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Seller Info */}
      <SellerCard sellerId={product.sellerId} />

      {/* Tabs */}
      <div className="mt-8 bg-card rounded-2xl shadow-sm overflow-hidden">
        <div className="flex border-b border-border">
          {(["desc", "reviews", "qa"] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className="flex-1 py-4 text-sm font-semibold transition-colors"
              style={{
                color: activeTab === tab ? "#00BFB3" : "#6b7280",
                borderBottom: activeTab === tab ? "2px solid #00BFB3" : "2px solid transparent",
              }}
            >
              {tab === "desc"
                ? t("product.tabs.desc")
                : tab === "reviews"
                  ? t("product.tabs.reviews", { count: product.reviewCount })
                  : t("product.tabs.qa")}
            </button>
          ))}
        </div>

        <div className="p-6">
          {activeTab === "desc" ? (
            <div className="space-y-4">
              <p className="text-foreground leading-relaxed">{product.description}</p>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4">
                {[
                  { label: t("product.info.category"), value: product.categoryLabel },
                  { label: t("product.info.origin"), value: product.location },
                  { label: t("product.info.shipping"), value: product.shipping },
                  {
                    label: t("product.info.stockStatus"),
                    value: t("product.info.stockValue", { count: product.stock }),
                  },
                ].map((info) => (
                  <div key={info.label} className="p-3 rounded-xl bg-muted">
                    <p className="text-xs text-muted-foreground mb-0.5">{info.label}</p>
                    <p className="text-sm font-semibold text-foreground">{info.value}</p>
                  </div>
                ))}
              </div>
              {product.tags.length > 0 ? (
                <div className="flex flex-wrap gap-2 mt-2">
                  {product.tags.map((tag) => (
                    <span
                      key={tag}
                      className="px-3 py-1 rounded-full text-xs bg-muted text-muted-foreground"
                    >
                      #{tag}
                    </span>
                  ))}
                </div>
              ) : null}
            </div>
          ) : null}

          {activeTab === "reviews" ? (
            <div className="space-y-6">
              {/* Rating Summary — only when at least one review exists.
                  Previously this rendered hardcoded percentages
                  (68/22/7/2/1), creating a fake histogram on products
                  with zero reviews. Now we hide it entirely when empty
                  and derive real per-star percentages otherwise. */}
              {liveReviewsQuery.data && liveReviewsQuery.data.length > 0 ? (
                <div className="flex items-center gap-8 p-4 rounded-2xl bg-muted">
                  <div className="text-center">
                    <p className="text-5xl font-black" style={{ color: "#FF6200" }}>
                      {product.rating}
                    </p>
                    <StarRating value={product.rating} />
                    <p className="text-xs text-muted-foreground mt-1">
                      {t("product.reviewsCount", { count: product.reviewCount })}
                    </p>
                  </div>
                  <div className="flex-1 space-y-1.5">
                    {[5, 4, 3, 2, 1].map((star) => {
                      const total = liveReviewsQuery.data?.length ?? 0;
                      const count =
                        liveReviewsQuery.data?.filter((r) => Math.round(r.rating) === star).length ?? 0;
                      const pct = total === 0 ? 0 : Math.round((count / total) * 100);
                      return (
                        <div key={star} className="flex items-center gap-2">
                          <span className="text-xs w-4 text-muted-foreground">{star}</span>
                          <IconStar size={11} fill="#F59E0B" className="text-amber-400" />
                          <div className="flex-1 h-2 bg-gray-200 rounded-full overflow-hidden">
                            <div
                              className="h-full rounded-full"
                              style={{ width: `${pct}%`, background: "#F59E0B" }}
                            />
                          </div>
                          <span className="text-xs text-muted-foreground w-6">{pct}%</span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              ) : null}

              {/* Empty-state card — leads the tab when no reviews exist
                  so the form below it is the obvious next action. */}
              {!liveReviewsQuery.isLoading &&
              liveReviewsQuery.data &&
              liveReviewsQuery.data.length === 0 ? (
                <div className="py-6 text-center rounded-2xl border border-dashed border-border">
                  <IconMessage size={36} className="mx-auto mb-2 text-muted-foreground" />
                  <p className="text-sm font-semibold text-foreground">
                    {t("product.reviews.beFirstTitle")}
                  </p>
                  <p className="text-xs text-muted-foreground mt-1">
                    {t("product.reviews.beFirstSubtitle")}
                  </p>
                </div>
              ) : null}

              {/* Write review */}
              {authenticated ? (
                <div className="border border-border rounded-2xl p-4 bg-muted">
                  <p className="text-sm font-semibold text-foreground mb-2">
                    {t("product.reviews.writeTitle")}
                  </p>
                  <div className="flex items-center gap-1 mb-3">
                    {[1, 2, 3, 4, 5].map((n) => (
                      <button
                        key={n}
                        onClick={() => setReviewDraft((d) => ({ ...d, rating: n }))}
                        type="button"
                      >
                        <IconStar
                          size={20}
                          fill={n <= reviewDraft.rating ? "#F59E0B" : "#e5e7eb"}
                          className={n <= reviewDraft.rating ? "text-amber-400" : "text-gray-200"}
                        />
                      </button>
                    ))}
                  </div>
                  <textarea
                    value={reviewDraft.comment}
                    onChange={(e) => setReviewDraft((d) => ({ ...d, comment: e.target.value }))}
                    rows={3}
                    placeholder={t("product.reviews.placeholder")}
                    className="w-full px-3 py-2 border border-border rounded-xl text-sm outline-none focus:border-[#00BFB3] resize-none bg-card"
                  />
                  <button
                    onClick={() => submitReview.mutate(reviewDraft)}
                    disabled={submitReview.isPending || reviewDraft.comment.trim().length === 0}
                    className="mt-3 px-4 py-2 rounded-xl text-white text-sm font-semibold disabled:opacity-50"
                    style={{ background: "#00BFB3" }}
                  >
                    {submitReview.isPending
                      ? t("product.reviews.submitting")
                      : t("product.reviews.submit")}
                  </button>
                </div>
              ) : (
                <button
                  onClick={() => login(`/product/${id}`)}
                  className="text-sm font-medium"
                  style={{ color: "#00BFB3" }}
                >
                  {t("product.reviews.loginToWrite")}
                </button>
              )}

              {/* Live review list */}
              {liveReviewsQuery.isLoading ? (
                <p className="text-sm text-muted-foreground">{t("product.reviews.loading")}</p>
              ) : null}
              {liveReviewsQuery.data && liveReviewsQuery.data.length > 0
                ? liveReviewsQuery.data.map((review) => (
                    <div key={review.id} className="border-b border-border pb-5">
                      <div className="flex items-center gap-3 mb-2">
                        <div className="w-9 h-9 rounded-full bg-gray-200 flex items-center justify-center text-xs font-bold text-muted-foreground">
                          {(review.userName ?? "?").charAt(0).toUpperCase()}
                        </div>
                        <div>
                          <p className="font-medium text-sm text-foreground">
                            {review.userName ?? t("product.reviews.anonGuest")}
                          </p>
                          <div className="flex items-center gap-2">
                            <StarRating value={review.rating} size={13} />
                            {review.createdAt ? (
                              <span className="text-xs text-muted-foreground">· {review.createdAt}</span>
                            ) : null}
                          </div>
                        </div>
                      </div>
                      {review.comment ? (
                        <p className="text-sm text-foreground leading-relaxed mb-3">
                          {review.comment}
                        </p>
                      ) : null}
                      {review.images && review.images.length > 0 ? (
                        <div className="flex gap-2 mb-3">
                          {review.images.map((img) => (
                            <img
                              key={`${review.id}-${img}`}
                              src={img}
                              alt=""
                              className="w-16 h-16 rounded-lg object-cover border border-border"
                              loading="lazy"
                            />
                          ))}
                        </div>
                      ) : null}
                      <button
                        onClick={() => voteHelpful.mutate(review.id)}
                        disabled={voteHelpful.isPending}
                        className="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-muted-foreground transition-colors disabled:opacity-50"
                      >
                        <IconThumbUp size={13} />{" "}
                        {t("product.reviews.helpful", { count: review.helpful ?? 0 })}
                      </button>
                    </div>
                  ))
                : !liveReviewsQuery.isLoading && liveReviewsQuery.data === undefined && (
                    <div className="py-8 text-center">
                      <IconMessage size={40} className="mx-auto mb-3 text-gray-300" />
                      <p className="text-muted-foreground">{t("product.reviews.empty")}</p>
                    </div>
                  )}
            </div>
          ) : null}

          {activeTab === "qa" ? (
            <div className="space-y-5">
              {authenticated ? (
                <div className="border border-border rounded-2xl p-4 bg-muted">
                  <p className="text-sm font-semibold text-foreground mb-2">
                    {t("product.qa.askTitle")}
                  </p>
                  <textarea
                    value={questionDraft}
                    onChange={(e) => setQuestionDraft(e.target.value)}
                    rows={3}
                    placeholder={t("product.qa.placeholder")}
                    className="w-full px-3 py-2 border border-border rounded-xl text-sm outline-none focus:border-[#00BFB3] resize-none bg-card"
                  />
                  <button
                    onClick={() => submitQuestion.mutate(questionDraft.trim())}
                    disabled={submitQuestion.isPending || questionDraft.trim().length === 0}
                    className="mt-3 px-4 py-2 rounded-xl text-white text-sm font-semibold disabled:opacity-50"
                    style={{ background: "#00BFB3" }}
                  >
                    {submitQuestion.isPending ? t("product.qa.submitting") : t("product.qa.submit")}
                  </button>
                </div>
              ) : (
                <button
                  onClick={() => login(`/product/${id}`)}
                  className="text-sm font-medium"
                  style={{ color: "#00BFB3" }}
                >
                  {t("product.qa.loginToAsk")}
                </button>
              )}

              {liveQuestionsQuery.isLoading ? (
                <p className="text-sm text-muted-foreground">{t("product.qa.loading")}</p>
              ) : null}

              {liveQuestionsQuery.data && liveQuestionsQuery.data.length > 0 ? (
                <div className="space-y-4">
                  {liveQuestionsQuery.data.map((q) => (
                    <div key={q.id} className="border border-border rounded-2xl p-4">
                      <p className="text-sm font-medium text-foreground">
                        {t("product.qa.qPrefix")}
                        {q.question}
                      </p>
                      {q.answer ? (
                        <p className="mt-2 text-sm text-muted-foreground pl-3 border-l-2 border-teal-400">
                          {t("product.qa.aPrefix")}
                          {q.answer}
                        </p>
                      ) : (
                        <p className="mt-2 text-xs text-muted-foreground italic">
                          {t("product.qa.noAnswer")}
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                !liveQuestionsQuery.isLoading && (
                  <div className="text-center py-6 text-sm text-muted-foreground">
                    {t("product.qa.empty")}
                  </div>
                )
              )}
            </div>
          ) : null}
        </div>
      </div>

      {/* Frequently bought together — co-purchase aggregate from recommendations-service. */}
      {fbtQuery.data && fbtQuery.data.length > 0 ? (
        <RecommendationGrid
          title={t("product.frequentlyBoughtTogether")}
          items={fbtQuery.data}
          onSelect={(productId) => navigate(`/product/${productId}`)}
        />
      ) : null}

      {/* You may also like — same-category, ±30% price proximity from recommendations-service. */}
      {ymalQuery.data && ymalQuery.data.length > 0 ? (
        <RecommendationGrid
          title={t("product.youMayAlsoLike")}
          items={ymalQuery.data}
          onSelect={(productId) => navigate(`/product/${productId}`)}
        />
      ) : null}
    </div>
  );
}

function RecommendationGrid({
  title,
  items,
  onSelect,
}: {
  title: string;
  items: RecommendationItem[];
  onSelect: (productId: string) => void;
}) {
  return (
    <div className="mt-10">
      <h2
        className="text-xl font-bold text-foreground mb-5"
        style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
      >
        {title}
      </h2>
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
        {items.map((p) => {
          const discount =
            p.originalPrice && p.price && p.originalPrice > p.price
              ? Math.round(((p.originalPrice - p.price) / p.originalPrice) * 100)
              : null;
          const displayName = p.name ?? "";
          return (
            <button
              key={p.id}
              type="button"
              className="bg-card rounded-2xl overflow-hidden shadow-sm hover:shadow-md cursor-pointer group transition-all text-left block w-full p-0 border-0"
              onClick={() => onSelect(p.id)}
            >
              <div className="relative overflow-hidden" style={{ aspectRatio: "1" }}>
                <ImageWithFallback
                  src={p.image ?? ""}
                  alt={displayName}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                />
                {discount ? (
                  <span
                    className="absolute top-2 left-2 px-1.5 py-0.5 rounded text-white text-[10px] font-bold"
                    style={{ background: "#FF6200" }}
                  >
                    -{discount}%
                  </span>
                ) : null}
              </div>
              <div className="p-2.5">
                <p className="text-xs text-muted-foreground font-medium line-clamp-2 mb-1">
                  {displayName.split(" ").slice(0, 5).join(" ")}
                </p>
                <p className="font-bold text-sm" style={{ color: "#FF6200" }}>
                  {p.price != null ? formatPrice(p.price) : ""}
                </p>
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}
