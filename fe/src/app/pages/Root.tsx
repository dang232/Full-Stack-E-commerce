import {
  ShoppingCart,
  Heart,
  Bell,
  Sun,
  Moon,
  Menu,
  X,
  Home,
  Package,
  User,
  LogOut,
  Settings,
  Store,
  LayoutDashboard,
  ChevronDown,
  Sparkles,
  Phone,
  MapPin,
  Tag,
  Headphones,
} from "lucide-react";
import { motion, AnimatePresence } from "motion/react";
import { useState } from "react";
import { Outlet, useNavigate, useLocation } from "react-router";

import { ImageWithFallback } from "../components/image-with-fallback";
import { NotificationBell } from "../components/notification-bell";
import { SearchAutocomplete } from "../components/search-autocomplete";
import { useVNShop } from "../components/vnshop-context";
import { useCart } from "../hooks/use-cart";
import { useSearchSuggestions } from "../hooks/use-search-suggestions";
import { useWishlist } from "../hooks/use-wishlist";

function Navbar() {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, isLoggedIn, logout, isDark, toggleTheme } = useVNShop();
  const { itemCount: cartCount } = useCart();
  const { ids: wishlist } = useWishlist();
  const [searchQ, setSearchQ] = useState("");
  const { suggestions } = useSearchSuggestions(searchQ);
  const [menuOpen, setMenuOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);

  const submitSearch = (q: string) => {
    void navigate(`/search?q=${encodeURIComponent(q)}`);
  };

  const navLinks = [
    { label: "Trang Chủ", path: "/" },
    { label: "Flash Sale", path: "/search?flash=true" },
    { label: "Siêu Thị", path: "/search?cat=home" },
    { label: "Thời Trang", path: "/search?cat=fashion" },
    { label: "Điện Tử", path: "/search?cat=electronics" },
  ];

  return (
    <header
      className="sticky top-0 z-50 w-full"
      style={{ background: isDark ? "#0f1117" : "linear-gradient(to right, #00BFB3, #009990)" }}
    >
      {/* Top bar */}
      <div className="border-b border-white/10 hidden md:block">
        <div className="max-w-7xl mx-auto px-4 py-1.5 flex items-center justify-between text-white/80 text-xs">
          <div className="flex items-center gap-6">
            <span className="flex items-center gap-1.5">
              <Phone size={11} /> 1800 6789 (Miễn phí)
            </span>
            <span className="flex items-center gap-1.5">
              <MapPin size={11} /> Giao hàng toàn quốc
            </span>
          </div>
          <div className="flex items-center gap-5">
            <button
              onClick={() => navigate("/seller")}
              className="flex items-center gap-1 hover:text-white transition-colors"
            >
              <Store size={11} /> Kênh Người Bán
            </button>
            <button
              onClick={() => navigate("/admin")}
              className="flex items-center gap-1 hover:text-white transition-colors"
            >
              <LayoutDashboard size={11} /> Admin
            </button>
            <span className="w-px h-3 bg-white/20" />
            <button
              onClick={toggleTheme}
              className="flex items-center gap-1 hover:text-white transition-colors"
            >
              {isDark ? <Sun size={11} /> : <Moon size={11} />}
              {isDark ? "Sáng" : "Tối"}
            </button>
          </div>
        </div>
      </div>

      {/* Main navbar */}
      <div className="max-w-7xl mx-auto px-4 py-3">
        <div className="flex items-center gap-4">
          {/* Logo */}
          <button onClick={() => navigate("/")} className="flex items-center gap-2.5 shrink-0">
            <div
              className="w-9 h-9 rounded-xl flex items-center justify-center shadow-lg"
              style={{ background: "rgba(255,255,255,0.2)" }}
            >
              <Sparkles className="w-5 h-5 text-white" />
            </div>
            <div>
              <span
                className="text-white font-bold text-xl tracking-tight"
                style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
              >
                VNShop
              </span>
              <div className="text-white/60 text-[9px] leading-none tracking-widest">
                MARKETPLACE
              </div>
            </div>
          </button>

          {/* Search */}
          <SearchAutocomplete
            className="flex-1 max-w-2xl hidden sm:flex"
            value={searchQ}
            onValueChange={setSearchQ}
            suggestions={suggestions}
            onSubmit={submitSearch}
            placeholder="Tìm kiếm sản phẩm, thương hiệu, shop..."
          />

          {/* Right actions */}
          <div className="flex items-center gap-1 ml-auto sm:ml-0 shrink-0">
            <button
              onClick={() => navigate("/wishlist")}
              className="relative p-2 text-white rounded-lg hover:bg-white/10 transition-colors"
              title="Yêu thích"
            >
              <Heart size={22} />
              {wishlist.length > 0 ? (
                <span className="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-0.5 rounded-full bg-red-500 text-white text-[10px] font-bold flex items-center justify-center">
                  {wishlist.length}
                </span>
              ) : null}
            </button>

            <button
              onClick={() => navigate("/cart")}
              className="relative p-2 text-white rounded-lg hover:bg-white/10 transition-colors"
              title="Giỏ hàng"
            >
              <ShoppingCart size={22} />
              {cartCount > 0 ? (
                <span
                  className="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-0.5 rounded-full text-white text-[10px] font-bold flex items-center justify-center"
                  style={{ background: "#FF6200" }}
                >
                  {cartCount}
                </span>
              ) : null}
            </button>

            <NotificationBell />

            {/* User menu */}
            {isLoggedIn ? (
              <div className="relative ml-1">
                <button
                  onClick={() => setUserMenuOpen((o) => !o)}
                  className="flex items-center gap-2 pl-2 pr-3 py-1.5 rounded-xl text-white hover:bg-white/10 transition-colors"
                >
                  <ImageWithFallback
                    src={user?.avatar ?? ""}
                    alt={user?.name ?? ""}
                    className="w-8 h-8 rounded-full object-cover border-2 border-white/30"
                    placeholder={
                      <div
                        className="w-full h-full rounded-full flex items-center justify-center text-xs font-bold text-white"
                        style={{ background: "rgba(255,255,255,0.2)" }}
                      >
                        {user?.name?.charAt(0)?.toUpperCase() ?? "?"}
                      </div>
                    }
                  />
                  <span className="hidden md:block text-sm font-medium max-w-[80px] truncate">
                    {user?.name?.split(" ").pop()}
                  </span>
                  <ChevronDown size={14} className="hidden md:block" />
                </button>
                <AnimatePresence>
                  {userMenuOpen ? (
                    <motion.div
                      initial={{ opacity: 0, y: 8, scale: 0.95 }}
                      animate={{ opacity: 1, y: 0, scale: 1 }}
                      exit={{ opacity: 0, y: 8, scale: 0.95 }}
                      transition={{ duration: 0.15 }}
                      className="absolute right-0 top-full mt-2 w-52 rounded-2xl shadow-2xl border overflow-hidden z-50"
                      style={{
                        background: isDark ? "#1a1d2b" : "#fff",
                        borderColor: isDark ? "#2a2d3b" : "#e5e7eb",
                      }}
                    >
                      <div
                        className="p-4 border-b"
                        style={{ borderColor: isDark ? "#2a2d3b" : "#e5e7eb" }}
                      >
                        <p
                          className="font-semibold text-sm"
                          style={{ color: isDark ? "#fff" : "#111" }}
                        >
                          {user?.name}
                        </p>
                        <p
                          className="text-xs mt-0.5"
                          style={{ color: isDark ? "#9ca3af" : "#6b7280" }}
                        >
                          {user?.email}
                        </p>
                      </div>
                      {[
                        { icon: User, label: "Tài khoản của tôi", path: "/profile" },
                        { icon: Package, label: "Đơn mua", path: "/orders" },
                        { icon: Heart, label: "Yêu thích", path: "/wishlist" },
                        { icon: Bell, label: "Thông báo", path: "#" },
                        { icon: Settings, label: "Cài đặt", path: "#" },
                      ].map((item) => (
                        <button
                          key={item.label}
                          onClick={() => {
                            void navigate(item.path);
                            setUserMenuOpen(false);
                          }}
                          className="w-full flex items-center gap-3 px-4 py-2.5 text-sm hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors text-left"
                          style={{ color: isDark ? "#d1d5db" : "#374151" }}
                        >
                          <item.icon size={16} style={{ color: "#00BFB3" }} />
                          {item.label}
                        </button>
                      ))}
                      <div
                        className="border-t"
                        style={{ borderColor: isDark ? "#2a2d3b" : "#e5e7eb" }}
                      >
                        <button
                          onClick={() => {
                            logout();
                            setUserMenuOpen(false);
                            void navigate("/");
                          }}
                          className="w-full flex items-center gap-3 px-4 py-2.5 text-sm hover:bg-red-50 transition-colors text-left text-red-500"
                        >
                          <LogOut size={16} /> Đăng xuất
                        </button>
                      </div>
                    </motion.div>
                  ) : null}
                </AnimatePresence>
              </div>
            ) : (
              <button
                onClick={() => navigate("/login")}
                className="ml-1 px-4 py-2 rounded-xl text-sm font-semibold transition-colors"
                style={{ background: "#FF6200", color: "#fff" }}
              >
                Đăng nhập
              </button>
            )}

            <button className="md:hidden p-2 text-white" onClick={() => setMenuOpen((o) => !o)}>
              {menuOpen ? <X size={22} /> : <Menu size={22} />}
            </button>
          </div>
        </div>

        {/* Nav links */}
        <nav className="hidden md:flex items-center gap-1 mt-2.5">
          {navLinks.map((link) => (
            <button
              key={link.path}
              onClick={() => navigate(link.path)}
              className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                location.pathname === link.path
                  ? "bg-white/20 text-white"
                  : "text-white/80 hover:text-white hover:bg-white/10"
              }`}
            >
              {link.label}
            </button>
          ))}
          <button
            onClick={() => navigate("/search")}
            className="ml-auto flex items-center gap-1.5 px-4 py-1.5 rounded-lg text-sm font-medium text-white/80 hover:text-white hover:bg-white/10 transition-colors"
          >
            <Tag size={14} />
            <span>Tất cả danh mục</span>
          </button>
          <button className="flex items-center gap-1.5 px-4 py-1.5 rounded-lg text-sm font-medium text-white/80 hover:text-white hover:bg-white/10 transition-colors">
            <Headphones size={14} />
            <span>Hỗ trợ</span>
          </button>
        </nav>
      </div>

      {/* Mobile menu */}
      <AnimatePresence>
        {menuOpen ? (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: "auto" }}
            exit={{ opacity: 0, height: 0 }}
            className="md:hidden border-t border-white/10 overflow-hidden"
            style={{ background: isDark ? "#0f1117" : "rgba(0,153,144,0.98)" }}
          >
            <div className="p-4 space-y-1">
              <SearchAutocomplete
                className="mb-3"
                value={searchQ}
                onValueChange={setSearchQ}
                suggestions={suggestions}
                onSubmit={(q) => {
                  submitSearch(q);
                  setMenuOpen(false);
                }}
                placeholder="Tìm kiếm..."
              />
              {[
                { icon: Home, label: "Trang Chủ", path: "/" },
                { icon: Package, label: "Đơn mua", path: "/orders" },
                { icon: Heart, label: "Yêu thích", path: "/wishlist" },
                { icon: User, label: "Tài khoản", path: "/profile" },
                { icon: Store, label: "Kênh Người Bán", path: "/seller" },
              ].map((item) => (
                <button
                  key={item.path}
                  onClick={() => {
                    void navigate(item.path);
                    setMenuOpen(false);
                  }}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-white/90 hover:bg-white/10 transition-colors text-sm font-medium text-left"
                >
                  <item.icon size={18} />
                  {item.label}
                </button>
              ))}
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </header>
  );
}

export function Root() {
  const navigate = useNavigate();
  const { isDark } = useVNShop();
  return (
    <div className="min-h-screen" style={{ background: isDark ? "#0f1117" : "#f4f6f9" }}>
      <Navbar />
      <main>
        <Outlet />
      </main>
      {/* Footer */}
      <footer
        className="mt-16 border-t"
        style={{
          background: isDark ? "#0a0c12" : "#1a1d2b",
          borderColor: isDark ? "#2a2d3b" : "#2a2d3b",
        }}
      >
        <div className="max-w-7xl mx-auto px-4 py-12">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-8">
            <div>
              <div className="flex items-center gap-2 mb-4">
                <Sparkles className="w-5 h-5" style={{ color: "#00BFB3" }} />
                <span
                  className="text-white font-bold text-lg"
                  style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
                >
                  VNShop
                </span>
              </div>
              <p className="text-gray-400 text-sm leading-relaxed">
                Nền tảng mua sắm trực tuyến hàng đầu Việt Nam. Uy tín - Nhanh chóng - Tiết kiệm.
              </p>
              <div className="flex gap-3 mt-4">
                {["fb", "ig", "tw", "yt"].map((s) => (
                  <div
                    key={s}
                    className="w-8 h-8 rounded-lg flex items-center justify-center text-xs font-bold text-white cursor-pointer hover:opacity-80 transition-opacity"
                    style={{ background: "#00BFB3" }}
                  >
                    {s.toUpperCase()}
                  </div>
                ))}
              </div>
            </div>
            {[
              {
                title: "Hỗ trợ khách hàng",
                links: [
                  "Trung tâm hỗ trợ",
                  "Chính sách bảo hành",
                  "Hướng dẫn mua hàng",
                  "Phương thức thanh toán",
                  "Vận chuyển & Giao nhận",
                ],
              },
              {
                title: "VNShop",
                links: [
                  "Giới thiệu",
                  "Tuyển dụng",
                  "Điều khoản dịch vụ",
                  "Chính sách bảo mật",
                  "Blog & Tin tức",
                ],
              },
              {
                title: "Thanh toán & Giao hàng",
                links: ["VNPay", "MoMo", "ZaloPay", "Thẻ ngân hàng", "Giao Hàng Nhanh"],
              },
            ].map((col) => (
              <div key={col.title}>
                <h4 className="text-white font-semibold mb-4 text-sm">{col.title}</h4>
                <ul className="space-y-2.5">
                  {col.links.map((link) => (
                    <li key={link}>
                      <button className="text-gray-400 text-sm hover:text-white transition-colors text-left">
                        {link}
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
          <div className="mt-10 pt-6 border-t border-gray-700 flex flex-col sm:flex-row items-center justify-between gap-4">
            <p className="text-gray-500 text-xs">
              © 2026 VNShop. Được vận hành bởi VNShop JSC. ĐKKD số: 0123456789
            </p>
            <div className="flex items-center gap-3">
              <button
                onClick={() => navigate("/design-system")}
                className="px-3 py-1.5 rounded-lg border border-gray-700 text-gray-500 text-xs hover:border-teal-600 hover:text-teal-400 transition-colors"
              >
                🎨 Design System
              </button>
              {["DMCA", "BoCongThuong", "SSL"].map((b) => (
                <div
                  key={b}
                  className="px-3 py-1.5 rounded-lg border border-gray-700 text-gray-500 text-xs"
                >
                  {b}
                </div>
              ))}
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
