import { IconAlertCircle, IconChevronLeft, IconChevronRight, IconEdit, IconFilter, IconPlus, IconSearch } from "@tabler/icons-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";

import { comingSoon } from "../../lib/ui/coming-soon";

import { SellerProductModal } from "../../components/seller-product-modal";
import { useAuth } from "../../hooks/use-auth";
import { useProducts } from "../../hooks/use-products";
import { formatPrice } from "../../lib/format";
import { type Product } from "../../types/ui";

const PAGE_SIZE = 24;

export function SellerProducts() {
  const [search, setSearch] = useState("");
  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<Product | null>(null);
  const [page, setPage] = useState(0);
  const { subject: sellerId } = useAuth();
  const { data: catalog = [], isLoading } = useProducts({ sellerId, page, size: PAGE_SIZE });
  const { t } = useTranslation();

  const filtered = catalog.filter((p) => p.name.toLowerCase().includes(search.toLowerCase()));
  const hasMore = catalog.length === PAGE_SIZE;

  const handleSearchChange = (value: string) => {
    setSearch(value);
    setPage(0);
  };

  return (
    <div className="space-y-5">
      <SellerProductModal open={showCreate} onClose={() => setShowCreate(false)} />
      <SellerProductModal open={!!editing} product={editing} onClose={() => setEditing(null)} />
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-foreground">{t("seller.products.title")}</h2>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-white font-semibold text-sm"
          style={{ background: "#FF6200" }}
        >
          <IconPlus size={16} /> {t("seller.products.addNew")}
        </button>
      </div>

      <div className="rounded-xl bg-amber-50 border border-amber-200 p-3 text-xs text-amber-800 flex items-start gap-2">
        <IconAlertCircle size={14} className="shrink-0 mt-0.5" />
        <p>{t("seller.products.fallbackBanner")}</p>
      </div>

      <div className="flex gap-3">
        <div className="flex-1 flex items-center gap-3 bg-card border border-border rounded-xl px-4 py-2.5 shadow-sm">
          <IconSearch size={16} className="text-muted-foreground" />
          <input
            value={search}
            onChange={(e) => handleSearchChange(e.target.value)}
            placeholder={t("seller.products.searchPlaceholder")}
            className="flex-1 text-sm outline-none"
          />
        </div>
        <button
          onClick={() => comingSoon("Product filtering")}
          className="flex items-center gap-2 px-4 py-2.5 border border-border rounded-xl bg-card text-sm text-muted-foreground"
        >
          <IconFilter size={15} /> {t("seller.products.filter")}
        </button>
      </div>

      {isLoading ? <p className="text-sm text-muted-foreground">{t("seller.products.loading")}</p> : null}

      <div className="bg-card rounded-2xl shadow-sm overflow-hidden">
        <table className="w-full">
          <thead className="bg-muted">
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
                  className="px-4 py-3 text-xs font-semibold text-muted-foreground text-left"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {filtered.map((p) => (
              <tr key={p.id} className="hover:bg-muted transition-colors">
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
                    <p className="text-sm font-medium text-foreground max-w-[280px] truncate">
                      {p.name}
                    </p>
                  </div>
                </td>
                <td className="px-4 py-3 text-sm font-bold" style={{ color: "#FF6200" }}>
                  {formatPrice(p.price)}
                </td>
                <td className="px-4 py-3 text-sm text-muted-foreground">{p.stock}</td>
                <td className="px-4 py-3 text-sm text-muted-foreground">{p.sold.toLocaleString()}</td>
                <td className="px-4 py-3">
                  <button
                    onClick={() => setEditing(p)}
                    className="p-1.5 rounded-lg hover:bg-blue-50 text-blue-500 transition-colors"
                    title={t("seller.products.editTooltip")}
                  >
                    <IconEdit size={14} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="flex items-center justify-between px-4 py-3 border-t border-border">
          <button
            onClick={() => setPage((p) => p - 1)}
            disabled={page === 0}
            className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-sm text-muted-foreground border border-border disabled:opacity-40 hover:bg-muted transition-colors"
          >
            <IconChevronLeft size={14} /> {t("seller.products.prev")}
          </button>
          <span className="text-xs text-muted-foreground">
            {t("seller.products.pageIndicator", { page: page + 1 })}
          </span>
          <button
            onClick={() => setPage((p) => p + 1)}
            disabled={!hasMore}
            className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-sm text-muted-foreground border border-border disabled:opacity-40 hover:bg-muted transition-colors"
          >
            {t("seller.products.next")} <IconChevronRight size={14} />
          </button>
        </div>
      </div>
    </div>
  );
}
