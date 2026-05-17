import { CheckCircle } from "lucide-react";
import { motion } from "motion/react";
import { Trans, useTranslation } from "react-i18next";
import { useNavigate } from "react-router";

import { formatPrice } from "../../lib/format";

import type { PaymentOption } from "./types";

interface Props {
  placedOrderId: string | null;
  selectedPaymentId: PaymentOption["id"];
  finalTotal: number;
}

export function CheckoutSuccess({ placedOrderId, selectedPaymentId, finalTotal }: Props) {
  const { t } = useTranslation();
  const navigate = useNavigate();

  return (
    <div className="max-w-lg mx-auto px-4 py-16 text-center">
      <motion.div
        initial={{ scale: 0.5, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: "spring", duration: 0.6 }}
      >
        <div
          className="w-24 h-24 rounded-full flex items-center justify-center mx-auto mb-6"
          style={{ background: "rgba(0,191,179,0.12)" }}
        >
          <CheckCircle size={48} style={{ color: "#00BFB3" }} />
        </div>
        <h1
          className="text-3xl font-black text-gray-800 mb-3"
          style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
        >
          {t("checkout.success.title")}
        </h1>
        {placedOrderId ? (
          <>
            <p className="text-gray-500 mb-2">{t("checkout.success.orderIdLabel")}</p>
            <div
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl mb-6 font-mono font-bold text-lg"
              style={{ background: "rgba(0,191,179,0.08)", color: "#00BFB3" }}
            >
              {placedOrderId}
            </div>
          </>
        ) : null}
        {selectedPaymentId === "COD" ? (
          <p className="text-sm text-gray-500 mb-2">
            <Trans
              i18nKey="checkout.success.codNotice"
              values={{ amount: formatPrice(finalTotal) }}
              components={{ 1: <strong /> }}
            />
          </p>
        ) : null}
        <div className="flex gap-3">
          <button
            onClick={() => navigate("/orders")}
            className="flex-1 py-3 rounded-xl border-2 font-semibold text-sm transition-colors"
            style={{ borderColor: "#00BFB3", color: "#00BFB3" }}
          >
            {t("checkout.success.viewOrders")}
          </button>
          <button
            onClick={() => navigate("/")}
            className="flex-1 py-3 rounded-xl text-white font-semibold text-sm"
            style={{ background: "linear-gradient(135deg, #00BFB3, #009990)" }}
          >
            {t("checkout.success.continueShopping")}
          </button>
        </div>
      </motion.div>
    </div>
  );
}
