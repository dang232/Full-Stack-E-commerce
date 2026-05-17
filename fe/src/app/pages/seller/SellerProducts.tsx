import { AlertCircle, Edit, Filter, Plus, Search } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";

import { SellerProductModal } from "../../components/seller-product-modal";
import { type Product } from "../../components/vnshop-data";
import { useProducts } from "../../hooks/use-products";
import { formatPrice } from "../../lib/format";

export function SellerProducts() {
  const [search, setSearch] = useState("");
  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<Product | null>(null);
  const { data: catalog = [], isLoading } = useProducts();
  const filtered = catalog.filter((p) => p.name.toLowerCase().includes(search.toLowerCase()));
  const { t } = useTranslation();

  return (
    <div className="space-y-5">
      <SellerProductModal open={showCreate} onClose={() => setShowCreate(false)} />
      <SellerProductModal open={!!editing} product={editing} onClose={() => setEditing(null)} />
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-gray-800">{t("seller.products.title")}</h2>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-white font-semibold text-sm"
          style={{ background: "#FF6200" }}
        >
          <Plus size={16} /> {t("seller.products.addNew")}
        </button>
      </div>

      <div className="rounded-xl bg-amber-50 border border-amber-200 p-3 text-xs text-amber-800 flex items-start gap-2">
        <AlertCircle size={14} className="shrink-0 mt-0.5" />
        <p>{t("seller.products.fallbackBanner")}</p>
      </div>

      <div className="flex gap-3">
        <div className="flex-1 flex items-center gap-3 bg-white border border-gray-200 rounded-xl px-4 py-2.5 shadow-sm">
          <Search size={16} className="text-gray-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={t("seller.products.searchPlaceholder")}
            className="flex-1 text-sm outline-none"
          />
        </div>
        <button className="flex items-center gap-2 px-4 py-2.5 border border-gray-200 rounded-xl bg-white text-sm text-gray-600">
          <Filter size={15} /> {t("seller.products.filter")}
        </button>
      </div>

      {isLoading ? <p className="text-sm text-gray-400">{t("seller.products.loading")}</p> : null}

      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        <table className="w-full">
          <thead style={{ background: "#f9fafb" }}>
            <tr>
              {[
                t("seller.products.th.product"),
                t("seller.products.th.price"),
                t("seller.products.th.stock"),
                t("seller.products.th.sold"),
                "",
              ].map((h, i) => (
                <th
                  // eslint-disable-next-line react/no-array-index-key -- table headers are positional, no stable id
                  key={i}
                  className="px-4 py-3 text-xs font-semibold text-gray-500 text-left"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {filtered.slice(0, 50).map((p) => (
              <tr key={p.id} className="hover:bg-gray-50 transition-colors">
                <td className="px-4 py-3">
                  <div className="flex items-center gap-3">
                    {p.image ? (
                      <img
                        src={p.image}
                        alt={p.name}
                        className="w-10 h-10 rounded-lg object-cover"
                        loading="lazy"
                      />
                    ) : null}
                    <p className="text-sm font-medium text-gray-800 max-w-[280px] truncate">
                      {p.name}
                    </p>
                  </div>
                </td>
                <td className="px-4 py-3 text-sm font-bold" style={{ color: "#FF6200" }}>
                  {formatPrice(p.price)}
                </td>
                <td className="px-4 py-3 text-sm text-gray-600">{p.stock}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{p.sold.toLocaleString()}</td>
                <td className="px-4 py-3">
                  <button
                    onClick={() => setEditing(p)}
                    className="p-1.5 rounded-lg hover:bg-blue-50 text-blue-500 transition-colors"
                    title={t("seller.products.editTooltip")}
                  >
                    <Edit size={14} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
