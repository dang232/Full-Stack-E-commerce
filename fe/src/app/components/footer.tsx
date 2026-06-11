import { Palette } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";

// ─── Footer ────────────────────────────────────────────────────────────────────
export function Footer() {
  const navigate = useNavigate();
  const { t } = useTranslation();

  return (
    <footer className="mt-16 pt-12 pb-6 bg-background border-t border-border">
      <div className="max-w-[var(--content-max)] mx-auto px-[var(--content-padding)]">
        {/* 5-column grid: brand(2fr) + 4×1fr */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-[2fr_1fr_1fr_1fr_1fr] gap-8 mb-8">
          {/* Brand */}
          <div>
            <div className="text-xl font-extrabold text-primary mb-3">VNShop</div>
            <p className="text-sm text-text-secondary leading-relaxed">
              {t("footer.description", {
                defaultValue:
                  "Vietnam's modern marketplace. Buy and sell anything — electronics, fashion, software, and more. Trusted by millions.",
              })}
            </p>
          </div>

          {/* Shop */}
          <div>
            <h4 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground mb-3">
              {t("footer.shop", { defaultValue: "Shop" })}
            </h4>
            <ul className="space-y-2">
              {[
                { label: t("footer.allCategories", { defaultValue: "All Categories" }), path: "/search" },
                { label: t("footer.flashDeals", { defaultValue: "Flash Deals" }), path: "/search?flash=true" },
                { label: t("footer.newArrivals", { defaultValue: "New Arrivals" }), path: "/search" },
                { label: t("footer.topSellers", { defaultValue: "Top Sellers" }), path: "/search" },
              ].map((link) => (
                <li key={link.label}>
                  <button
                    onClick={() => navigate(link.path)}
                    className="text-sm text-text-secondary hover:text-primary hover:translate-x-0.5 transition-all inline-block"
                  >
                    {link.label}
                  </button>
                </li>
              ))}
            </ul>
          </div>

          {/* Sell */}
          <div>
            <h4 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground mb-3">
              {t("footer.sell", { defaultValue: "Sell" })}
            </h4>
            <ul className="space-y-2">
              {[
                { label: t("footer.startSelling", { defaultValue: "Start Selling" }), path: "/seller" },
                { label: t("footer.sellerCenter", { defaultValue: "Seller Center" }), path: "/seller" },
                { label: t("footer.sellerTools", { defaultValue: "Seller Tools" }), path: "/seller" },
                { label: t("footer.fees", { defaultValue: "Fees" }), path: "/seller" },
              ].map((link) => (
                <li key={link.label}>
                  <button
                    onClick={() => navigate(link.path)}
                    className="text-sm text-text-secondary hover:text-primary hover:translate-x-0.5 transition-all inline-block"
                  >
                    {link.label}
                  </button>
                </li>
              ))}
            </ul>
          </div>

          {/* Help */}
          <div>
            <h4 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground mb-3">
              {t("footer.help", { defaultValue: "Help" })}
            </h4>
            <ul className="space-y-2">
              {[
                { label: t("footer.helpCenter", { defaultValue: "Help Center" }), path: "#" },
                { label: t("footer.returns", { defaultValue: "Returns" }), path: "#" },
                { label: t("footer.shipping", { defaultValue: "Shipping & Delivery" }), path: "#" },
                { label: t("footer.contactUs", { defaultValue: "Contact Us" }), path: "#" },
              ].map((link) => (
                <li key={link.label}>
                  <button
                    onClick={() => navigate(link.path)}
                    className="text-sm text-text-secondary hover:text-primary hover:translate-x-0.5 transition-all inline-block"
                  >
                    {link.label}
                  </button>
                </li>
              ))}
            </ul>
          </div>

          {/* Company */}
          <div>
            <h4 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground mb-3">
              {t("footer.company", { defaultValue: "Company" })}
            </h4>
            <ul className="space-y-2">
              {[
                { label: t("footer.about", { defaultValue: "About" }), path: "#" },
                { label: t("footer.careers", { defaultValue: "Careers" }), path: "#" },
                { label: t("footer.blog", { defaultValue: "Blog & News" }), path: "#" },
                { label: t("footer.press", { defaultValue: "Press" }), path: "#" },
              ].map((link) => (
                <li key={link.label}>
                  <button
                    onClick={() => navigate(link.path)}
                    className="text-sm text-text-secondary hover:text-primary hover:translate-x-0.5 transition-all inline-block"
                  >
                    {link.label}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        </div>

        {/* Bottom bar */}
        <div className="pt-6 border-t border-border flex flex-col sm:flex-row justify-between items-center gap-2 text-xs text-muted-foreground">
          <span>{t("footer.copyright")}</span>
          <div className="flex items-center gap-4">
            <span>{t("footer.privacy", { defaultValue: "Privacy" })}</span>
            <span>·</span>
            <span>{t("footer.terms", { defaultValue: "Terms" })}</span>
            <span>·</span>
            <button
              onClick={() => navigate("/design-system")}
              className="inline-flex items-center gap-1 hover:text-primary transition-colors"
            >
              <Palette className="w-3 h-3" />
              Design System
            </button>
          </div>
        </div>
      </div>
    </footer>
  );
}
