import { useQuery } from "@tanstack/react-query";
import {
  Bell,
  CheckCircle,
  LayoutDashboard,
  Package,
  Settings,
  ShoppingBag,
  Star,
  Wallet,
} from "lucide-react";
import { motion } from "motion/react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { sellerPendingOrders } from "../../lib/api/endpoints/orders";
import { myPayouts, myWallet } from "../../lib/api/endpoints/seller-finance";
import { sellerProfile } from "../../lib/api/endpoints/users";

import { SellerDashboard } from "./SellerDashboard";
import { SellerOrders } from "./SellerOrders";
import { SellerProducts } from "./SellerProducts";
import { SellerReviews } from "./SellerReviews";
import { SellerSettings } from "./SellerSettings";
import { SellerWallet } from "./SellerWallet";

type SellerTab = "dashboard" | "products" | "orders" | "reviews" | "wallet" | "settings";

const NAV_ITEMS: { id: SellerTab; labelKey: string; icon: typeof LayoutDashboard }[] = [
  { id: "dashboard", labelKey: "seller.nav.dashboard", icon: LayoutDashboard },
  { id: "products", labelKey: "seller.nav.products", icon: Package },
  { id: "orders", labelKey: "seller.nav.orders", icon: ShoppingBag },
  { id: "reviews", labelKey: "seller.nav.reviews", icon: Star },
  { id: "wallet", labelKey: "seller.nav.wallet", icon: Wallet },
  { id: "settings", labelKey: "seller.nav.settings", icon: Settings },
];

export function SellerPage() {
  const [activeTab, setActiveTab] = useState<SellerTab>("dashboard");
  const { t } = useTranslation();

  const profileQuery = useQuery({
    queryKey: ["seller", "profile"],
    queryFn: sellerProfile,
    retry: false,
  });

  const pendingQuery = useQuery({
    queryKey: ["seller", "pending-orders"],
    queryFn: sellerPendingOrders,
    refetchInterval: 60_000,
    retry: false,
  });

  const walletQuery = useQuery({
    queryKey: ["seller", "wallet"],
    queryFn: myWallet,
    retry: false,
  });

  const payoutsQuery = useQuery({
    queryKey: ["seller", "payouts"],
    queryFn: myPayouts,
    retry: false,
  });

  const pendingOrders = useMemo(() => pendingQuery.data ?? [], [pendingQuery.data]);
  const balance = walletQuery.data?.balance ?? null;
  const sellerName = profileQuery.data?.name ?? t("seller.shopFallback");

  return (
    <div className="min-h-screen" style={{ background: "#f4f6f9" }}>
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="flex items-center justify-between mb-6 bg-white rounded-2xl p-5 shadow-sm">
          <div className="flex items-center gap-4">
            <div
              className="w-14 h-14 rounded-2xl flex items-center justify-center text-white font-bold text-lg"
              style={{ background: "linear-gradient(135deg, #00BFB3, #009990)" }}
            >
              {sellerName.charAt(0).toUpperCase()}
            </div>
            <div>
              <h1 className="text-lg font-bold text-gray-800">{sellerName}</h1>
              <div className="flex items-center gap-2 text-sm text-gray-500">
                <span className="flex items-center gap-1 text-green-500 font-medium">
                  <CheckCircle size={13} /> {t("seller.loggedIn")}
                </span>
                {pendingOrders.length > 0 ? (
                  <>
                    <span>·</span>
                    <span style={{ color: "#FF6200" }}>
                      {t("seller.ordersToHandle", { count: pendingOrders.length })}
                    </span>
                  </>
                ) : null}
              </div>
            </div>
          </div>
          <div className="flex gap-2">
            <button className="relative p-2.5 rounded-xl border border-gray-200 text-gray-500 hover:bg-gray-50">
              <Bell size={18} />
            </button>
          </div>
        </div>

        <div className="flex gap-6">
          <nav className="hidden md:block w-56 shrink-0">
            <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
              {NAV_ITEMS.map((item) => (
                <button
                  key={item.id}
                  onClick={() => setActiveTab(item.id)}
                  className="w-full flex items-center gap-3 px-4 py-3.5 text-sm font-medium transition-all text-left border-b border-gray-50 last:border-0"
                  style={{
                    background: activeTab === item.id ? "rgba(0,191,179,0.08)" : "transparent",
                    color: activeTab === item.id ? "#00BFB3" : "#6b7280",
                    borderLeft:
                      activeTab === item.id ? "3px solid #00BFB3" : "3px solid transparent",
                  }}
                >
                  <item.icon size={18} />
                  {t(item.labelKey)}
                </button>
              ))}
            </div>
          </nav>

          <div className="md:hidden flex gap-2 mb-4 overflow-x-auto pb-1 scrollbar-hide w-full">
            {NAV_ITEMS.map((item) => (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id)}
                className="shrink-0 flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-medium transition-all"
                style={
                  activeTab === item.id
                    ? { background: "#00BFB3", color: "white" }
                    : { background: "white", color: "#6b7280", border: "1px solid #e5e7eb" }
                }
              >
                <item.icon size={14} /> {t(item.labelKey)}
              </button>
            ))}
          </div>

          <div className="flex-1 min-w-0">
            <motion.div
              key={activeTab}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.2 }}
            >
              {activeTab === "dashboard" ? (
                <SellerDashboard pendingOrders={pendingOrders} walletBalance={balance} />
              ) : null}
              {activeTab === "products" ? <SellerProducts /> : null}
              {activeTab === "orders" ? (
                <SellerOrders
                  orders={pendingOrders}
                  isLoading={pendingQuery.isLoading}
                  error={pendingQuery.error}
                />
              ) : null}
              {activeTab === "reviews" ? <SellerReviews /> : null}
              {activeTab === "wallet" ? (
                <SellerWallet
                  balance={balance}
                  payouts={payoutsQuery.data ?? []}
                  isLoading={walletQuery.isLoading}
                  error={walletQuery.error}
                />
              ) : null}
              {activeTab === "settings" ? (
                <SellerSettings
                  profileData={profileQuery.data}
                  profileError={profileQuery.error}
                />
              ) : null}
            </motion.div>
          </div>
        </div>
      </div>
    </div>
  );
}
