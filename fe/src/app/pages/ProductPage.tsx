import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Star,
  Truck,
  Shield,
  RefreshCw,
  ChevronRight,
  ChevronLeft,
  Heart,
  Share2,
  Store,
  MessageSquare,
  ThumbsUp,
  CheckCircle,
  Package,
} from "lucide-react";
import { motion, AnimatePresence } from "motion/react";
import { useState, useMemo } from "react";
import { useParams, useNavigate } from "react-router";
import { toast } from "sonner";

import { ImageWithFallback } from "../components/image-with-fallback";
import { useVNShop } from "../components/vnshop-context";
import { products, reviews as reviewsMock, sellers } from "../components/vnshop-data";
import { useAuth } from "../hooks/use-auth";
import { useProduct, useProducts } from "../hooks/use-products";
import { askQuestion, questionsByProduct } from "../lib/api/endpoints/questions";
import { reviewsByProduct, createReview, voteReviewHelpful } from "../lib/api/endpoints/reviews";
import { ApiError } from "../lib/api/envelope";
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

export function ProductPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { addToCart, toggleWishlist, isWishlisted } = useVNShop();

  const productQuery = useProduct(id ?? "");
  const product = productQuery.data ?? products.find((p) => p.id === id);
  const { data: catalog = products } = useProducts();
  const related = useMemo(
    () => catalog.filter((p) => p.category === product?.category && p.id !== id).slice(0, 6),
    [catalog, product, id],
  );
  const productReviews = useMemo(() => reviewsMock.filter((r) => r.productId === id), [id]);
  const seller = useMemo(() => sellers.find((s) => s.id === product?.sellerId), [product]);
  const { authenticated, login } = useAuth();
  const qc = useQueryClient();

  const liveReviewsQuery = useQuery({
    queryKey: ["reviews", "product", id],
    queryFn: () => reviewsByProduct(id!),
    enabled: !!id,
    retry: false,
  });

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
      void qc.invalidateQueries({ queryKey: ["reviews", "product", id] });
      toast.success("Đã gửi đánh giá");
      setReviewDraft({ rating: 5, comment: "" });
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không thể gửi đánh giá"),
  });

  const voteHelpful = useMutation({
    mutationFn: (reviewId: string) => voteReviewHelpful(reviewId),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["reviews", "product", id] }),
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không thể vote"),
  });

  const submitQuestion = useMutation({
    mutationFn: (question: string) => askQuestion({ productId: id!, question }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["questions", "product", id] });
      toast.success("Đã gửi câu hỏi");
      setQuestionDraft("");
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không thể gửi câu hỏi"),
  });

  const [reviewDraft, setReviewDraft] = useState({ rating: 5, comment: "" });
  const [questionDraft, setQuestionDraft] = useState("");

  const [imageIdx, setImageIdx] = useState(0);
  const [selectedColor, setSelectedColor] = useState(product?.colors?.[0] ?? "");
  const [selectedSize, setSelectedSize] = useState(product?.sizes?.[0] ?? "");
  const [quantity, setQuantity] = useState(1);
  const [activeTab, setActiveTab] = useState<"desc" | "reviews" | "qa">("desc");
  const loved = isWishlisted(product?.id ?? "");

  if (!product)
    return (
      <div className="max-w-7xl mx-auto px-4 py-24 text-center">
        <Package size={64} className="mx-auto mb-4 text-gray-300" />
        <h2 className="text-xl font-bold text-gray-600">Không tìm thấy sản phẩm</h2>
        <button
          onClick={() => navigate(-1)}
          className="mt-4 px-6 py-2.5 rounded-xl text-white font-medium"
          style={{ background: "#00BFB3" }}
        >
          Quay lại
        </button>
      </div>
    );

  const handleAddToCart = () => addToCart(product, quantity);
  const handleBuyNow = () => {
    addToCart(product, quantity);
    void navigate("/checkout");
  };

  const images = product.images.length > 0 ? product.images : [product.image];
  const savings = product.originalPrice ? product.originalPrice - product.price : 0;

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-sm text-gray-500 mb-6">
        <button onClick={() => navigate("/")} className="hover:text-gray-700">
          Trang chủ
        </button>
        <ChevronRight size={14} />
        <button
          onClick={() => navigate(`/search?cat=${product.category}`)}
          className="hover:text-gray-700"
        >
          {product.categoryLabel}
        </button>
        <ChevronRight size={14} />
        <span className="text-gray-700 truncate max-w-xs">{product.name}</span>
      </nav>

      <div className="grid lg:grid-cols-[500px_1fr] gap-8">
        {/* Image Gallery */}
        <div className="space-y-3">
          <div
            className="relative bg-white rounded-2xl overflow-hidden shadow-sm"
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
                  className="absolute left-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-white/80 shadow-md flex items-center justify-center hover:bg-white transition-colors"
                >
                  <ChevronLeft size={18} className="text-gray-700" />
                </button>
                <button
                  onClick={() => setImageIdx((i) => (i + 1) % images.length)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-white/80 shadow-md flex items-center justify-center hover:bg-white transition-colors"
                >
                  <ChevronRight size={18} className="text-gray-700" />
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
                  ? "⚡ Flash"
                  : product.badge === "new"
                    ? "Mới"
                    : product.badge === "bestseller"
                      ? "Bán chạy"
                      : "Hot"}
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
                <p className="text-xs text-gray-400 mb-1 uppercase tracking-wider">
                  {product.categoryLabel} · {product.sellerName}
                </p>
                <h1
                  className="text-2xl font-bold text-gray-900 leading-snug"
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
                  <Heart size={18} fill={loved ? "currentColor" : "none"} />
                </button>
                <button className="p-2.5 rounded-xl border border-gray-200 text-gray-500 hover:bg-gray-50">
                  <Share2 size={18} />
                </button>
              </div>
            </div>

            {/* Rating */}
            <div className="flex items-center gap-3 mt-3">
              <StarRating value={product.rating} />
              <span className="font-semibold text-gray-800">{product.rating}</span>
              <button className="text-sm text-gray-500 underline">
                {product.reviewCount.toLocaleString()} đánh giá
              </button>
              <span className="text-gray-300">·</span>
              <span className="text-sm text-gray-500">{product.sold.toLocaleString()} đã bán</span>
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
                <span className="text-lg text-gray-400 line-through mb-0.5">
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
                🎉 Bạn tiết kiệm được {formatPrice(savings)}
              </p>
            ) : null}
          </div>

          {/* Colors */}
          {product.colors && product.colors.length > 0 ? (
            <div>
              <p className="text-sm font-semibold text-gray-700 mb-2.5">
                Màu sắc: <span className="font-normal text-gray-500">{selectedColor}</span>
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
                <p className="text-sm font-semibold text-gray-700">
                  Kích cỡ: <span className="font-normal text-gray-500">{selectedSize}</span>
                </p>
                <button className="text-xs font-medium underline" style={{ color: "#00BFB3" }}>
                  Hướng dẫn chọn size
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
            <p className="text-sm font-semibold text-gray-700 mb-2.5">
              Số lượng:{" "}
              <span className="font-normal text-gray-500">({product.stock} sản phẩm có sẵn)</span>
            </p>
            <div className="flex items-center gap-3">
              <div className="flex items-center border border-gray-200 rounded-xl overflow-hidden">
                <button
                  onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                  className="w-10 h-10 text-gray-600 hover:bg-gray-50 flex items-center justify-center font-bold transition-colors"
                >
                  −
                </button>
                <span className="w-12 text-center font-medium">{quantity}</span>
                <button
                  onClick={() => setQuantity((q) => Math.min(product.stock, q + 1))}
                  className="w-10 h-10 text-gray-600 hover:bg-gray-50 flex items-center justify-center font-bold transition-colors"
                >
                  +
                </button>
              </div>
              <span className="text-sm text-gray-500">
                Tổng:{" "}
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
              🛒 Thêm vào giỏ
            </button>
            <button
              onClick={handleBuyNow}
              className="flex-1 py-3.5 rounded-xl font-bold text-sm text-white shadow-lg hover:opacity-90 transition-opacity"
              style={{ background: "linear-gradient(135deg, #FF6200, #ff8a40)" }}
            >
              ⚡ Mua ngay
            </button>
          </div>

          {/* Trust indicators */}
          <div className="grid grid-cols-3 gap-3">
            {[
              {
                icon: Truck,
                text:
                  product.shippingFee === 0
                    ? "Miễn phí vận chuyển"
                    : `Ship ${formatPrice(product.shippingFee)}`,
                sub: `Qua ${product.shipping}`,
              },
              { icon: Shield, text: "Bảo vệ quyền lợi", sub: "Hoàn tiền 100%" },
              { icon: RefreshCw, text: "Đổi trả miễn phí", sub: "Trong 30 ngày" },
            ].map((item) => (
              <div
                key={item.text}
                className="flex flex-col items-center text-center p-3 rounded-xl bg-gray-50"
              >
                <item.icon size={20} className="mb-1.5" style={{ color: "#00BFB3" }} />
                <p className="text-xs font-semibold text-gray-700">{item.text}</p>
                <p className="text-[10px] text-gray-400">{item.sub}</p>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Seller Info */}
      {seller ? (
        <div className="mt-8 bg-white rounded-2xl p-5 shadow-sm">
          <div className="flex items-center justify-between flex-wrap gap-4">
            <div className="flex items-center gap-4">
              <ImageWithFallback
                src={seller.avatar}
                alt={seller.name}
                className="w-16 h-16 rounded-2xl object-cover"
              />
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="font-bold text-gray-800">{seller.name}</h3>
                  {seller.verified ? (
                    <span className="flex items-center gap-1 px-2 py-0.5 rounded-full text-xs text-green-600 bg-green-50 font-medium">
                      <CheckCircle size={11} /> Đã xác minh
                    </span>
                  ) : null}
                </div>
                <div className="flex items-center gap-3 mt-1 text-sm text-gray-500">
                  <span className="flex items-center gap-1">
                    <Star size={13} fill="#F59E0B" className="text-amber-400" /> {seller.rating}
                  </span>
                  <span>{seller.products} sản phẩm</span>
                  <span>Phản hồi {seller.responseRate}%</span>
                </div>
              </div>
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => toast.info("Tin nhắn người bán đang được phát triển")}
                className="flex items-center gap-2 px-4 py-2 rounded-xl border border-gray-200 text-sm font-medium hover:bg-gray-50"
              >
                <MessageSquare size={15} /> Chat ngay
              </button>
              <button
                onClick={() => navigate(`/search?seller=${seller.id}`)}
                className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-white"
                style={{ background: "#00BFB3" }}
              >
                <Store size={15} /> Xem Shop
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {/* Tabs */}
      <div className="mt-8 bg-white rounded-2xl shadow-sm overflow-hidden">
        <div className="flex border-b border-gray-100">
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
                ? "Mô tả sản phẩm"
                : tab === "reviews"
                  ? `Đánh giá (${product.reviewCount.toLocaleString()})`
                  : "Hỏi & Đáp"}
            </button>
          ))}
        </div>

        <div className="p-6">
          {activeTab === "desc" ? (
            <div className="space-y-4">
              <p className="text-gray-700 leading-relaxed">{product.description}</p>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4">
                {[
                  { label: "Danh mục", value: product.categoryLabel },
                  { label: "Xuất xứ", value: product.location },
                  { label: "Vận chuyển", value: product.shipping },
                  { label: "Tình trạng", value: `Còn ${product.stock} SP` },
                ].map((info) => (
                  <div key={info.label} className="p-3 rounded-xl bg-gray-50">
                    <p className="text-xs text-gray-400 mb-0.5">{info.label}</p>
                    <p className="text-sm font-semibold text-gray-700">{info.value}</p>
                  </div>
                ))}
              </div>
              {product.tags.length > 0 ? (
                <div className="flex flex-wrap gap-2 mt-2">
                  {product.tags.map((tag) => (
                    <span
                      key={tag}
                      className="px-3 py-1 rounded-full text-xs bg-gray-100 text-gray-600"
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
              {/* Rating Summary */}
              <div className="flex items-center gap-8 p-4 rounded-2xl bg-gray-50">
                <div className="text-center">
                  <p className="text-5xl font-black" style={{ color: "#FF6200" }}>
                    {product.rating}
                  </p>
                  <StarRating value={product.rating} />
                  <p className="text-xs text-gray-500 mt-1">
                    {product.reviewCount.toLocaleString()} đánh giá
                  </p>
                </div>
                <div className="flex-1 space-y-1.5">
                  {[5, 4, 3, 2, 1].map((star) => {
                    const pct =
                      star === 5 ? 68 : star === 4 ? 22 : star === 3 ? 7 : star === 2 ? 2 : 1;
                    return (
                      <div key={star} className="flex items-center gap-2">
                        <span className="text-xs w-4 text-gray-500">{star}</span>
                        <Star size={11} fill="#F59E0B" className="text-amber-400" />
                        <div className="flex-1 h-2 bg-gray-200 rounded-full overflow-hidden">
                          <div
                            className="h-full rounded-full"
                            style={{ width: `${pct}%`, background: "#F59E0B" }}
                          />
                        </div>
                        <span className="text-xs text-gray-400 w-6">{pct}%</span>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Write review */}
              {authenticated ? (
                <div className="border border-gray-100 rounded-2xl p-4 bg-gray-50">
                  <p className="text-sm font-semibold text-gray-700 mb-2">Viết đánh giá của bạn</p>
                  <div className="flex items-center gap-1 mb-3">
                    {[1, 2, 3, 4, 5].map((n) => (
                      <button
                        key={n}
                        onClick={() => setReviewDraft((d) => ({ ...d, rating: n }))}
                        type="button"
                      >
                        <Star
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
                    placeholder="Chia sẻ trải nghiệm về sản phẩm..."
                    className="w-full px-3 py-2 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#00BFB3] resize-none bg-white"
                  />
                  <button
                    onClick={() => submitReview.mutate(reviewDraft)}
                    disabled={submitReview.isPending || reviewDraft.comment.trim().length === 0}
                    className="mt-3 px-4 py-2 rounded-xl text-white text-sm font-semibold disabled:opacity-50"
                    style={{ background: "#00BFB3" }}
                  >
                    {submitReview.isPending ? "Đang gửi..." : "Gửi đánh giá"}
                  </button>
                </div>
              ) : (
                <button
                  onClick={() => login(`/product/${id}`)}
                  className="text-sm font-medium"
                  style={{ color: "#00BFB3" }}
                >
                  Đăng nhập để viết đánh giá →
                </button>
              )}

              {/* Live review list */}
              {liveReviewsQuery.isLoading ? (
                <p className="text-sm text-gray-400">Đang tải đánh giá...</p>
              ) : null}
              {liveReviewsQuery.data && liveReviewsQuery.data.length > 0
                ? liveReviewsQuery.data.map((review) => (
                    <div key={review.id} className="border-b border-gray-100 pb-5">
                      <div className="flex items-center gap-3 mb-2">
                        <div className="w-9 h-9 rounded-full bg-gray-200 flex items-center justify-center text-xs font-bold text-gray-600">
                          {(review.userName ?? review.userId ?? "?").charAt(0).toUpperCase()}
                        </div>
                        <div>
                          <p className="font-medium text-sm text-gray-800">
                            {review.userName ?? review.userId ?? "Khách hàng"}
                          </p>
                          <div className="flex items-center gap-2">
                            <StarRating value={review.rating} size={13} />
                            {review.createdAt ? (
                              <span className="text-xs text-gray-400">· {review.createdAt}</span>
                            ) : null}
                          </div>
                        </div>
                      </div>
                      {review.comment ? (
                        <p className="text-sm text-gray-700 leading-relaxed mb-3">
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
                              className="w-16 h-16 rounded-lg object-cover border border-gray-200"
                              loading="lazy"
                            />
                          ))}
                        </div>
                      ) : null}
                      <button
                        onClick={() => voteHelpful.mutate(review.id)}
                        disabled={voteHelpful.isPending}
                        className="flex items-center gap-1.5 text-xs text-gray-400 hover:text-gray-600 transition-colors disabled:opacity-50"
                      >
                        <ThumbsUp size={13} /> Hữu ích ({review.helpful ?? 0})
                      </button>
                    </div>
                  ))
                : !liveReviewsQuery.isLoading && productReviews.length > 0
                  ? productReviews.map((review) => (
                      <div key={review.id} className="border-b border-gray-100 pb-5">
                        <div className="flex items-center gap-3 mb-2">
                          <img
                            src={review.avatar}
                            alt={review.userName}
                            className="w-9 h-9 rounded-full object-cover"
                            loading="lazy"
                          />
                          <div>
                            <p className="font-medium text-sm text-gray-800">{review.userName}</p>
                            <div className="flex items-center gap-2">
                              <StarRating value={review.rating} size={13} />
                              {review.variant ? (
                                <span className="text-xs text-gray-400">· {review.variant}</span>
                              ) : null}
                              <span className="text-xs text-gray-400">· {review.date}</span>
                            </div>
                          </div>
                        </div>
                        <p className="text-sm text-gray-700 leading-relaxed mb-3">
                          {review.comment}
                        </p>
                        {review.images && review.images.length > 0 ? (
                          <div className="flex gap-2 mb-3">
                            {review.images.map((img) => (
                              <img
                                key={`${review.id}-${img}`}
                                src={img}
                                alt=""
                                className="w-16 h-16 rounded-lg object-cover border border-gray-200"
                                loading="lazy"
                              />
                            ))}
                          </div>
                        ) : null}
                        <button className="flex items-center gap-1.5 text-xs text-gray-400 hover:text-gray-600 transition-colors">
                          <ThumbsUp size={13} /> Hữu ích ({review.helpful})
                        </button>
                      </div>
                    ))
                  : !liveReviewsQuery.isLoading && (
                      <div className="py-8 text-center">
                        <MessageSquare size={40} className="mx-auto mb-3 text-gray-300" />
                        <p className="text-gray-500">
                          Chưa có đánh giá nào. Hãy là người đầu tiên!
                        </p>
                      </div>
                    )}
            </div>
          ) : null}

          {activeTab === "qa" ? (
            <div className="space-y-5">
              {authenticated ? (
                <div className="border border-gray-100 rounded-2xl p-4 bg-gray-50">
                  <p className="text-sm font-semibold text-gray-700 mb-2">Đặt câu hỏi</p>
                  <textarea
                    value={questionDraft}
                    onChange={(e) => setQuestionDraft(e.target.value)}
                    rows={3}
                    placeholder="Bạn muốn hỏi gì về sản phẩm?"
                    className="w-full px-3 py-2 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#00BFB3] resize-none bg-white"
                  />
                  <button
                    onClick={() => submitQuestion.mutate(questionDraft.trim())}
                    disabled={submitQuestion.isPending || questionDraft.trim().length === 0}
                    className="mt-3 px-4 py-2 rounded-xl text-white text-sm font-semibold disabled:opacity-50"
                    style={{ background: "#00BFB3" }}
                  >
                    {submitQuestion.isPending ? "Đang gửi..." : "Gửi câu hỏi"}
                  </button>
                </div>
              ) : (
                <button
                  onClick={() => login(`/product/${id}`)}
                  className="text-sm font-medium"
                  style={{ color: "#00BFB3" }}
                >
                  Đăng nhập để đặt câu hỏi →
                </button>
              )}

              {liveQuestionsQuery.isLoading ? (
                <p className="text-sm text-gray-400">Đang tải câu hỏi...</p>
              ) : null}

              {liveQuestionsQuery.data && liveQuestionsQuery.data.length > 0 ? (
                <div className="space-y-4">
                  {liveQuestionsQuery.data.map((q) => (
                    <div key={q.id} className="border border-gray-100 rounded-2xl p-4">
                      <p className="text-sm font-medium text-gray-800">Q: {q.question}</p>
                      {q.answer ? (
                        <p className="mt-2 text-sm text-gray-600 pl-3 border-l-2 border-teal-400">
                          A: {q.answer}
                        </p>
                      ) : (
                        <p className="mt-2 text-xs text-gray-400 italic">
                          Chưa có câu trả lời từ người bán.
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                !liveQuestionsQuery.isLoading && (
                  <div className="text-center py-6 text-sm text-gray-400">Chưa có câu hỏi nào.</div>
                )
              )}
            </div>
          ) : null}
        </div>
      </div>

      {/* Related Products */}
      {related.length > 0 ? (
        <div className="mt-10">
          <h2
            className="text-xl font-bold text-gray-800 mb-5"
            style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
          >
            Sản phẩm tương tự
          </h2>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
            {related.map((p) => (
              <button
                key={p.id}
                type="button"
                className="bg-white rounded-2xl overflow-hidden shadow-sm hover:shadow-md cursor-pointer group transition-all text-left block w-full p-0 border-0"
                onClick={() => navigate(`/product/${p.id}`)}
              >
                <div className="relative overflow-hidden" style={{ aspectRatio: "1" }}>
                  <ImageWithFallback
                    src={p.image}
                    alt={p.name}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                  />
                  {p.discount ? (
                    <span
                      className="absolute top-2 left-2 px-1.5 py-0.5 rounded text-white text-[10px] font-bold"
                      style={{ background: "#FF6200" }}
                    >
                      -{p.discount}%
                    </span>
                  ) : null}
                </div>
                <div className="p-2.5">
                  <p className="text-xs text-gray-600 font-medium line-clamp-2 mb-1">
                    {p.name.split(" ").slice(0, 5).join(" ")}
                  </p>
                  <p className="font-bold text-sm" style={{ color: "#FF6200" }}>
                    {formatPrice(p.price)}
                  </p>
                </div>
              </button>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  );
}
