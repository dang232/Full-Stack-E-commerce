import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router";
import { CheckCircle, AlertCircle, Clock } from "lucide-react";
import { paymentStatus } from "../lib/api/endpoints/payment";
import { ApiError } from "../lib/api/envelope";
import { formatPrice } from "../lib/format";

type Provider = "vnpay" | "momo";
type Phase = "pending" | "completed" | "failed" | "error";

const TERMINAL_STATUSES = new Set(["COMPLETED", "PAID", "SUCCESS", "FAILED", "CANCELLED", "EXPIRED"]);

export function PaymentReturnPage() {
  const navigate = useNavigate();
  const params = useParams<{ provider: string }>();
  const [search] = useSearchParams();

  const provider: Provider = (params.provider === "momo" ? "momo" : "vnpay");

  // Most VN gateways return order id in their own param names. Try a few common ones.
  const orderId = useMemo(() => {
    const candidates = [
      search.get("orderId"),
      search.get("vnp_TxnRef"),
      search.get("orderInfo"),
      search.get("requestId"),
    ];
    return candidates.find((v): v is string => !!v) ?? null;
  }, [search]);

  const [phase, setPhase] = useState<Phase>("pending");
  const [errorMessage, setErrorMessage] = useState<string>("");
  const [amount, setAmount] = useState<number | null>(null);
  const [attempts, setAttempts] = useState(0);

  useEffect(() => {
    if (!orderId) {
      setPhase("error");
      setErrorMessage("Không tìm thấy mã đơn hàng trong tham số URL.");
      return;
    }

    let cancelled = false;
    let timeout: ReturnType<typeof setTimeout> | undefined;

    const poll = async () => {
      try {
        const status = await paymentStatus(orderId);
        if (cancelled) return;
        if (TERMINAL_STATUSES.has(status.status.toUpperCase())) {
          const ok = ["COMPLETED", "PAID", "SUCCESS"].includes(status.status.toUpperCase());
          setPhase(ok ? "completed" : "failed");
          return;
        }
        // Not terminal yet: backoff and retry up to ~30 attempts (~60s).
        setAttempts((n) => n + 1);
        timeout = setTimeout(poll, attempts < 5 ? 1000 : 2000);
      } catch (err) {
        if (cancelled) return;
        if (err instanceof ApiError) {
          if (err.status >= 500 && attempts < 10) {
            setAttempts((n) => n + 1);
            timeout = setTimeout(poll, 2000);
            return;
          }
          setPhase("error");
          setErrorMessage(err.message);
          return;
        }
        setPhase("error");
        setErrorMessage("Không thể kiểm tra trạng thái thanh toán.");
      }
    };

    void poll();
    return () => {
      cancelled = true;
      if (timeout) clearTimeout(timeout);
    };
    // attempts is intentionally not in deps — we use it inside poll for the schedule.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderId]);

  // Best-effort amount surface: gateways sometimes pass it back in URL.
  useEffect(() => {
    const raw = search.get("vnp_Amount") ?? search.get("amount");
    if (raw) {
      const parsed = Number(raw);
      if (!Number.isNaN(parsed)) {
        // VNPay returns amount * 100; MoMo returns plain VND.
        setAmount(provider === "vnpay" ? parsed / 100 : parsed);
      }
    }
  }, [provider, search]);

  return (
    <div className="max-w-lg mx-auto px-4 py-20 text-center">
      {phase === "pending" && (
        <>
          <div
            className="w-20 h-20 rounded-full mx-auto mb-6 flex items-center justify-center animate-pulse"
            style={{ background: "rgba(0,191,179,0.12)" }}
          >
            <Clock size={36} style={{ color: "#00BFB3" }} />
          </div>
          <h1
            className="text-2xl font-bold text-gray-800 mb-3"
            style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
          >
            Đang xác nhận thanh toán...
          </h1>
          <p className="text-sm text-gray-500">
            Vui lòng giữ trang này mở. Hệ thống đang kiểm tra trạng thái thanh toán với{" "}
            {provider === "vnpay" ? "VNPay" : "MoMo"}.
          </p>
        </>
      )}

      {phase === "completed" && (
        <>
          <div
            className="w-24 h-24 rounded-full mx-auto mb-6 flex items-center justify-center"
            style={{ background: "rgba(16,185,129,0.12)" }}
          >
            <CheckCircle size={48} style={{ color: "#10B981" }} />
          </div>
          <h1
            className="text-3xl font-black text-gray-800 mb-3"
            style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
          >
            Thanh toán thành công 🎉
          </h1>
          {amount !== null && (
            <p className="text-sm text-gray-500 mb-2">
              Số tiền đã thanh toán: <strong>{formatPrice(amount)}</strong>
            </p>
          )}
          {orderId && (
            <p className="text-sm text-gray-500 mb-8">
              Mã đơn hàng: <span className="font-mono font-semibold">{orderId}</span>
            </p>
          )}
          <div className="flex gap-3">
            <button
              onClick={() => navigate("/orders")}
              className="flex-1 py-3 rounded-xl border-2 font-semibold text-sm"
              style={{ borderColor: "#00BFB3", color: "#00BFB3" }}
            >
              Xem đơn hàng
            </button>
            <button
              onClick={() => navigate("/")}
              className="flex-1 py-3 rounded-xl text-white font-semibold text-sm"
              style={{ background: "linear-gradient(135deg, #00BFB3, #009990)" }}
            >
              Tiếp tục mua sắm
            </button>
          </div>
        </>
      )}

      {phase === "failed" && (
        <>
          <div
            className="w-20 h-20 rounded-full mx-auto mb-6 flex items-center justify-center"
            style={{ background: "rgba(239,68,68,0.12)" }}
          >
            <AlertCircle size={40} style={{ color: "#EF4444" }} />
          </div>
          <h1 className="text-2xl font-bold text-gray-800 mb-3">Thanh toán không thành công</h1>
          <p className="text-sm text-gray-500 mb-6">
            Đơn hàng của bạn vẫn được giữ. Bạn có thể thử thanh toán lại từ trang Đơn hàng.
          </p>
          <div className="flex gap-3">
            <button
              onClick={() => navigate("/orders")}
              className="flex-1 py-3 rounded-xl text-white font-semibold text-sm"
              style={{ background: "#FF6200" }}
            >
              Đến đơn hàng
            </button>
            <button
              onClick={() => navigate("/")}
              className="flex-1 py-3 rounded-xl border border-gray-200 text-gray-600 font-semibold text-sm"
            >
              Trang chủ
            </button>
          </div>
        </>
      )}

      {phase === "error" && (
        <>
          <div
            className="w-20 h-20 rounded-full mx-auto mb-6 flex items-center justify-center"
            style={{ background: "rgba(245,158,11,0.12)" }}
          >
            <AlertCircle size={40} style={{ color: "#F59E0B" }} />
          </div>
          <h1 className="text-2xl font-bold text-gray-800 mb-3">Không xác định được trạng thái</h1>
          <p className="text-sm text-gray-500 mb-6">
            {errorMessage || "Vui lòng kiểm tra trang đơn hàng để xem trạng thái mới nhất."}
          </p>
          <button
            onClick={() => navigate("/orders")}
            className="px-6 py-3 rounded-xl text-white font-semibold text-sm"
            style={{ background: "#00BFB3" }}
          >
            Đến đơn hàng
          </button>
        </>
      )}
    </div>
  );
}
