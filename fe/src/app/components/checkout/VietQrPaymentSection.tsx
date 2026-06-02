import { useEffect, useState } from "react";

import { paymentStatus, vietqrCreate } from "../../lib/api/endpoints/payment";

interface Props {
  orderId: string;
  idempotencyKey: string;
  onCompleted: () => void;
}

export function VietQrPaymentSection({
  orderId,
  idempotencyKey,
  onCompleted,
}: Props) {
  const [qr, setQr] = useState<{
    qrImageUrl: string;
    accountNo: string;
    accountName: string;
    reference: string;
  } | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (qr || error) return;
    vietqrCreate({ orderId }, idempotencyKey)
      .then((res) =>
        setQr({
          qrImageUrl: res.qrImageUrl,
          accountNo: res.accountNo,
          accountName: res.accountName,
          reference: res.reference,
        }),
      )
      .catch((err: Error) => setError(err.message));
  }, [orderId, idempotencyKey, qr, error]);

  useEffect(() => {
    if (!qr) return;
    const deadline = Date.now() + 10 * 60_000;
    let cancelled = false;
    const tick = async () => {
      if (cancelled || Date.now() > deadline) return;
      try {
        const status = await paymentStatus(orderId);
        if (status.status === "COMPLETED") {
          onCompleted();
          return;
        }
      } catch {
        // ignore transient
      }
      if (!cancelled) {
        window.setTimeout(() => {
          void tick();
        }, 5000);
      }
    };
    void tick();
    return () => {
      cancelled = true;
    };
  }, [qr, orderId, onCompleted]);

  if (error) {
    return <div className="rounded-2xl border-2 border-red-200 bg-red-50 p-4 text-sm text-red-700">{error}</div>;
  }
  if (!qr) {
    return (
      <div className="rounded-2xl border-2 border-border p-4 text-sm text-muted-foreground">
        Đang tạo QR…
      </div>
    );
  }

  return (
    <div className="rounded-2xl border-2 border-border p-6 space-y-4 text-center" data-testid="vietqr-section">
      <img
        src={qr.qrImageUrl}
        alt="VietQR mã thanh toán"
        className="mx-auto w-64 h-64 object-contain"
        data-testid="vietqr-image"
      />
      <div className="text-sm text-foreground space-y-1">
        <p>
          <strong>Tài khoản:</strong> {qr.accountNo}
        </p>
        <p>
          <strong>Tên:</strong> {qr.accountName}
        </p>
        <p>
          <strong>Nội dung CK:</strong> <code>{qr.reference}</code>
        </p>
      </div>
      <p className="text-xs text-muted-foreground">
        Sau khi chuyển xong, đơn hàng sẽ tự động cập nhật trong vòng 1 phút.
      </p>
    </div>
  );
}
