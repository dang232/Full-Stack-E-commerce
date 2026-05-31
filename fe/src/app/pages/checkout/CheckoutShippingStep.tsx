import { IconTruck } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

import { formatPrice } from "../../lib/format";

import type { ShippingOption } from "./types";

interface Props {
  shippingOptions: ShippingOption[];
  selectedShippingId: string;
  setShippingChoice: (id: string) => void;
  note: string;
  setNote: (note: string) => void;
}

export function CheckoutShippingStep({
  shippingOptions,
  selectedShippingId,
  setShippingChoice,
  note,
  setNote,
}: Props) {
  const { t } = useTranslation();

  return (
    <div>
      <h2 className="font-bold text-foreground text-lg mb-4">{t("checkout.shipping.header")}</h2>
      <div role="radiogroup" aria-label="Shipping method" className="space-y-3">
        {shippingOptions.map((method) => (
          <button
            key={method.id}
            role="radio"
            aria-checked={selectedShippingId === method.id}
            onClick={() => setShippingChoice(method.id)}
            className="w-full p-4 rounded-2xl border-2 text-left transition-all bg-card"
            style={{
              borderColor: selectedShippingId === method.id ? "#00BFB3" : "#e5e7eb",
              background: selectedShippingId === method.id ? "rgba(0,191,179,0.04)" : "white",
            }}
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <IconTruck
                  size={20}
                  style={{
                    color: selectedShippingId === method.id ? "#00BFB3" : "#6b7280",
                  }}
                />
                <div>
                  <p className="font-semibold text-sm text-foreground">{method.name}</p>
                  <p className="text-xs text-muted-foreground">
                    {method.desc} · {t("checkout.shipping.etaPrefix", { eta: method.eta })}
                  </p>
                </div>
              </div>
              <div className="text-right">
                <p
                  className="font-bold text-sm"
                  style={{ color: method.fee === 0 ? "#00BFB3" : "#374151" }}
                >
                  {method.fee === 0 ? t("checkout.shipping.free") : formatPrice(method.fee)}
                </p>
              </div>
            </div>
          </button>
        ))}
      </div>
      <div className="mt-5">
        <label
          htmlFor="checkout-note"
          className="block text-sm font-semibold text-foreground mb-2"
        >
          {t("checkout.shipping.noteLabel")}
        </label>
        <textarea
          id="checkout-note"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          rows={3}
          placeholder={t("checkout.shipping.notePlaceholder")}
          className="w-full px-4 py-3 border border-border rounded-xl text-sm outline-none focus:border-[#00BFB3] resize-none bg-card"
        />
      </div>
    </div>
  );
}
