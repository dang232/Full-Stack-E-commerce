import { IconAlertCircle, IconPlus } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";

import type { Address } from "../../types/api";

import { formatAddressLine } from "./format";

interface Props {
  addresses: Address[];
  selectedAddressIndex: number;
  setSelectedAddressIndex: (i: number) => void;
  buyerName: string;
  isLoading: boolean;
}

export function CheckoutAddressStep({
  addresses,
  selectedAddressIndex,
  setSelectedAddressIndex,
  buyerName,
  isLoading,
}: Props) {
  const { t } = useTranslation();
  const navigate = useNavigate();

  return (
    <div className="space-y-4">
      <h2 className="font-bold text-foreground text-lg mb-4">{t("checkout.address.header")}</h2>
      {isLoading ? (
        <p className="text-sm text-muted-foreground">{t("checkout.address.loading")}</p>
      ) : null}
      {!isLoading && addresses.length === 0 ? (
        <div className="bg-card rounded-2xl p-5 text-sm text-muted-foreground flex items-start gap-3">
          <IconAlertCircle size={18} className="text-orange-500 shrink-0 mt-0.5" />
          <div>
            <p className="font-semibold text-foreground">{t("checkout.address.noAddressesTitle")}</p>
            <p className="mt-1">{t("checkout.address.noAddressesSub")}</p>
            <button
              onClick={() => navigate("/profile")}
              className="mt-3 text-sm font-semibold"
              style={{ color: "#00BFB3" }}
            >
              {t("checkout.address.goToProfile")}
            </button>
          </div>
        </div>
      ) : null}
      <div role="radiogroup" aria-label="Delivery address" className="space-y-4">
        {addresses.map((addr, i) => (
          <button
            // eslint-disable-next-line react/no-array-index-key -- address list has no stable id; index is the address position
            key={i}
            role="radio"
            aria-checked={selectedAddressIndex === i}
            onClick={() => setSelectedAddressIndex(i)}
            className="w-full p-4 rounded-2xl border-2 text-left transition-all"
            style={{
              borderColor: selectedAddressIndex === i ? "#00BFB3" : "#e5e7eb",
              background: selectedAddressIndex === i ? "rgba(0,191,179,0.04)" : "white",
            }}
          >
            <div className="flex items-start justify-between gap-3">
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <span className="font-semibold text-foreground">{buyerName}</span>
                  {addr.phone ? (
                    <>
                      <span className="text-muted-foreground text-sm">|</span>
                      <span className="text-muted-foreground text-sm">{addr.phone}</span>
                    </>
                  ) : null}
                  {addr.isDefault ? (
                    <span
                      className="px-1.5 py-0.5 rounded text-xs font-medium border"
                      style={{ borderColor: "#FF6200", color: "#FF6200" }}
                    >
                      {t("checkout.address.isDefaultBadge")}
                    </span>
                  ) : null}
                </div>
                <p className="text-sm text-muted-foreground">{formatAddressLine(addr)}</p>
              </div>
              <div
                className="w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 mt-1 transition-all"
                style={{ borderColor: selectedAddressIndex === i ? "#00BFB3" : "#d1d5db" }}
              >
                {selectedAddressIndex === i ? (
                  <div className="w-2.5 h-2.5 rounded-full" style={{ background: "#00BFB3" }} />
                ) : null}
              </div>
            </div>
          </button>
        ))}
      </div>
      <button
        onClick={() => navigate("/profile")}
        className="w-full py-3 rounded-2xl border-2 border-dashed border-border flex items-center justify-center gap-2 text-sm font-medium text-muted-foreground hover:border-[#00BFB3] hover:text-[#00BFB3] transition-colors bg-card"
      >
        <IconPlus size={16} /> {t("checkout.address.addNew")}
      </button>
    </div>
  );
}
