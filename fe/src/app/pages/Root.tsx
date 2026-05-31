import { IconShoppingCart, IconHeart, IconBell, IconSun, IconMoon, IconMenu2, IconX, IconHome, IconPackage, IconUser, IconLogout, IconSettings, IconBuildingStore, IconLayoutDashboard, IconChevronDown, IconSparkles, IconPhone, IconMapPin, IconTag, IconHeadphones, IconPalette } from "@tabler/icons-react";
import { motion, AnimatePresence } from "motion/react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Outlet, useNavigate, useLocation } from "react-router";

import { ImageWithFallback } from "../components/image-with-fallback";
import { ConsoleChrome } from "../components/console-chrome";
import { LanguageSwitcher } from "../components/language-switcher";
import { NotificationBell } from "../components/notification-bell";
import { SearchAutocomplete } from "../components/search-autocomplete";
import { useVNShop } from "../components/vnshop-context";
import { useCart } from "../hooks/use-cart";
import { useSearchSuggestions } from "../hooks/use-search-suggestions";
import { useWishlist } from "../hooks/use-wishlist";
import { comingSoon } from "../lib/ui/coming-soon";

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
  const { t } = useTranslation();

  const submitSearch = (q: string) => {
    void navigate(`/search?q=${encodeURIComponent(q)}`);
  };

  const navLinks = [
    { label: t("nav.home"), path: "/" },
    { label: t("nav.flashSale"), path: "/search?flash=true" },
    { label: t("nav.supermarket"), path: "/search?cat=home" },
    { label: t("nav.fashion"), path: "/search?cat=fashion" },
    { label: t("nav.electronics"), path: "/search?cat=electronics" },
  ];

  return (
    <header
      className="sticky top-0 z-50 w-full bg-gradient-to-r from-[#00BFB3] to-[#009990] dark:from-[#0d3d3a] dark:to-[#062523]"
    >
      {/* Top bar */}
      <div className="border-b border-white/10 hidden md:block">
        <div className="max-w-7xl mx-auto px-4 py-1.5 flex items-center justify-between text-white/80 text-xs">
          <div className="flex items-center gap-6">
            <span className="flex items-center gap-1.5">
              <IconPhone size={11} /> {t("nav.freePhone")}
            </span>
            <span className="flex items-center gap-1.5">
              <IconMapPin size={11} /> {t("nav.nationwide")}
            </span>
          </div>
          <div className="flex items-center gap-5">
            <button
              onClick={() => navigate("/seller")}
              className="flex items-center gap-1 hover:text-white transition-colors"
            >
              <IconBuildingStore size={11} /> {t("nav.sellerChannel")}
            </button>
            <button
              onClick={() => navigate("/admin")}
              className="flex items-center gap-1 hover:text-white transition-colors"
            >
              <IconLayoutDashboard size={11} /> {t("nav.admin")}
            </button>
            <span className="w-px h-3 bg-white/20" />
            <button
              onClick={toggleTheme}
              className="flex items-center gap-1 hover:text-white transition-colors"
            >
              {isDark ? <IconSun size={11} /> : <IconMoon size={11} />}
              {isDark ? t("nav.light") : t("nav.dark")}
            </button>
            <LanguageSwitcher />
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
              <IconSparkles className="w-5 h-5 text-white" />
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
            placeholder={t("search.placeholder")}
          />

          {/* Right actions */}
          <div className="flex items-center gap-1 ml-auto sm:ml-0 shrink-0">
            <button
              onClick={() => navigate("/wishlist")}
              className="relative p-2 text-white rounded-lg hover:bg-white/10 transition-colors"
              title={t("auth.wishlist")}
            >
              <IconHeart size={22} />
              {wishlist.length > 0 ? (
                <span className="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-0.5 rounded-full bg-red-500 text-white text-[10px] font-bold flex items-center justify-center">
                  {wishlist.length}
                </span>
              ) : null}
            </button>

            <button
              onClick={() => navigate("/cart")}
              className="relative p-2 text-white rounded-lg hover:bg-white/10 transition-colors"
              title={t("cart.title")}
            >
              <IconShoppingCart size={22} />
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

            {/* IconUser menu */}
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
                  <IconChevronDown size={14} className="hidden md:block" />
                </button>
                <AnimatePresence>
                  {userMenuOpen ? (
                    <motion.div
                      initial={{ opacity: 0, y: 8, scale: 0.95 }}
                      animate={{ opacity: 1, y: 0, scale: 1 }}
                      exit={{ opacity: 0, y: 8, scale: 0.95 }}
                      transition={{ duration: 0.15 }}
                      className="absolute right-0 top-full mt-2 w-52 rounded-2xl shadow-2xl border border-border bg-card overflow-hidden z-50"
                    >
                      <div className="p-4 border-b border-border">
                        <p className="font-semibold text-sm text-foreground">{user?.name}</p>
                        <p className="text-xs mt-0.5 text-muted-foreground">{user?.email}</p>
                      </div>
                      {[
                        { icon: IconUser, label: t("auth.myAccount"), path: "/profile" },
                        { icon: IconPackage, label: t("auth.myOrders"), path: "/orders" },
                        { icon: IconHeart, label: t("auth.wishlist"), path: "/wishlist" },
                        { icon: IconBell, label: t("auth.notifications"), path: "/notifications" },
                        { icon: IconSettings, label: t("auth.settings"), path: "#", action: () => comingSoon("Settings") },
                      ].map((item) => (
                        <button
                          key={item.label}
                          onClick={() => {
                            if (item.action) {
                              item.action();
                            } else {
                              void navigate(item.path);
                            }
                            setUserMenuOpen(false);
                          }}
                          className="w-full flex items-center gap-3 px-4 py-2.5 text-sm hover:bg-muted transition-colors text-left text-foreground"
                        >
                          <item.icon size={16} style={{ color: "#00BFB3" }} />
                          {item.label}
                        </button>
                      ))}
                      <div className="border-t border-border">
                        <button
                          onClick={() => {
                            logout();
                            setUserMenuOpen(false);
                            void navigate("/");
                          }}
                          className="w-full flex items-center gap-3 px-4 py-2.5 text-sm hover:bg-red-500/10 transition-colors text-left text-red-500"
                        >
                          <IconLogout size={16} /> {t("auth.logout")}
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
                {t("auth.login")}
              </button>
            )}

            <button className="md:hidden p-2 text-white" onClick={() => setMenuOpen((o) => !o)}>
              {menuOpen ? <IconX size={22} /> : <IconMenu2 size={22} />}
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
            <IconTag size={14} />
            <span>{t("nav.allCategories")}</span>
          </button>
          <button
            onClick={() => comingSoon("Support center")}
            className="flex items-center gap-1.5 px-4 py-1.5 rounded-lg text-sm font-medium text-white/80 hover:text-white hover:bg-white/10 transition-colors"
          >
            <IconHeadphones size={14} />
            <span>{t("nav.support")}</span>
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
            className="md:hidden border-t border-white/10 overflow-hidden bg-[#009990]/95 dark:bg-[#062523]/95"
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
                placeholder={t("search.mobilePlaceholder")}
              />
              {[
                { icon: IconHome, label: t("nav.home"), path: "/" },
                { icon: IconPackage, label: t("auth.myOrders"), path: "/orders" },
                { icon: IconHeart, label: t("auth.wishlist"), path: "/wishlist" },
                { icon: IconUser, label: t("auth.myAccount"), path: "/profile" },
                { icon: IconBuildingStore, label: t("nav.sellerChannel"), path: "/seller" },
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
  const location = useLocation();
  const { t } = useTranslation();

  // Back-office routes get a thin console chrome instead of the
  // storefront Navbar (search, categories, wishlist, cart) — those
  // controls are useless inside /admin and /seller and were creating
  // visual confusion for back-office users (pt32 walkthrough finding).
  const isAdminRoute = location.pathname.startsWith("/admin");
  const isSellerRoute = location.pathname.startsWith("/seller");
  const isConsoleRoute = isAdminRoute || isSellerRoute;

  return (
    <div className="min-h-screen bg-background">
      {isConsoleRoute ? (
        <ConsoleChrome persona={isAdminRoute ? "admin" : "seller"} />
      ) : (
        <Navbar />
      )}
      <main>
        <Outlet />
      </main>
      {/* Storefront marketing footer — VNPay/MoMo/payments columns —
          is irrelevant on back-office pages. Replace with a one-line
          strip on /admin and /seller. */}
      {isConsoleRoute ? (
        <footer className="mt-12 border-t border-border bg-card">
          <div className="max-w-7xl mx-auto px-4 py-4 flex items-center justify-between text-xs text-muted-foreground">
            <span>{t("footer.copyright")}</span>
            <button
              onClick={() => navigate("/")}
              className="hover:text-foreground transition-colors"
            >
              {t("consoleChrome.backToStorefront")} →
            </button>
          </div>
        </footer>
      ) : (
        <footer className="mt-16 border-t border-[#2a2d3b] bg-[#1a1d2b] dark:bg-[#0a0c12]">
        <div className="max-w-7xl mx-auto px-4 py-12">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-8">
            <div>
              <div className="flex items-center gap-2 mb-4">
                <IconSparkles className="w-5 h-5" style={{ color: "#00BFB3" }} />
                <span
                  className="text-white font-bold text-lg"
                  style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
                >
                  VNShop
                </span>
              </div>
              <p className="text-gray-400 text-sm leading-relaxed">{t("footer.tagline")}</p>
              <div className="flex gap-3 mt-4">
                {[
                  { key: "fb", label: "FB", href: "https://facebook.com" },
                  { key: "ig", label: "IG", href: "https://instagram.com" },
                  { key: "tw", label: "TW", href: "https://x.com" },
                  { key: "yt", label: "YT", href: "https://youtube.com" },
                ].map((s) => (
                  <a
                    key={s.key}
                    href={s.href}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="w-8 h-8 rounded-lg flex items-center justify-center text-xs font-bold text-white hover:opacity-80 transition-opacity"
                    style={{ background: "#00BFB3" }}
                  >
                    {s.label}
                  </a>
                ))}
              </div>
            </div>
            {[
              {
                title: t("footer.customerSupport"),
                links: [
                  t("footer.helpCenter"),
                  t("footer.warranty"),
                  t("footer.buyingGuide"),
                  t("footer.paymentMethods"),
                  t("footer.shipping"),
                ],
              },
              {
                title: "VNShop",
                links: [
                  t("footer.about"),
                  t("footer.careers"),
                  t("footer.terms"),
                  t("footer.privacy"),
                  t("footer.blog"),
                ],
              },
              {
                title: t("footer.paymentShipping"),
                links: ["VNPay", "MoMo", "ZaloPay", "Thẻ ngân hàng", "Giao Hàng Nhanh"],
              },
            ].map((col) => (
              <div key={col.title}>
                <h4 className="text-white font-semibold mb-4 text-sm">{col.title}</h4>
                <ul className="space-y-2.5">
                  {col.links.map((link) => (
                    <li key={link}>
                      <button
                        onClick={() => comingSoon(link)}
                        className="text-gray-400 text-sm hover:text-white transition-colors text-left"
                      >
                        {link}
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
          <div className="mt-10 pt-6 border-t border-gray-700 flex flex-col sm:flex-row items-center justify-between gap-4">
            <p className="text-gray-500 text-xs">{t("footer.copyright")}</p>
            <div className="flex items-center gap-3">
              <button
                onClick={() => navigate("/design-system")}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-gray-700 text-gray-500 text-xs hover:border-teal-600 hover:text-teal-400 transition-colors"
              >
                <IconPalette size={14} stroke={1.75} />
                Design System
              </button>
              {["DMCA", "BoCongThuong", "SSL"].map((b) => (
                <div
                  key={b}
                  aria-hidden="true"
                  className="px-3 py-1.5 rounded-lg border border-gray-700 text-gray-500 text-xs"
                >
                  {b}
                </div>
              ))}
            </div>
          </div>
        </div>
      </footer>
      )}
    </div>
  );
}
