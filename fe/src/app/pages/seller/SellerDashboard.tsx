import { Eye, ShoppingBag, Star, Wallet } from "lucide-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { KPICard } from "../../components/kpi-card";
import { useSellerRevenue } from "../../hooks/use-seller-revenue";
import type { PendingSubOrder } from "../../lib/api/endpoints/orders";
import type { SellerRevenuePoint } from "../../lib/api/endpoints/seller-analytics";
import { ApiError } from "../../lib/api/envelope";
import { formatPrice } from "../../lib/format";


interface RevenueChartPoint {
  day: string;
  revenue: number;
  orders: number;
}

function dateToWeekdayLabel(iso: string): string {
  const labels = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];
  const d = new Date(`${iso}T00:00:00`);
  if (Number.isNaN(d.getTime())) return iso;
  return labels[d.getDay()] ?? iso;
}

function toChartData(points: SellerRevenuePoint[]): RevenueChartPoint[] {
  return points.map((p) => ({
    day: dateToWeekdayLabel(p.date),
    revenue: Number(p.revenue) || 0,
    orders: p.orderCount,
  }));
}

export function SellerDashboard({
  pendingOrders,
  walletBalance,
}: {
  pendingOrders: PendingSubOrder[];
  walletBalance: number | null;
}) {
  const { points, isLoading: revenueLoading, error: revenueError } = useSellerRevenue({ days: 30 });
  const chartData = useMemo(() => toChartData(points), [points]);
  const hasRevenue = chartData.length > 0;
  const { t } = useTranslation();

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-800">{t("seller.dashboard.title")}</h2>
          <p className="text-sm text-gray-500">{t("seller.dashboard.subtitle")}</p>
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <KPICard
          icon={Wallet}
          label={t("seller.dashboard.kpi.balance")}
          value={walletBalance !== null ? formatPrice(walletBalance) : "—"}
          color="#00BFB3"
        />
        <KPICard
          icon={ShoppingBag}
          label={t("seller.dashboard.kpi.pending")}
          value={String(pendingOrders.length)}
          color="#FF6200"
        />
        <KPICard icon={Eye} label={t("seller.dashboard.kpi.views")} value="—" color="#3B82F6" />
        <KPICard icon={Star} label={t("seller.dashboard.kpi.rating")} value="—" color="#F59E0B" />
      </div>

      <div className="bg-white rounded-2xl p-5 shadow-sm">
        <div className="flex items-center justify-between mb-5">
          <h3 className="font-bold text-gray-800">{t("seller.dashboard.revenue30dTitle")}</h3>
          <span className="text-[11px] text-gray-400">{t("seller.dashboard.revenue30dHint")}</span>
        </div>
        {revenueError instanceof ApiError ? (
          <div className="rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-600 mb-3">
            {t("seller.dashboard.revenue30dError", { message: revenueError.message })}
          </div>
        ) : null}
        {revenueLoading ? (
          <p className="text-sm text-gray-400 py-12 text-center">
            {t("seller.dashboard.revenue30dLoading")}
          </p>
        ) : hasRevenue ? (
          <ResponsiveContainer width="100%" height={200}>
            <AreaChart data={chartData}>
              <defs>
                <linearGradient id="revenueGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#00BFB3" stopOpacity={0.2} />
                  <stop offset="95%" stopColor="#00BFB3" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
              <XAxis
                dataKey="day"
                axisLine={false}
                tickLine={false}
                tick={{ fill: "#9ca3af", fontSize: 12 }}
              />
              <YAxis
                axisLine={false}
                tickLine={false}
                tick={{ fill: "#9ca3af", fontSize: 11 }}
                tickFormatter={(v) => `${(v / 1000000).toFixed(0)}tr`}
              />
              <Tooltip
                formatter={(v: number) => formatPrice(v)}
                contentStyle={{
                  borderRadius: "12px",
                  border: "none",
                  boxShadow: "0 4px 24px rgba(0,0,0,0.1)",
                }}
              />
              <Area
                type="monotone"
                dataKey="revenue"
                stroke="#00BFB3"
                strokeWidth={2.5}
                fill="url(#revenueGrad)"
              />
            </AreaChart>
          </ResponsiveContainer>
        ) : (
          <p className="text-sm text-gray-400 py-12 text-center">
            {t("seller.dashboard.revenue30dEmpty")}
          </p>
        )}
      </div>

      <div className="bg-white rounded-2xl p-5 shadow-sm">
        <h3 className="font-bold text-gray-800 mb-4">{t("seller.dashboard.orders30dTitle")}</h3>
        {revenueLoading ? (
          <p className="text-sm text-gray-400 py-10 text-center">
            {t("seller.dashboard.orders30dLoading")}
          </p>
        ) : hasRevenue ? (
          <ResponsiveContainer width="100%" height={160}>
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" vertical={false} />
              <XAxis
                dataKey="day"
                axisLine={false}
                tickLine={false}
                tick={{ fill: "#9ca3af", fontSize: 12 }}
              />
              <YAxis axisLine={false} tickLine={false} tick={{ fill: "#9ca3af", fontSize: 11 }} />
              <Tooltip
                contentStyle={{
                  borderRadius: "12px",
                  border: "none",
                  boxShadow: "0 4px 24px rgba(0,0,0,0.1)",
                }}
              />
              <Bar dataKey="orders" fill="#FF6200" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        ) : (
          <p className="text-sm text-gray-400 py-10 text-center">
            {t("seller.dashboard.orders30dEmpty")}
          </p>
        )}
      </div>
    </div>
  );
}
