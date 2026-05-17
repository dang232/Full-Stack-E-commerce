import { useQuery } from "@tanstack/react-query";
import { AlertCircle, LayoutDashboard, Star, Tag, Users, Wallet } from "lucide-react";
import { motion } from "motion/react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import {
  adminListSellers,
  adminOpenDisputes,
  adminPendingPayouts,
  adminPendingReviews,
} from "../../lib/api/endpoints/admin";

import { AdminDashboard } from "./AdminDashboard";
import { CouponsManagement } from "./CouponsManagement";
import { DisputesQueue } from "./DisputesQueue";
import { PayoutsQueue } from "./PayoutsQueue";
import { ReviewsModeration } from "./ReviewsModeration";
import { SellersApproval } from "./SellersApproval";

type AdminTab = "dashboard" | "sellers" | "reviews" | "coupons" | "disputes" | "payouts";

const NAV_ITEMS: { id: AdminTab; labelKey: string; icon: typeof LayoutDashboard }[] = [
  { id: "dashboard", labelKey: "admin.nav.dashboard", icon: LayoutDashboard },
  { id: "sellers", labelKey: "admin.nav.sellers", icon: Users },
  { id: "reviews", labelKey: "admin.nav.reviews", icon: Star },
  { id: "coupons", labelKey: "admin.nav.coupons", icon: Tag },
  { id: "disputes", labelKey: "admin.nav.disputes", icon: AlertCircle },
  { id: "payouts", labelKey: "admin.nav.payouts", icon: Wallet },
];

export function AdminPage() {
  const [activeTab, setActiveTab] = useState<AdminTab>("dashboard");
  const { t } = useTranslation();

  // Pull pending counts for sidebar badges (best effort).
  const sellersQuery = useQuery({
    queryKey: ["admin", "sellers"],
    queryFn: adminListSellers,
    retry: false,
  });
  const reviewsQuery = useQuery({
    queryKey: ["admin", "reviews", "pending"],
    queryFn: adminPendingReviews,
    retry: false,
  });
  const disputesQuery = useQuery({
    queryKey: ["admin", "disputes"],
    queryFn: adminOpenDisputes,
    retry: false,
  });
  const payoutsQuery = useQuery({
    queryKey: ["admin", "payouts", "pending"],
    queryFn: adminPendingPayouts,
    retry: false,
  });

  const badges = useMemo(
    () => ({
      sellers: sellersQuery.data?.length ?? 0,
      reviews: reviewsQuery.data?.length ?? 0,
      disputes: disputesQuery.data?.length ?? 0,
      payouts: payoutsQuery.data?.length ?? 0,
    }),
    [sellersQuery.data, reviewsQuery.data, disputesQuery.data, payoutsQuery.data],
  );

  return (
    <div className="min-h-screen" style={{ background: "#f4f6f9" }}>
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="flex items-center justify-between mb-6 bg-white rounded-2xl p-5 shadow-sm">
          <div className="flex items-center gap-3">
            <div
              className="w-12 h-12 rounded-2xl flex items-center justify-center"
              style={{ background: "linear-gradient(135deg, #6366F1, #8B5CF6)" }}
            >
              <LayoutDashboard size={22} className="text-white" />
            </div>
            <div>
              <h1 className="text-lg font-bold text-gray-800">{t("admin.console")}</h1>
              <p className="text-sm text-gray-500">{t("admin.consoleSub")}</p>
            </div>
          </div>
        </div>

        <div className="flex gap-6">
          <nav className="hidden md:block w-56 shrink-0">
            <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
              {NAV_ITEMS.map((item) => {
                const badge =
                  item.id === "sellers"
                    ? badges.sellers
                    : item.id === "reviews"
                      ? badges.reviews
                      : item.id === "disputes"
                        ? badges.disputes
                        : item.id === "payouts"
                          ? badges.payouts
                          : 0;
                return (
                  <button
                    key={item.id}
                    onClick={() => setActiveTab(item.id)}
                    className="w-full flex items-center gap-3 px-4 py-3.5 text-sm font-medium transition-all text-left border-b border-gray-50 last:border-0"
                    style={{
                      background: activeTab === item.id ? "rgba(99,102,241,0.08)" : "transparent",
                      color: activeTab === item.id ? "#6366F1" : "#6b7280",
                      borderLeft:
                        activeTab === item.id ? "3px solid #6366F1" : "3px solid transparent",
                    }}
                  >
                    <item.icon size={18} />
                    <span className="flex-1">{t(item.labelKey)}</span>
                    {badge > 0 ? (
                      <span
                        className="px-1.5 py-0.5 rounded-full text-[10px] font-bold text-white"
                        style={{ background: "#FF6200" }}
                      >
                        {badge}
                      </span>
                    ) : null}
                  </button>
                );
              })}
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
                    ? { background: "#6366F1", color: "white" }
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
              {activeTab === "dashboard" ? <AdminDashboard /> : null}
              {activeTab === "sellers" ? <SellersApproval /> : null}
              {activeTab === "reviews" ? <ReviewsModeration /> : null}
              {activeTab === "coupons" ? <CouponsManagement /> : null}
              {activeTab === "disputes" ? <DisputesQueue /> : null}
              {activeTab === "payouts" ? <PayoutsQueue /> : null}
            </motion.div>
          </div>
        </div>
      </div>
    </div>
  );
}
