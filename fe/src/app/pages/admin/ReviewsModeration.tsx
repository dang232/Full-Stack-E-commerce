import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle, Star, XCircle } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

import { FormDialog } from "../../components/form-dialog";
import { adminApproveReview, adminPendingReviews, adminRejectReview } from "../../lib/api/endpoints/admin";
import { ApiError } from "../../lib/api/envelope";

export function ReviewsModeration() {
  const qc = useQueryClient();
  const { t } = useTranslation();
  const [rejectFor, setRejectFor] = useState<string | null>(null);
  const reviewsQuery = useQuery({
    queryKey: ["admin", "reviews", "pending"],
    queryFn: adminPendingReviews,
    retry: false,
  });

  const approve = useMutation({
    mutationFn: (id: string) => adminApproveReview(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["admin", "reviews"] });
      toast.success(t("admin.reviewsModeration.approveOk"));
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("admin.reviewsModeration.approveErr")),
  });

  const reject = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      adminRejectReview(id, { reason }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["admin", "reviews"] });
      toast.success(t("admin.reviewsModeration.rejectOk"));
      setRejectFor(null);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("admin.reviewsModeration.rejectErr")),
  });

  const reviews = reviewsQuery.data ?? [];

  return (
    <div className="space-y-5">
      <FormDialog
        open={!!rejectFor}
        title={t("admin.reviewsModeration.rejectDialog.title")}
        submitLabel={t("admin.reviewsModeration.rejectDialog.submit")}
        submitColor="#EF4444"
        fields={[
          {
            key: "reason",
            label: t("admin.reviewsModeration.rejectDialog.reasonLabel"),
            placeholder: t("admin.reviewsModeration.rejectDialog.reasonPlaceholder"),
            type: "textarea",
            required: true,
          },
        ]}
        onClose={() => setRejectFor(null)}
        onSubmit={({ reason }) => {
          if (rejectFor) reject.mutate({ id: rejectFor, reason });
        }}
        isSubmitting={reject.isPending}
      />
      <h2 className="text-xl font-bold text-gray-800">{t("admin.reviewsModeration.title")}</h2>

      {reviewsQuery.isLoading ? (
        <p className="text-sm text-gray-400">{t("admin.reviewsModeration.loading")}</p>
      ) : null}
      {reviewsQuery.error instanceof ApiError ? (
        <p className="text-sm text-red-500">{reviewsQuery.error.message}</p>
      ) : null}
      {!reviewsQuery.isLoading && reviews.length === 0 ? (
        <div className="bg-white rounded-2xl p-8 text-center shadow-sm">
          <p className="text-sm text-gray-500">{t("admin.reviewsModeration.empty")}</p>
        </div>
      ) : null}

      <div className="space-y-3">
        {reviews.map((r) => (
          <div key={r.id} className="bg-white rounded-2xl p-5 shadow-sm">
            <div className="flex items-start justify-between gap-3 mb-3">
              <div>
                <p className="text-xs font-mono text-gray-400">
                  {t("admin.reviewsModeration.productPrefix", { id: r.productId })}
                </p>
                <p className="text-sm font-semibold text-gray-800">
                  {r.userName ?? r.userId ?? t("admin.reviewsModeration.anonGuest")}
                </p>
              </div>
              <div className="flex items-center gap-0.5">
                {Array.from({ length: 5 }).map((_, i) => (
                  <Star
                    key={i} // eslint-disable-line react/no-array-index-key -- decorative star rating, no stable id
                    size={14}
                    fill={i < r.rating ? "#F59E0B" : "#e5e7eb"}
                    className={i < r.rating ? "text-amber-400" : "text-gray-200"}
                  />
                ))}
              </div>
            </div>
            {r.comment ? (
              <p className="text-sm text-gray-700 mb-3 bg-gray-50 p-3 rounded-xl">{r.comment}</p>
            ) : null}
            <div className="flex gap-2">
              <button
                onClick={() => approve.mutate(r.id)}
                disabled={approve.isPending}
                className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold text-white disabled:opacity-50"
                style={{ background: "#10B981" }}
              >
                <CheckCircle size={13} /> {t("admin.reviewsModeration.approve")}
              </button>
              <button
                onClick={() => setRejectFor(r.id)}
                disabled={reject.isPending}
                className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold border border-red-200 text-red-500 disabled:opacity-50"
              >
                <XCircle size={13} /> {t("admin.reviewsModeration.reject")}
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
