import { useState, useEffect, useMemo, useCallback } from "react";
import { useNavigate } from "react-router";
import { motion, AnimatePresence } from "motion/react";
import {
  ChevronRight, Star, Zap, Truck, Shield, RefreshCw,
  Headphones, TrendingUp, Award, ChevronLeft, Sparkles,
  ArrowRight, Gift, BadgeCheck
} from "lucide-react";
import { formatPrice } from "../lib/format";
import { products, categories, sellers, flashSaleProducts, flashSaleEnd, type Product } from "../components/vnshop-data";
import { useVNShop } from "../components/vnshop-context";
import { useProducts } from "../hooks/use-products";
import { useCountdown } from "../hooks/use-countdown";
import { ImageWithFallback } from "../components/image-with-fallback";

// ─── Section Header ────────────────────────────────────────────────────────────
function SectionHeader({
  title, subtitle, ctaLabel = "Xem tất cả", ctaPath, accent = "teal"
}: {
  title: string; subtitle?: string; ctaLabel?: string; ctaPath?: string; accent?: "teal" | "orange";
}) {
  const navigate = useNavigate();
  const color = accent === "teal" ? "#00BFB3" : "#FF6200";
  return (
    <div className="flex items-end justify-between mb-6">
      <div>
        <h2 className="text-2xl md:text-[26px] font-bold tracking-tight text-gray-900 leading-tight" style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}>{title}</h2>
        {subtitle && <p className="text-sm text-gray-500 mt-1.5">{subtitle}</p>}
      </div>
      {ctaPath && (
        <button
          onClick={() => navigate(ctaPath)}
          className="group flex items-center gap-1.5 text-sm font-semibold transition-all shrink-0 px-3 py-1.5 rounded-full hover:bg-gray-50"
          style={{ color }}
        >
          {ctaLabel} <ChevronRight size={15} className="group-hover:translate-x-0.5 transition-transform" />
        </button>
      )}
    </div>
  );
}

// ─── Product Card ──────────────────────────────────────────────────────────────
function ProductCard({ product, index = 0 }: { product: Product; index?: number }) {
  const navigate = useNavigate();
  const { addToCart, toggleWishlist, isWishlisted } = useVNShop();
  const loved = isWishlisted(product.id);

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: Math.min(index * 0.05, 0.4), duration: 0.3 }}
      className="group rounded-2xl overflow-hidden cursor-pointer bg-white border border-gray-100 hover:border-transparent hover:shadow-[0_20px_40px_-15px_rgba(0,0,0,0.15)] hover:-translate-y-1 transition-all duration-300"
      onClick={() => navigate(`/product/${product.id}`)}
    >
      <div className="relative overflow-hidden" style={{ aspectRatio: "1" }}>
        <ImageWithFallback
          src={product.image}
          alt={product.name}
          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
        />
        {/* Badges */}
        <div className="absolute top-2.5 left-2.5 flex flex-col gap-1">
          {product.discount && (
            <span className="px-2 py-0.5 rounded-full text-white text-xs font-bold leading-tight" style={{ background: "#FF6200" }}>
              -{product.discount}%
            </span>
          )}
          {product.badge === "flash" && (
            <span className="flex items-center gap-0.5 px-2 py-0.5 rounded-full text-white text-xs font-bold leading-tight" style={{ background: "#E53E3E" }}>
              <Zap size={9} fill="white" /> Flash
            </span>
          )}
          {product.badge === "new" && (
            <span className="px-2 py-0.5 rounded-full text-white text-xs font-bold leading-tight" style={{ background: "#10B981" }}>Mới</span>
          )}
        </div>
        {product.shippingFee === 0 && (
          <span className="absolute top-2.5 right-2.5 flex items-center gap-0.5 px-1.5 py-0.5 rounded-md text-white text-xs font-semibold" style={{ background: "rgba(0,0,0,0.45)", backdropFilter: "blur(4px)" }}>
            <Truck size={9} /> Free
          </span>
        )}
        {/* Wishlist */}
        <button
          className="absolute bottom-2.5 right-2.5 w-8 h-8 rounded-full flex items-center justify-center shadow-md transition-all duration-200 opacity-0 group-hover:opacity-100 hover:scale-110"
          style={{ background: loved ? "#FF6200" : "rgba(255,255,255,0.95)" }}
          onClick={e => { e.stopPropagation(); toggleWishlist(product.id); }}
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill={loved ? "white" : "none"} stroke={loved ? "white" : "#374151"} strokeWidth="2">
            <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
          </svg>
        </button>
        {/* Add to cart overlay */}
        <button
          className="absolute bottom-0 left-0 right-0 py-2 text-white text-xs font-semibold translate-y-full group-hover:translate-y-0 transition-transform duration-300"
          style={{ background: "rgba(0,191,179,0.95)", backdropFilter: "blur(4px)" }}
          onClick={e => { e.stopPropagation(); addToCart(product); }}
        >
          + Thêm vào giỏ hàng
        </button>
      </div>
      <div className="p-4">
        <p className="text-[11px] uppercase tracking-wider text-gray-400 mb-1 truncate font-medium">{product.sellerName}</p>
        <h3 className="text-sm font-medium text-gray-900 leading-snug line-clamp-2 mb-3 min-h-[2.5rem]">{product.name}</h3>
        <div className="flex items-baseline gap-2 mb-2.5">
          <span className="font-bold text-base" style={{ color: "#FF6200" }}>{formatPrice(product.price)}</span>
          {product.originalPrice && (
            <span className="text-xs text-gray-400 line-through">{formatPrice(product.originalPrice)}</span>
          )}
        </div>
        <div className="flex items-center gap-1.5 pt-2.5 border-t border-gray-100">
          <Star size={11} fill="#FF6200" stroke="#FF6200" />
          <span className="text-xs font-semibold text-gray-700">{product.rating}</span>
          <span className="text-xs text-gray-400">({product.reviewCount >= 1000 ? `${(product.reviewCount/1000).toFixed(1)}k` : product.reviewCount})</span>
          <span className="text-xs text-gray-400 ml-auto">Đã bán {product.sold >= 1000 ? `${(product.sold/1000).toFixed(0)}k` : product.sold}</span>
        </div>
      </div>
    </motion.div>
  );
}

// ─── Hero Section ─────────────────────────────────────────────────────────────
const heroSlides = [
  {
    id: 1,
    eyebrow: "🔥 Flash Sale — Giảm đến 50%",
    title: "Mua Sắm\nThả Ga Không Lo",
    subtitle: "Hàng ngàn sản phẩm chính hãng, giao hàng tận nơi, hoàn tiền 100%.",
    cta: "Mua ngay",
    ctaPath: "/search?flash=true",
    secondaryCta: "Khám phá",
    secondaryPath: "/search",
    bg: "linear-gradient(135deg, #006B65 0%, #009990 45%, #00BFB3 100%)",
    image: "https://images.unsplash.com/photo-1483985988355-763728e1935b?w=900&q=85",
    imageAlign: "right",
  },
  {
    id: 2,
    eyebrow: "💻 Công Nghệ — Bảo hành 12 tháng",
    title: "Điện Tử\nChính Hãng",
    subtitle: "Laptop, điện thoại, tai nghe cao cấp — Chính hãng, giá tốt nhất thị trường.",
    cta: "Xem ngay",
    ctaPath: "/search?cat=electronics",
    secondaryCta: "Flash Deal",
    secondaryPath: "/search?flash=true",
    bg: "linear-gradient(135deg, #0f172a 0%, #1e3a5f 50%, #1D4ED8 100%)",
    image: "https://images.unsplash.com/photo-1498049794561-7780e7231661?w=900&q=85",
    imageAlign: "right",
  },
  {
    id: 3,
    eyebrow: "👗 Fashion Week — Xu hướng 2026",
    title: "Phong Cách\nMỗi Ngày",
    subtitle: "Cập nhật bộ sưu tập thời trang mới nhất, miễn phí vận chuyển toàn quốc.",
    cta: "Khám phá",
    ctaPath: "/search?cat=fashion",
    secondaryCta: "Hàng mới về",
    secondaryPath: "/search?cat=fashion&sort=new",
    bg: "linear-gradient(135deg, #7C3AED 0%, #C026D3 50%, #FF6200 100%)",
    image: "https://images.unsplash.com/photo-1441986300917-64674bd600d8?w=900&q=85",
    imageAlign: "right",
  },
];

function HeroSection() {
  const navigate = useNavigate();
  const [slide, setSlide] = useState(0);
  const [direction, setDirection] = useState(1);

  const go = useCallback((next: number) => {
    setSlide((curr) => {
      const target = ((next % heroSlides.length) + heroSlides.length) % heroSlides.length;
      setDirection(target > curr ? 1 : -1);
      return target;
    });
  }, []);

  useEffect(() => {
    const id = setInterval(() => {
      setSlide((s) => {
        setDirection(1);
        return (s + 1) % heroSlides.length;
      });
    }, 6000);
    return () => clearInterval(id);
  }, []);

  const current = heroSlides[slide];

  return (
    <div className="relative overflow-hidden rounded-3xl shadow-[0_20px_60px_-20px_rgba(0,0,0,0.25)]" style={{ minHeight: 440, background: current.bg }}>
      {/* Background photo with overlay */}
      <AnimatePresence mode="wait">
        <motion.div
          key={slide + "-bg"}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.6 }}
          className="absolute inset-0 bg-cover bg-center"
          style={{ backgroundImage: `url(${current.image})`, filter: "brightness(0.35) saturate(1.1)" }}
        />
      </AnimatePresence>
      <div className="absolute inset-0" style={{ background: "linear-gradient(105deg, rgba(0,0,0,0.7) 0%, rgba(0,0,0,0.35) 50%, rgba(0,0,0,0.05) 100%)" }} />
      <div className="absolute inset-0 opacity-30" style={{ background: "radial-gradient(circle at 80% 20%, rgba(255,255,255,0.18), transparent 50%)" }} />

      {/* Content */}
      <div className="relative z-10 h-full flex items-center px-10 md:px-14 py-14 md:py-20 max-w-2xl">
        <AnimatePresence mode="wait">
          <motion.div
            key={slide}
            initial={{ opacity: 0, x: direction * -40 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: direction * 30 }}
            transition={{ duration: 0.45, ease: "easeOut" }}
          >
            <span className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-full text-xs font-semibold text-white mb-6 tracking-wide" style={{ background: "rgba(255,255,255,0.12)", backdropFilter: "blur(10px)", border: "1px solid rgba(255,255,255,0.22)" }}>
              {current.eyebrow}
            </span>
            <h1 className="text-4xl md:text-6xl font-black text-white leading-[1.05] mb-5 tracking-tight" style={{ fontFamily: "'Be Vietnam Pro', sans-serif", whiteSpace: "pre-line", letterSpacing: "-0.02em" }}>
              {current.title}
            </h1>
            <p className="text-white/80 text-base md:text-lg mb-9 leading-relaxed max-w-md font-light">
              {current.subtitle}
            </p>
            <div className="flex gap-3 flex-wrap">
              <button
                onClick={() => navigate(current.ctaPath)}
                className="px-7 py-3.5 rounded-2xl font-bold text-white text-sm shadow-[0_10px_30px_-8px_rgba(255,98,0,0.6)] hover:shadow-[0_14px_40px_-8px_rgba(255,98,0,0.7)] transition-all hover:-translate-y-0.5 flex items-center gap-2"
                style={{ background: "linear-gradient(135deg, #FF6200 0%, #FF8C00 100%)" }}
              >
                {current.cta} <ArrowRight size={16} />
              </button>
              <button
                onClick={() => navigate(current.secondaryPath)}
                className="px-6 py-3.5 rounded-2xl font-semibold text-sm text-white border border-white/30 hover:bg-white/10 hover:border-white/50 transition-all backdrop-blur-sm"
              >
                {current.secondaryCta}
              </button>
            </div>
          </motion.div>
        </AnimatePresence>
      </div>

      {/* Controls */}
      <div className="absolute bottom-5 left-8 flex items-center gap-3">
        <div className="flex gap-1.5">
          {heroSlides.map((_, i) => (
            <button
              key={i}
              onClick={() => go(i)}
              className="h-1.5 rounded-full transition-all duration-400"
              style={{ background: i === slide ? "#fff" : "rgba(255,255,255,0.35)", width: i === slide ? 28 : 8 }}
            />
          ))}
        </div>
        <span className="text-white/40 text-xs tabular-nums">{slide + 1}/{heroSlides.length}</span>
      </div>
      <button
        onClick={() => go(slide - 1)}
        className="absolute left-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full flex items-center justify-center text-white transition-colors"
        style={{ background: "rgba(0,0,0,0.3)", backdropFilter: "blur(4px)" }}
      >
        <ChevronLeft size={18} />
      </button>
      <button
        onClick={() => go(slide + 1)}
        className="absolute right-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full flex items-center justify-center text-white transition-colors"
        style={{ background: "rgba(0,0,0,0.3)", backdropFilter: "blur(4px)" }}
      >
        <ChevronRight size={18} />
      </button>
    </div>
  );
}

// ─── Promo Strip ──────────────────────────────────────────────────────────────
function PromoStrip() {
  const navigate = useNavigate();
  const items = [
    { emoji: "⚡", label: "Flash Sale", sub: "Đến -50%", path: "/search?flash=true", color: "#E53E3E" },
    { emoji: "🚀", label: "Giao Hỏa Tốc", sub: "2 giờ nhận hàng", path: "/search", color: "#FF6200" },
    { emoji: "🎁", label: "Voucher 50k", sub: "Code: VNSHOP50", path: "/search", color: "#8B5CF6" },
    { emoji: "🛡️", label: "Hàng Chính Hãng", sub: "Bảo đảm 100%", path: "/search", color: "#10B981" },
  ];
  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
      {items.map(item => (
        <button
          key={item.label}
          onClick={() => navigate(item.path)}
          className="flex items-center gap-3 p-3.5 rounded-2xl bg-white border border-gray-100 hover:shadow-md hover:-translate-y-0.5 transition-all text-left group"
        >
          <span className="text-2xl">{item.emoji}</span>
          <div>
            <p className="text-sm font-semibold text-gray-800 group-hover:text-[#00BFB3] transition-colors">{item.label}</p>
            <p className="text-xs" style={{ color: item.color }}>{item.sub}</p>
          </div>
        </button>
      ))}
    </div>
  );
}

// ─── Flash Sale ────────────────────────────────────────────────────────────────
function FlashSaleSection() {
  const { h, m, s, isExpired } = useCountdown(flashSaleEnd.getTime());
  const navigate = useNavigate();
  if (isExpired) return null;

  return (
    <section>
      {/* Header */}
      <div
        className="rounded-2xl overflow-hidden"
        style={{ background: "linear-gradient(135deg, #7f0000 0%, #cc0000 35%, #FF6200 100%)" }}
      >
        <div className="px-6 pt-5 pb-4">
          <div className="flex flex-col sm:flex-row sm:items-center gap-4 mb-5">
            <div className="flex items-center gap-3">
              <div className="w-11 h-11 rounded-xl flex items-center justify-center" style={{ background: "rgba(255,255,255,0.15)" }}>
                <Zap size={24} fill="white" className="text-white" />
              </div>
              <div>
                <p className="text-white font-black text-lg leading-tight" style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}>⚡ FLASH SALE</p>
                <p className="text-white/60 text-xs">Giảm sốc — Số lượng có hạn!</p>
              </div>
            </div>
            <div className="sm:ml-auto flex items-center gap-3">
              <span className="text-white/60 text-sm hidden sm:block">Kết thúc sau</span>
              <div className="flex items-center gap-1.5">
                {[{ v: h, l: "Giờ" }, { v: m, l: "Phút" }, { v: s, l: "Giây" }].map(({ v, l }, i) => (
                  <div key={l} className="flex items-center gap-1.5">
                    <div className="flex flex-col items-center">
                      <span className="text-white font-black text-xl tabular-nums w-12 h-12 flex items-center justify-center rounded-xl" style={{ background: "rgba(0,0,0,0.3)" }}>{v}</span>
                      <span className="text-white/50 text-[10px] mt-0.5">{l}</span>
                    </div>
                    {i < 2 && <span className="text-white/50 font-bold text-lg mb-4">:</span>}
                  </div>
                ))}
              </div>
              <button
                onClick={() => navigate("/search?flash=true")}
                className="ml-2 px-5 py-2.5 rounded-xl text-sm font-bold bg-white transition-opacity hover:opacity-90"
                style={{ color: "#E53E3E" }}
              >
                Xem tất cả
              </button>
            </div>
          </div>

          {/* Product strip */}
          <div className="flex gap-3 overflow-x-auto pb-1 -mx-1 px-1 scrollbar-hide">
            {flashSaleProducts.map((p, i) => (
              <motion.button
                key={p.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.08 }}
                onClick={() => navigate(`/product/${p.id}`)}
                className="shrink-0 group"
                style={{ width: 130 }}
              >
                <div className="rounded-xl overflow-hidden mb-2 relative" style={{ aspectRatio: "1" }}>
                  <ImageWithFallback src={p.image} alt={p.name} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300" />
                  <div className="absolute bottom-0 left-0 right-0 py-1 text-center" style={{ background: "rgba(229,53,62,0.9)" }}>
                    <span className="text-white font-black text-xs">-{p.discount}%</span>
                  </div>
                </div>
                <p className="text-white text-xs font-medium text-left line-clamp-1 mb-0.5">{p.name.split(" ").slice(0, 5).join(" ")}</p>
                <p className="text-yellow-300 font-bold text-sm text-left">{formatPrice(p.price)}</p>
                {/* Sold progress */}
                <div className="mt-1.5">
                  <div className="h-1.5 rounded-full overflow-hidden" style={{ background: "rgba(255,255,255,0.2)" }}>
                    <div className="h-full rounded-full bg-yellow-300" style={{ width: `${(i % 4) * 15 + 35}%` }} />
                  </div>
                  <p className="text-white/50 text-[10px] mt-0.5">Đã bán {(i % 4) * 15 + 35}%</p>
                </div>
              </motion.button>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

// ─── Categories ────────────────────────────────────────────────────────────────
function CategoriesSection() {
  const navigate = useNavigate();
  const catColors: Record<string, string> = {
    electronics: "#3B82F6", fashion: "#EC4899", beauty: "#F59E0B",
    home: "#10B981", sports: "#FF6200", books: "#8B5CF6",
    food: "#EF4444", toys: "#F97316",
  };
  return (
    <section>
      <SectionHeader title="Danh Mục Sản Phẩm" ctaLabel="Tất cả danh mục" ctaPath="/search" />
      <div className="grid grid-cols-4 md:grid-cols-8 gap-3">
        {categories.map((cat, i) => {
          const color = catColors[cat.id] || "#00BFB3";
          return (
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
              <div className="w-14 h-14 rounded-2xl flex items-center justify-center text-[26px]" style={{ background: `${color}14` }}>
                {cat.emoji}
              </div>
              <span className="text-[13px] font-semibold text-gray-800 text-center leading-tight">{cat.label}</span>
              <span className="text-[11px] text-gray-400 font-medium">{(cat.count / 1000).toFixed(0)}k sản phẩm</span>
            </motion.button>
          );
        })}
      </div>
    </section>
  );
}

// ─── Trust Bar ────────────────────────────────────────────────────────────────
function TrustBar() {
  const items = [
    { icon: Truck,       text: "Miễn phí vận chuyển", sub: "Đơn từ 150.000đ", color: "#00BFB3" },
    { icon: Shield,      text: "Hàng chính hãng 100%", sub: "Cam kết hoàn tiền", color: "#3B82F6" },
    { icon: RefreshCw,   text: "Đổi trả 30 ngày", sub: "Không cần lý do", color: "#10B981" },
    { icon: Headphones,  text: "Hỗ trợ 24/7", sub: "1800 6789 miễn phí", color: "#F59E0B" },
  ];
  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-px bg-gray-100 rounded-2xl overflow-hidden border border-gray-100">
      {items.map(item => (
        <div key={item.text} className="flex items-center gap-3.5 p-5 bg-white hover:bg-gray-50/50 transition-colors">
          <div className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0" style={{ background: `${item.color}14` }}>
            <item.icon size={20} style={{ color: item.color }} strokeWidth={2.2} />
          </div>
          <div>
            <p className="text-sm font-semibold text-gray-900 leading-tight">{item.text}</p>
            <p className="text-xs text-gray-500 mt-1">{item.sub}</p>
          </div>
        </div>
      ))}
    </div>
  );
}

// ─── Promo Banners ────────────────────────────────────────────────────────────
function PromoBanners() {
  const navigate = useNavigate();
  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
      <div
        className="md:col-span-2 rounded-2xl p-7 cursor-pointer hover:opacity-95 transition-opacity relative overflow-hidden"
        style={{ background: "linear-gradient(135deg, #0f172a 0%, #1e3a5f 100%)", minHeight: 160 }}
        onClick={() => navigate("/search?cat=electronics")}
      >
        <div className="absolute right-0 top-0 bottom-0 w-1/2 opacity-15" style={{ backgroundImage: "url(https://images.unsplash.com/photo-1498049794561-7780e7231661?w=400&q=60)", backgroundSize: "cover", backgroundPosition: "center" }} />
        <div className="relative z-10">
          <span className="text-xs font-bold text-blue-300 tracking-wider uppercase">Flash Deal • Hôm Nay</span>
          <h3 className="text-2xl font-black text-white mt-1.5 mb-2" style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}>Điện Tử Giảm 40%</h3>
          <p className="text-gray-400 text-sm mb-5 max-w-xs">Laptop, tai nghe Sony, smartwatch — Bảo hành 12 tháng chính hãng</p>
          <button className="flex items-center gap-1.5 text-sm font-semibold text-white px-4 py-2 rounded-lg transition-colors hover:bg-white/10" style={{ border: "1px solid rgba(255,255,255,0.2)" }}>
            Mua ngay <ArrowRight size={15} />
          </button>
        </div>
      </div>
      <div className="flex flex-col gap-4">
        <div
          className="flex-1 rounded-2xl p-5 cursor-pointer hover:opacity-95 transition-opacity"
          style={{ background: "linear-gradient(135deg, #FF6200 0%, #FF8C00 100%)" }}
          onClick={() => navigate("/search?cat=fashion")}
        >
          <span className="text-xs font-bold text-yellow-100 tracking-wider uppercase">Mới về</span>
          <h3 className="text-lg font-black text-white mt-1 mb-1" style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}>Thời Trang Hè</h3>
          <p className="text-yellow-100/80 text-xs mb-3">Xu hướng mới nhất 2026</p>
          <button className="text-xs font-semibold text-white flex items-center gap-1 opacity-80 hover:opacity-100">Khám phá <ChevronRight size={13} /></button>
        </div>
        <div
          className="flex-1 rounded-2xl p-5 cursor-pointer hover:opacity-95 transition-opacity"
          style={{ background: "linear-gradient(135deg, #7C3AED 0%, #9333EA 100%)" }}
          onClick={() => navigate("/search?cat=beauty")}
        >
          <span className="text-xs font-bold text-purple-200 tracking-wider uppercase">Beauty Sale</span>
          <h3 className="text-lg font-black text-white mt-1 mb-1" style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}>Làm Đẹp -30%</h3>
          <p className="text-purple-200 text-xs mb-3">Skincare Hàn Quốc chính hãng</p>
          <button className="text-xs font-semibold text-white flex items-center gap-1 opacity-80 hover:opacity-100">Xem ngay <ChevronRight size={13} /></button>
        </div>
      </div>
    </div>
  );
}

// ─── Seller Showcase ──────────────────────────────────────────────────────────
function SellerShowcase() {
  const navigate = useNavigate();
  return (
    <section>
      <SectionHeader title="Shop Uy Tín" subtitle="Được xác minh bởi VNShop" ctaPath="/search" />
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {sellers.slice(0, 3).map((seller, i) => (
          <motion.div
            key={seller.id}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.1 }}
            whileHover={{ y: -5 }}
            className="bg-white rounded-2xl overflow-hidden border border-gray-100 hover:border-[#00BFB3] hover:shadow-xl transition-all cursor-pointer group"
            style={{ transitionDuration: "300ms" }}
            onClick={() => navigate(`/search?seller=${seller.id}`)}
          >
            {/* Banner */}
            <div className="h-24 relative overflow-hidden" style={{ background: "linear-gradient(135deg, #E6FAF9, #B3F0ED)" }}>
              <ImageWithFallback src={seller.banner} alt={seller.name} className="w-full h-full object-cover opacity-50 group-hover:scale-105 transition-transform duration-500" />
              <div className="absolute top-2.5 right-2.5 px-2 py-0.5 rounded-full text-xs font-bold text-white" style={{ background: "#00BFB3" }}>
                Mall
              </div>
            </div>
            <div className="px-4 pb-4">
              <div className="flex items-end justify-between -mt-7 mb-3">
                <img
                  src={seller.avatar}
                  alt={seller.name}
                  className="w-14 h-14 rounded-2xl object-cover border-4 border-white shadow-lg"
                />
                <div className="flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium bg-green-50 text-green-600 mb-1">
                  <BadgeCheck size={11} className="fill-green-600 text-white" />
                  Đã xác minh
                </div>
              </div>
              <div className="flex items-center gap-2 mb-0.5">
                <h3 className="font-bold text-gray-900 text-sm">{seller.name}</h3>
                {seller.verified && <Award size={13} style={{ color: "#F59E0B" }} />}
              </div>
              <div className="flex items-center gap-1.5 mb-3 text-xs text-gray-500">
                <Star size={11} fill="#FF6200" stroke="#FF6200" />
                <span className="font-semibold text-gray-700">{seller.rating}</span>
                <span>•</span>
                <span>{(seller.followers / 1000).toFixed(1)}k followers</span>
              </div>
              <div className="grid grid-cols-3 gap-2 pt-3 border-t border-gray-100">
                {[
                  { label: "Sản phẩm", value: seller.products },
                  { label: "Phản hồi", value: `${seller.responseRate}%` },
                  { label: "Đã bán", value: `${(seller.sales / 1000).toFixed(0)}k` },
                ].map(stat => (
                  <div key={stat.label} className="text-center">
                    <p className="font-bold text-sm text-gray-900">{stat.value}</p>
                    <p className="text-xs text-gray-400">{stat.label}</p>
                  </div>
                ))}
              </div>
            </div>
          </motion.div>
        ))}
      </div>
    </section>
  );
}

// ─── Trending ─────────────────────────────────────────────────────────────────
function TrendingBar() {
  const navigate = useNavigate();
  const trending = [
    { emoji: "🔥", text: "Tai nghe Sony WH", path: "/search?q=sony" },
    { emoji: "👟", text: "Nike Air Max 2026", path: "/search?q=nike" },
    { emoji: "💄", text: "Skincare Hàn Quốc", path: "/search?cat=beauty" },
    { emoji: "📱", text: "iPhone 16 Pro Max", path: "/search?cat=electronics" },
    { emoji: "👗", text: "Áo thun basic", path: "/search?q=ao+thun" },
    { emoji: "⌚", text: "Apple Watch 10", path: "/search?q=smartwatch" },
    { emoji: "💻", text: "MacBook Air M4", path: "/search?q=macbook" },
    { emoji: "🎧", text: "AirPods Pro 3", path: "/search?q=airpods" },
  ];
  return (
    <div className="flex items-center gap-3 p-4 bg-white rounded-2xl border border-gray-100 overflow-hidden">
      <div className="flex items-center gap-2 shrink-0 pr-3 border-r border-gray-100">
        <TrendingUp size={16} style={{ color: "#FF6200" }} />
        <span className="text-sm font-semibold text-gray-700 whitespace-nowrap">Đang Hot</span>
      </div>
      <div className="flex gap-2 overflow-x-auto scrollbar-hide flex-nowrap">
        {trending.map((t, i) => (
          <button
            key={i}
            onClick={() => navigate(t.path)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm border border-gray-100 whitespace-nowrap hover:border-[#00BFB3] hover:text-[#00BFB3] hover:bg-teal-50 transition-all shrink-0"
          >
            <span>{t.emoji}</span>
            <span className="text-gray-600 hover:text-[#00BFB3]">{t.text}</span>
          </button>
        ))}
      </div>
    </div>
  );
}

// ─── Products Section ─────────────────────────────────────────────────────────
function ProductsSection() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState("all");
  const { data: catalog = products } = useProducts();
  const tabs = [
    { id: "all", label: "Tất cả", emoji: "✨" },
    { id: "electronics", label: "Điện Tử", emoji: "📱" },
    { id: "fashion", label: "Thời Trang", emoji: "👗" },
    { id: "beauty", label: "Làm Đẹp", emoji: "💄" },
    { id: "sports", label: "Thể Thao", emoji: "⚽" },
  ];
  const filtered = useMemo(
    () => activeTab === "all" ? catalog : catalog.filter(p => p.category === activeTab),
    [activeTab, catalog]
  );

  return (
    <section>
      <div className="flex items-end justify-between mb-6">
        <div>
          <div className="flex items-center gap-2 mb-1.5">
            <Sparkles size={18} style={{ color: "#00BFB3" }} />
            <span className="text-xs font-bold uppercase tracking-widest" style={{ color: "#00BFB3" }}>Dành riêng cho bạn</span>
          </div>
          <h2 className="text-2xl md:text-[26px] font-bold tracking-tight text-gray-900 leading-tight" style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}>Gợi Ý Cho Bạn</h2>
          <p className="text-sm text-gray-500 mt-1.5">Được cá nhân hóa dựa trên sở thích mua sắm</p>
        </div>
        <button onClick={() => navigate("/search")} className="group flex items-center gap-1.5 text-sm font-semibold transition-all px-3 py-1.5 rounded-full hover:bg-gray-50" style={{ color: "#00BFB3" }}>
          Xem tất cả <ChevronRight size={15} className="group-hover:translate-x-0.5 transition-transform" />
        </button>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 mb-6 overflow-x-auto pb-1 scrollbar-hide">
        {tabs.map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className="shrink-0 flex items-center gap-1.5 px-4 py-2 rounded-full text-sm font-medium transition-all duration-200"
            style={activeTab === tab.id
              ? { background: "#00BFB3", color: "#fff", boxShadow: "0 4px 12px rgba(0,191,179,0.35)" }
              : { background: "#fff", color: "#6b7280", border: "1px solid #e5e7eb" }
            }
          >
            <span>{tab.emoji}</span> {tab.label}
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
          onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = "#00BFB3"; }}
          onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = "transparent"; }}
        >
          Xem thêm sản phẩm →
        </button>
      </div>
    </section>
  );
}

// ─── Bestsellers Sidebar ──────────────────────────────────────────────────────
function Bestsellers() {
  const navigate = useNavigate();
  const { data: catalog = products } = useProducts();
  const items = useMemo(() => [...catalog].sort((a, b) => b.sold - a.sold).slice(0, 5), [catalog]);
  return (
    <section>
      <SectionHeader title="Bán Chạy Nhất" accent="orange" />
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
              style={{ color: i < 3 ? "#FF6200" : "#D1D5DB", fontFamily: "'Be Vietnam Pro', sans-serif" }}
            >
              {i + 1}
            </span>
            <ImageWithFallback src={p.image} alt={p.name} className="w-12 h-12 rounded-lg object-cover shrink-0" />
            <div className="flex-1 min-w-0">
              <p className="text-xs font-medium text-gray-800 line-clamp-2 leading-tight group-hover:text-[#00BFB3] transition-colors">
                {p.name}
              </p>
              <p className="text-xs text-gray-400 mt-0.5">{p.sold.toLocaleString()} đã bán</p>
            </div>
            <div className="shrink-0 text-right">
              <p className="text-xs font-bold" style={{ color: "#FF6200" }}>{formatPrice(p.price)}</p>
              {p.discount && (
                <span className="text-[10px] font-bold px-1 py-0.5 rounded" style={{ background: "#FFF2EA", color: "#FF6200" }}>-{p.discount}%</span>
              )}
            </div>
          </motion.div>
        ))}
      </div>
    </section>
  );
}

// ─── App Download Banner ──────────────────────────────────────────────────────
function AppBanner() {
  return (
    <div
      className="rounded-2xl overflow-hidden relative"
      style={{ background: "linear-gradient(135deg, #00BFB3 0%, #009990 100%)", minHeight: 120 }}
    >
      <div className="absolute right-0 top-0 bottom-0 w-64 opacity-10" style={{ backgroundImage: "url(https://images.unsplash.com/photo-1512941937669-90a1b58e7e9c?w=300&q=50)", backgroundSize: "cover" }} />
      <div className="relative z-10 px-8 py-6 flex flex-col sm:flex-row items-start sm:items-center gap-5">
        <div className="w-12 h-12 rounded-2xl bg-white/20 flex items-center justify-center shrink-0">
          <span className="text-2xl">📱</span>
        </div>
        <div className="flex-1">
          <h3 className="font-black text-white text-lg mb-0.5" style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}>Tải App VNShop</h3>
          <p className="text-white/75 text-sm">Nhận thêm ưu đãi độc quyền — Voucher 100k cho lần mua đầu qua app</p>
        </div>
        <div className="flex gap-2.5 shrink-0">
          <button className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold bg-white hover:opacity-90 transition-opacity" style={{ color: "#009990" }}>
            <span>🍎</span> App Store
          </button>
          <button className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold bg-white hover:opacity-90 transition-opacity" style={{ color: "#009990" }}>
            <span>🤖</span> Google Play
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── User Sidebar Widget ──────────────────────────────────────────────────────
function UserWidget() {
  const navigate = useNavigate();
  const { user, isLoggedIn, cartCount, wishlist } = useVNShop();
  return (
    <div className="space-y-4">
      {/* Account card */}
      <div className="bg-white rounded-2xl p-5 border border-gray-100 shadow-sm">
        {isLoggedIn && user ? (
          <>
            <div className="flex items-center gap-3 mb-4">
              <div className="w-11 h-11 rounded-xl flex items-center justify-center text-white font-bold" style={{ background: "linear-gradient(135deg, #00BFB3, #009990)" }}>
                {user.name.charAt(0)}
              </div>
              <div>
                <p className="font-semibold text-sm text-gray-900">{user.name}</p>
                <p className="text-xs text-gray-400">{user.email}</p>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-2">
              {[
                { label: "Giỏ hàng", value: cartCount, color: "#00BFB3", path: "/cart" },
                { label: "Yêu thích", value: wishlist.length, color: "#FF6200", path: "/wishlist" },
                { label: "Đơn hàng", value: 3, color: "#3B82F6", path: "/orders" },
                { label: "Voucher", value: 5, color: "#10B981", path: "/profile" },
              ].map(item => (
                <button
                  key={item.label}
                  onClick={() => navigate(item.path)}
                  className="text-center p-2.5 rounded-xl hover:opacity-80 transition-opacity"
                  style={{ background: `${item.color}10` }}
                >
                  <p className="font-bold text-base" style={{ color: item.color }}>{item.value}</p>
                  <p className="text-xs text-gray-500 mt-0.5">{item.label}</p>
                </button>
              ))}
            </div>
          </>
        ) : (
          <div className="text-center py-2">
            <div className="w-14 h-14 rounded-2xl mx-auto mb-3 flex items-center justify-center" style={{ background: "#E6FAF9" }}>
              <span className="text-3xl">👋</span>
            </div>
            <p className="font-semibold text-gray-800 mb-1">Chào bạn!</p>
            <p className="text-xs text-gray-500 mb-4">Đăng nhập để nhận ưu đãi cá nhân</p>
            <div className="flex gap-2">
              <button onClick={() => navigate("/login")} className="flex-1 py-2 rounded-xl text-sm font-semibold text-white" style={{ background: "#00BFB3" }}>Đăng nhập</button>
              <button onClick={() => navigate("/login")} className="flex-1 py-2 rounded-xl text-sm font-semibold border" style={{ color: "#00BFB3", borderColor: "#00BFB3" }}>Đăng ký</button>
            </div>
          </div>
        )}
      </div>

      {/* Voucher */}
      <div
        className="rounded-2xl p-4 text-white relative overflow-hidden"
        style={{ background: "linear-gradient(135deg, #FF6200, #FF8C00)" }}
      >
        <div className="absolute -right-4 -top-4 w-24 h-24 rounded-full opacity-15" style={{ background: "rgba(255,255,255,0.4)" }} />
        <div className="flex items-center gap-2 mb-2">
          <Gift size={16} className="text-white" />
          <p className="font-bold text-sm">Voucher hôm nay</p>
        </div>
        <p className="text-white/70 text-xs mb-3">Giảm 50k cho đơn từ 299k</p>
        <div className="bg-white/20 rounded-xl px-3 py-2 text-center border border-white/20">
          <span className="font-black text-xl tracking-[0.2em]">VNSHOP50</span>
        </div>
        <p className="text-white/50 text-xs mt-2 text-center">Nhấn để sao chép</p>
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
