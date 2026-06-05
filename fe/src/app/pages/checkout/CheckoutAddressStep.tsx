import { IconAlertCircle, IconPlus } from "@tabler/icons-react";
import { useMutation } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

import type { Address } from "../../types/api";
import { ApiError } from "../../lib/api";
import { addAddress } from "../../lib/api/endpoints/users";
import { Modal } from "../../components/ui/modal";

import { formatAddressLine } from "./format";

interface Props {
  addresses: Address[];
  selectedAddressIndex: number;
  setSelectedAddressIndex: (i: number) => void;
  buyerName: string;
  isLoading: boolean;
  refetchAddresses: () => Promise<unknown>;
}

interface AddressFormState {
  street: string;
  ward: string;
  district: string;
  city: string;
}

const EMPTY_FORM: AddressFormState = { street: "", ward: "", district: "", city: "" };

export function CheckoutAddressStep({
  addresses,
  selectedAddressIndex,
  setSelectedAddressIndex,
  buyerName,
  isLoading,
  refetchAddresses,
}: Props) {
  const { t } = useTranslation();
  const [showAddModal, setShowAddModal] = useState(false);
  const [form, setForm] = useState<AddressFormState>(EMPTY_FORM);

  const addMutation = useMutation({
    mutationFn: (body: Address) => addAddress(body),
    onSuccess: async (data) => {
      const newIndex = (data.addresses?.length ?? 1) - 1;
      setShowAddModal(false);
      setForm(EMPTY_FORM);
      await refetchAddresses();
      setSelectedAddressIndex(newIndex);
    },
    onError: (err) => {
      toast.error(err instanceof ApiError ? err.message : t("checkout.address.addError"));
    },
  });

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!form.street || !form.district || !form.city) return;
    addMutation.mutate({
      street: form.street,
      ward: form.ward || undefined,
      district: form.district,
      city: form.city,
    });
  };

  const setField =
    (field: keyof AddressFormState) => (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm((prev) => ({ ...prev, [field]: e.target.value }));

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
          </div>
        </div>
      ) : null}
      <div role="radiogroup" aria-label="Delivery address" className="space-y-4">
        {addresses.map((addr, i) => (
          <button
            key={`${addr.street}|${addr.city}|${String(i)}`}
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
        onClick={() => setShowAddModal(true)}
        className="w-full py-3 rounded-2xl border-2 border-dashed border-border flex items-center justify-center gap-2 text-sm font-medium text-muted-foreground hover:border-[#00BFB3] hover:text-[#00BFB3] transition-colors bg-card"
      >
        <IconPlus size={16} /> {t("checkout.address.addNew")}
      </button>

      <Modal
        open={showAddModal}
        onClose={() => setShowAddModal(false)}
        dismissDisabled={addMutation.isPending}
        title={t("checkout.address.addNew")}
        size="md"
        footer={
          <>
            <button
              type="button"
              onClick={() => setShowAddModal(false)}
              disabled={addMutation.isPending}
              className="flex-1 py-2.5 rounded-xl border border-border text-sm font-medium text-muted-foreground hover:bg-muted transition-colors disabled:opacity-50"
            >
              {t("common.cancel")}
            </button>
            <button
              type="submit"
              form="add-address-form"
              disabled={addMutation.isPending}
              className="flex-1 py-2.5 rounded-xl text-white text-sm font-semibold transition-opacity disabled:opacity-60"
              style={{ background: "#00BFB3" }}
            >
              {addMutation.isPending ? t("common.saving") : t("common.save")}
            </button>
          </>
        }
      >
        <form id="add-address-form" onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-foreground mb-1">
              {t("checkout.address.form.street")} <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={form.street}
              onChange={setField("street")}
              required
              className="w-full px-3 py-2 rounded-xl border border-border bg-background text-sm focus:outline-none focus:ring-2 focus:ring-[#00BFB3]"
              placeholder={t("checkout.address.form.streetPlaceholder")}
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-foreground mb-1">
              {t("checkout.address.form.ward")}
            </label>
            <input
              type="text"
              value={form.ward}
              onChange={setField("ward")}
              className="w-full px-3 py-2 rounded-xl border border-border bg-background text-sm focus:outline-none focus:ring-2 focus:ring-[#00BFB3]"
              placeholder={t("checkout.address.form.wardPlaceholder")}
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-foreground mb-1">
              {t("checkout.address.form.district")} <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={form.district}
              onChange={setField("district")}
              required
              className="w-full px-3 py-2 rounded-xl border border-border bg-background text-sm focus:outline-none focus:ring-2 focus:ring-[#00BFB3]"
              placeholder={t("checkout.address.form.districtPlaceholder")}
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-foreground mb-1">
              {t("checkout.address.form.city")} <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={form.city}
              onChange={setField("city")}
              required
              className="w-full px-3 py-2 rounded-xl border border-border bg-background text-sm focus:outline-none focus:ring-2 focus:ring-[#00BFB3]"
              placeholder={t("checkout.address.form.cityPlaceholder")}
            />
          </div>
        </form>
      </Modal>
    </div>
  );
}
