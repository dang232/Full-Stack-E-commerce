import { PayPalButtons, PayPalScriptProvider } from "@paypal/react-paypal-js";
import { useState } from "react";
import { toast } from "sonner";

import { paypalCapture, paypalCreate } from "../../lib/api/endpoints/payment";

interface Props {
  orderId: string;
  idempotencyKey: string;
  onCompleted: () => void;
}

const PAYPAL_ENABLED = import.meta.env.VITE_PAYPAL_ENABLED === "true";
const CLIENT_ID = import.meta.env.VITE_PAYPAL_CLIENT_ID ?? "";

export function PayPalPaymentSection({
  orderId,
  idempotencyKey,
  onCompleted,
}: Props) {
  const [paymentId, setPaymentId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!PAYPAL_ENABLED || !CLIENT_ID) {
    return (
      <div className="rounded-2xl border-2 border-dashed border-gray-200 p-4 text-sm text-gray-500">
        PayPal is disabled. Set <code>VITE_PAYPAL_ENABLED=true</code> and{" "}
        <code>VITE_PAYPAL_CLIENT_ID</code> to enable.
      </div>
    );
  }

  return (
    <div data-testid="paypal-section">
      <PayPalScriptProvider options={{ clientId: CLIENT_ID, currency: "USD" }}>
        <PayPalButtons
          createOrder={async () => {
            const res = await paypalCreate({ orderId }, idempotencyKey);
            setPaymentId(res.payment.paymentId);
            return res.paypalOrderId;
          }}
          onApprove={async (data) => {
            if (!paymentId) {
              setError("missing payment id");
              return;
            }
            try {
              await paypalCapture(paymentId, data.orderID);
              onCompleted();
            } catch (err) {
              const msg = err instanceof Error ? err.message : "capture failed";
              setError(msg);
              toast.error(msg);
            }
          }}
          onError={(err) => {
            const msg = err instanceof Error ? err.message : "PayPal error";
            setError(msg);
            toast.error(msg);
          }}
        />
      </PayPalScriptProvider>
      {error ? (
        <div className="mt-3 rounded-2xl border-2 border-red-200 bg-red-50 p-3 text-sm text-red-700">
          {error}
        </div>
      ) : null}
    </div>
  );
}
