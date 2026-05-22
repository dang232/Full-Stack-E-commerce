import { IconShield } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

import type { PaymentOption } from "./types";

interface Props {
  paymentOptions: PaymentOption[];
  selectedPaymentId: PaymentOption["id"];
  setSelectedPaymentId: (id: PaymentOption["id"]) => void;
}

export function CheckoutPaymentStep({
  paymentOptions,
  selectedPaymentId,
  setSelectedPaymentId,
}: Props) {
  const { t } = useTranslation();

  return (
    <div>
      <h2 className="font-bold text-foreground text-lg mb-4">{t("checkout.payment.header")}</h2>
      <div className="space-y-3">
        {paymentOptions.map((method) => (
          <button
            key={method.id}
            onClick={() => setSelectedPaymentId(method.id)}
            className="w-full p-4 rounded-2xl border-2 text-left transition-all bg-card"
            style={{
              borderColor: selectedPaymentId === method.id ? "#00BFB3" : "#e5e7eb",
              background: selectedPaymentId === method.id ? "rgba(0,191,179,0.04)" : "white",
            }}
          >
            <div className="flex items-center gap-4">
              <span
                className="w-10 h-10 rounded-xl flex items-center justify-center shrink-0"
                style={{
                  background: selectedPaymentId === method.id ? "rgba(0,191,179,0.12)" : "#f3f4f6",
                  color: selectedPaymentId === method.id ? "#00BFB3" : "#6b7280",
                }}
              >
                <method.Icon size={22} stroke={2} />
              </span>
              <div className="flex-1">
                <p className="font-semibold text-sm text-foreground">{method.name}</p>
                <p className="text-xs text-muted-foreground">{method.desc}</p>
              </div>
              <div
                className="w-5 h-5 rounded-full border-2 flex items-center justify-center"
                style={{
                  borderColor: selectedPaymentId === method.id ? "#00BFB3" : "#d1d5db",
                }}
              >
                {selectedPaymentId === method.id ? (
                  <div className="w-2.5 h-2.5 rounded-full" style={{ background: "#00BFB3" }} />
                ) : null}
              </div>
            </div>
          </button>
        ))}
      </div>
      <div className="mt-4 flex items-center gap-2 text-xs text-muted-foreground px-1">
        <IconShield size={14} style={{ color: "#00BFB3" }} />
        <span>{t("checkout.payment.sslNotice")}</span>
      </div>
    </div>
  );
}
