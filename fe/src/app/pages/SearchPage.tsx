import { SlidersHorizontal, Star, Truck, X, Zap, Grid3X3, LayoutList, Search } from "lucide-react";
import { motion } from "motion/react";
import { useState, useMemo } from "react";
import { useNavigate, useSearchParams } from "react-router";

import { FacetList } from "../components/facet-list";
import { ImageWithFallback } from "../components/image-with-fallback";
import { useVNShop } from "../components/vnshop-context";
import { products, categories, type Product } from "../components/vnshop-data";
import { useProducts } from "../hooks/use-products";
import { useResettableState } from "../hooks/use-resettable-state";
import { useSearch } from "../hooks/use-search";
import { useSearchFacets } from "../hooks/use-search-facets";
import { formatPrice } from "../lib/format";

function ProductListItem({ product }: { product: Product }) {
  const navigate = useNavigate();
  const { addToCart, toggleWishlist, isWishlisted } = useVNShop();
  const loved = isWishlisted(product.id);
  return (
    <div
      className="flex gap-4 bg-white rounded-2xl p-4 shadow-sm hover:shadow-md transition-all cursor-pointer group"
      onClick={() => navigate(`/product/${product.id}`)}
    >
      <div className="relative shrink-0 w-36 h-36 rounded-xl overflow-hidden">
        <ImageWithFallback
          src={product.image}
          alt={product.name}
          className="w-full h-full object-cover group-hover:scale-105 transition-transform"
        />
        {product.discount ? (
          <span
            className="absolute top-2 left-2 px-1.5 py-0.5 rounded text-white text-[10px] font-bold"
            style={{ background: "#FF6200" }}
          >
            -{product.discount}%
          </span>
        ) : null}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-xs text-gray-400 mb-1">
          {product.sellerName} · {product.location}
        </p>
        <h3 className="font-medium text-gray-800 mb-2 line-clamp-2">{product.name}</h3>
        <div className="flex items-center gap-2 mb-2">
          <div className="flex items-center gap-1">
            {Array.from({ length: 5 }).map((_, i) => (
              <Star
                key={i} // eslint-disable-line react/no-array-index-key -- decorative star rating, no stable id
                size={12}
                className={
                  i < Math.floor(product.rating)
                    ? "text-amber-400 fill-amber-400"
                    : "text-gray-300 fill-gray-200"
                }
              />
            ))}
          </div>
          <span className="text-xs text-gray-500">({product.reviewCount.toLocaleString()})</span>
          <span className="text-xs text-gray-400">• {product.sold.toLocaleString()} đã bán</span>
        </div>
        <p className="text-xs text-gray-500 line-clamp-2 mb-3">
          {product.description.slice(0, 100)}...
        </p>
        <div className="flex items-center gap-2 flex-wrap">
          {product.shippingFee === 0 ? (
            <span
              className="flex items-center gap-1 text-xs px-2 py-0.5 rounded-full font-medium"
              style={{ background: "rgba(0,191,179,0.1)", color: "#00BFB3" }}
            >
              <Truck size={10} /> Miễn phí ship
            </span>
          ) : null}
          {product.colors?.slice(0, 3).map((c) => (
            <span key={c} className="text-xs px-2 py-0.5 rounded-full bg-gray-100 text-gray-600">
              {c}
            </span>
          ))}
        </div>
      </div>
      <div className="shrink-0 flex flex-col items-end justify-between">
        <button
          onClick={(e) => {
            e.stopPropagation();
            toggleWishlist(product.id);
          }}
          className="p-1.5 rounded-lg transition-colors"
          style={{ color: loved ? "#FF6200" : "#9ca3af" }}
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill={loved ? "currentColor" : "none"}
            stroke="currentColor"
            strokeWidth="2"
          >
            <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
          </svg>
        </button>
        <div className="text-right">
          <p className="font-bold text-lg" style={{ color: "#FF6200" }}>
            {formatPrice(product.price)}
          </p>
          {product.originalPrice ? (
            <p className="text-sm text-gray-400 line-through">
              {formatPrice(product.originalPrice)}
            </p>
          ) : null}
        </div>
        <button
          className="px-4 py-2 rounded-xl text-sm font-semibold text-white transition-colors"
          style={{ background: "#00BFB3" }}
          onClick={(e) => {
            e.stopPropagation();
            addToCart(product);
          }}
        >
          Mua ngay
        </button>
      </div>
    </div>
  );
}

function ProductGridCard({ product, index }: { product: Product; index: number }) {
  const navigate = useNavigate();
  const { addToCart, toggleWishlist, isWishlisted } = useVNShop();
  const loved = isWishlisted(product.id);
  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: Math.min(index * 0.04, 0.5) }}
      className="bg-white rounded-2xl overflow-hidden shadow-sm hover:shadow-lg transition-all cursor-pointer group"
      onClick={() => navigate(`/product/${product.id}`)}
    >
      <div className="relative overflow-hidden" style={{ aspectRatio: "1" }}>
        <ImageWithFallback
          src={product.image}
          alt={product.name}
          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
        />
        {product.discount ? (
          <span
            className="absolute top-2 left-2 px-2 py-0.5 rounded-full text-white text-[10px] font-bold"
            style={{ background: "#FF6200" }}
          >
            -{product.discount}%
          </span>
        ) : null}
        <button
          className="absolute top-2 right-2 w-7 h-7 rounded-full flex items-center justify-center shadow opacity-0 group-hover:opacity-100 transition-opacity"
          style={{
            background: loved ? "#FF6200" : "rgba(255,255,255,0.9)",
            color: loved ? "white" : "#6b7280",
          }}
          onClick={(e) => {
            e.stopPropagation();
            toggleWishlist(product.id);
          }}
        >
          <svg
            width="12"
            height="12"
            viewBox="0 0 24 24"
            fill={loved ? "currentColor" : "none"}
            stroke="currentColor"
            strokeWidth="2.5"
          >
            <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
          </svg>
        </button>
        <button
          className="absolute bottom-0 inset-x-0 py-2 text-white text-xs font-semibold opacity-0 group-hover:opacity-100 translate-y-full group-hover:translate-y-0 transition-all duration-200"
          style={{ background: "rgba(0,191,179,0.92)" }}
          onClick={(e) => {
            e.stopPropagation();
            addToCart(product);
          }}
        >
          Thêm vào giỏ
        </button>
      </div>
      <div className="p-3">
        <p className="text-[11px] text-gray-400 truncate">{product.sellerName}</p>
        <h3 className="text-sm font-medium text-gray-800 line-clamp-2 mt-0.5 mb-1.5 leading-snug">
          {product.name}
        </h3>
        <div className="flex items-center gap-1 mb-2">
          <Star size={11} fill="#F59E0B" className="text-amber-400" />
          <span className="text-xs text-gray-700">{product.rating}</span>
          <span className="text-xs text-gray-400">
            (
            {product.reviewCount > 999
              ? `${(product.reviewCount / 1000).toFixed(1)}k`
              : product.reviewCount}
            )
          </span>
        </div>
        <div className="flex items-end justify-between gap-1">
          <div>
            <p className="font-bold text-sm" style={{ color: "#FF6200" }}>
              {formatPrice(product.price)}
            </p>
            {product.originalPrice ? (
              <p className="text-[11px] text-gray-400 line-through">
                {formatPrice(product.originalPrice)}
              </p>
            ) : null}
          </div>
          {product.shippingFee === 0 ? (
            <span className="text-[10px] font-medium" style={{ color: "#00BFB3" }}>
              Free ship
            </span>
          ) : null}
        </div>
      </div>
    </motion.div>
  );
}

export function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const query = searchParams.get("q") ?? "";
  // URL is the source of truth for the selected category — no shadow state.
  const selectedCat = searchParams.get("cat") ?? "";
  const isFlash = searchParams.get("flash") === "true";

  const [localQuery, setLocalQuery] = useState(query);
  const [priceMin, setPriceMin] = useState("");
  const [priceMax, setPriceMax] = useState("");
  const [sortBy, setSortBy] = useState("popular");
  const [minRating, setMinRating] = useState(0);
  const [freeShipOnly, setFreeShipOnly] = useState(false);
  const [selectedBrand, setSelectedBrand] = useState("");
  const [viewMode, setViewMode] = useState<"grid" | "list">("grid");
  const [showFilters, setShowFilters] = useState(false);

  // Filter signature drives a key-based remount of the result window so
  // pagination resets without an effect.
  const filterSignature = `${query}|${selectedCat}|${selectedBrand}|${priceMin}|${priceMax}|${minRating}|${freeShipOnly}|${sortBy}|${isFlash}`;
  const [pageSize, setPageSize] = useResettableState(20, filterSignature);

  // Helper: replace `cat` in the URL while preserving other params.
  const setCategory = (next: string) => {
    setSearchParams((prev) => {
      const params = new URLSearchParams(prev);
      if (next) params.set("cat", next);
      else params.delete("cat");
      return params;
    });
  };

  // Backend search runs when there's a query OR a category. Empty results are kept
  // (the user really did search and got nothing back). We only fall through to the local
  // catalog when search wasn't enabled or the backend failed outright.
  const searchEnabled = !!(query || selectedCat || selectedBrand);
  const search = useSearch(
    {
      q: query || undefined,
      category: selectedCat || undefined,
      brand: selectedBrand || undefined,
      minPrice: priceMin ? Number(priceMin) * 1000 : undefined,
      maxPrice: priceMax ? Number(priceMax) * 1000 : undefined,
      sort: sortBy === "popular" ? undefined : sortBy,
      size: pageSize,
    },
    searchEnabled,
  );

  // BE-driven facet counts for the sidebar. Only fetched when there's
  // something to facet against; the hook short-circuits otherwise.
  const { facets } = useSearchFacets({
    q: query || undefined,
    category: selectedCat || undefined,
    brand: selectedBrand || undefined,
    minPrice: priceMin ? Number(priceMin) * 1000 : undefined,
    maxPrice: priceMax ? Number(priceMax) * 1000 : undefined,
    enabled: searchEnabled,
  });

  const { data: localCatalog = products } = useProducts();
  // With keepPreviousData on the search hook, isLoading is only true on the
  // very first fetch (before any successful data) — so we can safely treat
  // a non-error successful query as "use backend" without flicker on refetch.
  const usedBackend = searchEnabled && !search.error;
  const catalog = usedBackend ? search.products : localCatalog;

  const filtered = useMemo(() => {
    let list = [...catalog];
    if (isFlash) list = list.filter((p) => (p.discount ?? 0) >= 20 || p.badge === "flash");
    // Backend already applied q + category — skip those client-side filters.
    if (!usedBackend) {
      if (selectedCat) list = list.filter((p) => p.category === selectedCat);
      if (query) {
        const q = query.toLowerCase();
        list = list.filter(
          (p) =>
            p.name.toLowerCase().includes(q) ||
            p.description.toLowerCase().includes(q) ||
            p.tags.some((t) => t.includes(q)),
        );
      }
    }
    if (priceMin) list = list.filter((p) => p.price >= Number(priceMin) * 1000);
    if (priceMax) list = list.filter((p) => p.price <= Number(priceMax) * 1000);
    if (minRating > 0) list = list.filter((p) => p.rating >= minRating);
    if (freeShipOnly) list = list.filter((p) => p.shippingFee === 0);
    switch (sortBy) {
      case "price-low":
        list.sort((a, b) => a.price - b.price);
        break;
      case "price-high":
        list.sort((a, b) => b.price - a.price);
        break;
      case "rating":
        list.sort((a, b) => b.rating - a.rating);
        break;
      case "newest":
        list.sort((a, b) => (b.badge === "new" ? 1 : 0) - (a.badge === "new" ? 1 : 0));
        break;
      default:
        list.sort((a, b) => b.sold - a.sold);
    }
    return list;
  }, [
    catalog,
    usedBackend,
    query,
    selectedCat,
    priceMin,
    priceMax,
    minRating,
    freeShipOnly,
    sortBy,
    isFlash,
  ]);

  const paginated = filtered.slice(0, pageSize);
  const remaining = Math.max(0, filtered.length - pageSize);

  const clearFilters = () => {
    setPriceMin("");
    setPriceMax("");
    setMinRating(0);
    setFreeShipOnly(false);
    setSelectedBrand("");
    setSearchParams({});
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (localQuery.trim()) setSearchParams({ q: localQuery.trim() });
  };

  const activeFilterCount = [
    selectedCat,
    selectedBrand,
    priceMin,
    priceMax,
    minRating > 0,
    freeShipOnly,
  ].filter(Boolean).length;

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      {/* Search bar */}
      <form onSubmit={handleSearch} className="flex gap-3 mb-6">
        <div className="flex-1 flex items-center bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
          <Search size={18} className="ml-4 text-gray-400 shrink-0" />
          <input
            value={localQuery}
            onChange={(e) => setLocalQuery(e.target.value)}
            placeholder="Tìm kiếm sản phẩm..."
            className="flex-1 px-3 py-3 text-sm outline-none bg-transparent"
          />
          {localQuery ? (
            <button
              type="button"
              onClick={() => {
                setLocalQuery("");
                setSearchParams({});
              }}
              className="pr-3 text-gray-400"
            >
              <X size={16} />
            </button>
          ) : null}
        </div>
        <button
          type="submit"
          className="px-6 py-3 rounded-xl text-white font-semibold text-sm"
          style={{ background: "#00BFB3" }}
        >
          Tìm kiếm
        </button>
      </form>

      {/* Category pills */}
      <div className="flex gap-2 mb-6 overflow-x-auto pb-1 scrollbar-hide">
        <button
          onClick={() => setCategory("")}
          className="shrink-0 px-4 py-2 rounded-full text-sm font-medium transition-all"
          style={
            !selectedCat
              ? { background: "#00BFB3", color: "#fff" }
              : { background: "#fff", color: "#6b7280", border: "1px solid #e5e7eb" }
          }
        >
          Tất cả
        </button>
        {categories.map((cat) => (
          <button
            key={cat.id}
            onClick={() => setCategory(selectedCat === cat.id ? "" : cat.id)}
            className="shrink-0 flex items-center gap-1.5 px-4 py-2 rounded-full text-sm font-medium transition-all"
            style={
              selectedCat === cat.id
                ? { background: "#00BFB3", color: "#fff" }
                : { background: "#fff", color: "#6b7280", border: "1px solid #e5e7eb" }
            }
          >
            <span>{cat.emoji}</span> {cat.label}
          </button>
        ))}
      </div>

      <div className="flex gap-6">
        {/* Sidebar filters */}
        <aside className={`shrink-0 w-56 space-y-5 ${showFilters ? "block" : "hidden lg:block"}`}>
          <div className="bg-white rounded-2xl p-5 shadow-sm">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-bold text-gray-800">Bộ lọc</h3>
              {activeFilterCount > 0 ? (
                <button
                  onClick={clearFilters}
                  className="text-xs font-medium"
                  style={{ color: "#FF6200" }}
                >
                  Xóa tất cả
                </button>
              ) : null}
            </div>

            {/* Sort */}
            <div className="mb-5">
              <p className="text-sm font-semibold text-gray-700 mb-2">Sắp xếp</p>
              {[
                { v: "popular", l: "Phổ biến nhất" },
                { v: "rating", l: "Đánh giá cao" },
                { v: "price-low", l: "Giá: Thấp → Cao" },
                { v: "price-high", l: "Giá: Cao → Thấp" },
                { v: "newest", l: "Mới nhất" },
              ].map((opt) => (
                <button
                  key={opt.v}
                  onClick={() => setSortBy(opt.v)}
                  className="w-full text-left text-sm py-1.5 flex items-center gap-2"
                  style={{ color: sortBy === opt.v ? "#00BFB3" : "#4b5563" }}
                >
                  <div
                    className="w-4 h-4 rounded-full border-2 flex items-center justify-center shrink-0"
                    style={{ borderColor: sortBy === opt.v ? "#00BFB3" : "#d1d5db" }}
                  >
                    {sortBy === opt.v ? (
                      <div className="w-2 h-2 rounded-full" style={{ background: "#00BFB3" }} />
                    ) : null}
                  </div>
                  {opt.l}
                </button>
              ))}
            </div>

            {/* Price range */}
            <div className="mb-5">
              <p className="text-sm font-semibold text-gray-700 mb-2">Khoảng giá (nghìn đ)</p>
              <div className="flex gap-2">
                <input
                  value={priceMin}
                  onChange={(e) => setPriceMin(e.target.value)}
                  placeholder="Từ"
                  type="number"
                  className="flex-1 px-3 py-2 border border-gray-200 rounded-lg text-xs outline-none focus:border-[#00BFB3]"
                />
                <input
                  value={priceMax}
                  onChange={(e) => setPriceMax(e.target.value)}
                  placeholder="Đến"
                  type="number"
                  className="flex-1 px-3 py-2 border border-gray-200 rounded-lg text-xs outline-none focus:border-[#00BFB3]"
                />
              </div>
              {[
                { label: "Dưới 100k", min: "", max: "100" },
                { label: "100k–500k", min: "100", max: "500" },
                { label: "500k–2tr", min: "500", max: "2000" },
                { label: "Trên 2tr", min: "2000", max: "" },
              ].map((range) => (
                <button
                  key={range.label}
                  onClick={() => {
                    setPriceMin(range.min);
                    setPriceMax(range.max);
                  }}
                  className="mt-1 mr-1 px-2.5 py-1 rounded-full text-xs border transition-colors"
                  style={{
                    borderColor:
                      priceMin === range.min && priceMax === range.max ? "#00BFB3" : "#e5e7eb",
                    color: priceMin === range.min && priceMax === range.max ? "#00BFB3" : "#6b7280",
                    background:
                      priceMin === range.min && priceMax === range.max
                        ? "rgba(0,191,179,0.08)"
                        : "transparent",
                  }}
                >
                  {range.label}
                </button>
              ))}
            </div>

            {/* Rating */}
            <div className="mb-5">
              <p className="text-sm font-semibold text-gray-700 mb-2">Đánh giá</p>
              {[4, 3, 2].map((r) => (
                <button
                  key={r}
                  onClick={() => setMinRating(minRating === r ? 0 : r)}
                  className="w-full flex items-center gap-2 py-1.5 text-sm"
                  style={{ color: minRating === r ? "#00BFB3" : "#4b5563" }}
                >
                  <div className="flex">
                    {Array.from({ length: 5 }).map((_, i) => (
                      <Star
                        key={i} // eslint-disable-line react/no-array-index-key -- decorative star rating, no stable id
                        size={13}
                        fill={i < r ? "#F59E0B" : "#e5e7eb"}
                        className="text-amber-400"
                      />
                    ))}
                  </div>
                  <span>từ {r} ★</span>
                </button>
              ))}
            </div>

            {/* Free shipping */}
            <div>
              <button
                onClick={() => setFreeShipOnly(!freeShipOnly)}
                className="flex items-center gap-3 text-sm"
              >
                <div
                  className="w-10 h-5 rounded-full flex items-center transition-all duration-200 px-0.5"
                  style={{ background: freeShipOnly ? "#00BFB3" : "#d1d5db" }}
                >
                  <div
                    className="w-4 h-4 rounded-full bg-white shadow transition-transform duration-200"
                    style={{ transform: freeShipOnly ? "translateX(20px)" : "none" }}
                  />
                </div>
                <span className="text-gray-700">Miễn phí vận chuyển</span>
              </button>
            </div>

            {/* Brand + category facets — driven by /search/facets so the
                options reflect the current query and other filters. Each
                axis hides itself when the BE returned no entries (e.g. on
                the welcome state before the user has searched). */}
            <FacetList
              title="Thương hiệu"
              entries={facets.brands}
              selected={selectedBrand}
              onToggle={(key) => setSelectedBrand(selectedBrand === key ? "" : key)}
            />
            <FacetList
              title="Danh mục"
              entries={facets.categories}
              selected={selectedCat}
              onToggle={(key) => setCategory(selectedCat === key ? "" : key)}
              formatLabel={(key) => categories.find((c) => c.id === key)?.label ?? key}
            />
          </div>
        </aside>

        {/* Main content */}
        <div className="flex-1 min-w-0">
          {/* Result header */}
          <div className="flex items-center justify-between mb-4">
            <div>
              <p className="text-gray-800 font-medium">
                {isFlash ? (
                  <span className="inline-flex items-center gap-1 mr-2 text-red-500 font-bold">
                    <Zap size={16} fill="currentColor" /> Flash Sale
                  </span>
                ) : null}
                {query ? `Kết quả cho "${query}"` : "Tất cả sản phẩm"}
              </p>
              <p className="text-sm text-gray-500 mt-0.5">
                {(usedBackend ? search.totalElements : filtered.length).toLocaleString("vi-VN")} sản
                phẩm được tìm thấy
              </p>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setShowFilters(!showFilters)}
                className="lg:hidden flex items-center gap-2 px-3 py-2 rounded-xl border border-gray-200 bg-white text-sm font-medium"
              >
                <SlidersHorizontal size={16} />
                Lọc {activeFilterCount > 0 ? `(${activeFilterCount})` : null}
              </button>
              <div className="hidden sm:flex border border-gray-200 rounded-xl overflow-hidden bg-white">
                <button
                  onClick={() => setViewMode("grid")}
                  className="p-2.5 transition-colors"
                  style={{
                    background: viewMode === "grid" ? "#00BFB3" : "transparent",
                    color: viewMode === "grid" ? "white" : "#6b7280",
                  }}
                >
                  <Grid3X3 size={16} />
                </button>
                <button
                  onClick={() => setViewMode("list")}
                  className="p-2.5 transition-colors"
                  style={{
                    background: viewMode === "list" ? "#00BFB3" : "transparent",
                    color: viewMode === "list" ? "white" : "#6b7280",
                  }}
                >
                  <LayoutList size={16} />
                </button>
              </div>
              <select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value)}
                className="px-3 py-2 border border-gray-200 rounded-xl bg-white text-sm outline-none"
              >
                <option value="popular">Phổ biến</option>
                <option value="rating">Đánh giá</option>
                <option value="price-low">Giá tăng dần</option>
                <option value="price-high">Giá giảm dần</option>
                <option value="newest">Mới nhất</option>
              </select>
            </div>
          </div>

          {/* Active filter chips */}
          {activeFilterCount > 0 ? (
            <div className="flex flex-wrap gap-2 mb-4">
              {selectedCat ? (
                <span
                  className="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium text-white"
                  style={{ background: "#00BFB3" }}
                >
                  {categories.find((c) => c.id === selectedCat)?.label}
                  <button onClick={() => setCategory("")}>
                    <X size={12} />
                  </button>
                </span>
              ) : null}
              {selectedBrand ? (
                <span
                  className="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium text-white"
                  style={{ background: "#00BFB3" }}
                >
                  {selectedBrand}
                  <button onClick={() => setSelectedBrand("")}>
                    <X size={12} />
                  </button>
                </span>
              ) : null}
              {priceMin || priceMax ? (
                <span
                  className="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium text-white"
                  style={{ background: "#00BFB3" }}
                >
                  {priceMin ? `${priceMin}k` : "0"} – {priceMax ? `${priceMax}k` : "∞"}
                  <button
                    onClick={() => {
                      setPriceMin("");
                      setPriceMax("");
                    }}
                  >
                    <X size={12} />
                  </button>
                </span>
              ) : null}
              {minRating > 0 ? (
                <span
                  className="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium text-white"
                  style={{ background: "#00BFB3" }}
                >
                  ≥ {minRating}★
                  <button onClick={() => setMinRating(0)}>
                    <X size={12} />
                  </button>
                </span>
              ) : null}
              {freeShipOnly ? (
                <span
                  className="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium text-white"
                  style={{ background: "#00BFB3" }}
                >
                  Miễn phí ship
                  <button onClick={() => setFreeShipOnly(false)}>
                    <X size={12} />
                  </button>
                </span>
              ) : null}
            </div>
          ) : null}

          {/* Empty state */}
          {paginated.length === 0 ? (
            <div className="py-24 text-center bg-white rounded-2xl">
              <Search size={48} className="mx-auto mb-4 text-gray-300" />
              <h3 className="text-lg font-semibold text-gray-600 mb-2">Không tìm thấy sản phẩm</h3>
              <p className="text-sm text-gray-400 mb-6">
                Thử điều chỉnh bộ lọc hoặc từ khóa tìm kiếm
              </p>
              <button
                onClick={clearFilters}
                className="px-6 py-2.5 rounded-xl text-white text-sm font-semibold"
                style={{ background: "#00BFB3" }}
              >
                Xóa bộ lọc
              </button>
            </div>
          ) : viewMode === "grid" ? (
            <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-4 gap-4">
              {paginated.map((p, i) => (
                <ProductGridCard key={p.id} product={p} index={i} />
              ))}
            </div>
          ) : (
            <div className="space-y-3">
              {paginated.map((p) => (
                <ProductListItem key={p.id} product={p} />
              ))}
            </div>
          )}

          {/* Load more */}
          {remaining > 0 ? (
            <div className="mt-8 text-center">
              <button
                onClick={() => setPageSize((s) => s + 20)}
                className="px-8 py-3 rounded-full border-2 text-sm font-semibold transition-all hover:text-white hover:bg-[#00BFB3]"
                style={{ borderColor: "#00BFB3", color: "#00BFB3" }}
              >
                Xem thêm {Math.min(20, remaining)} sản phẩm
              </button>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}
