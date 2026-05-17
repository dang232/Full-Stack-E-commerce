import { Shield } from "lucide-react";
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
      <h2 className="font-bold text-gray-800 text-lg mb-4">{t("checkout.payment.header")}</h2>
      <div className="space-y-3">
        {paymentOptions.map((method) => (
          <button
            key={method.id}
            onClick={() => setSelectedPaymentId(method.id)}
            className="w-full p-4 rounded-2xl border-2 text-left transition-all bg-white"
            style={{
              borderColor: selectedPaymentId === method.id ? "#00BFB3" : "#e5e7eb",
              background: selectedPaymentId === method.id ? "rgba(0,191,179,0.04)" : "white",
            }}
          >
            <div className="flex items-center gap-4">
              <span className="text-2xl">{method.icon}</span>
              <div className="flex-1">
                <p className="font-semibold text-sm text-gray-800">{method.name}</p>
                <p className="text-xs text-gray-500">{method.desc}</p>
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
      <div className="mt-4 flex items-center gap-2 text-xs text-gray-500 px-1">
        <Shield size={14} style={{ color: "#00BFB3" }} />
        <span>{t("checkout.payment.sslNotice")}</span>
      </div>
    </div>
  );
}
