import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  User,
  MapPin,
  Bell,
  Shield,
  CreditCard,
  Package,
  Heart,
  Star,
  Camera,
  Edit3,
  Plus,
  Trash2,
  ChevronRight,
  LogOut,
  AlertCircle,
  Save,
} from "lucide-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { toast } from "sonner";

import { useAuth } from "../hooks/use-auth";
import { ApiError } from "../lib/api";
import {
  myProfile,
  updateProfile,
  addAddress,
  setDefaultAddress,
  removeAddress,
} from "../lib/api/endpoints/users";
import type { Address, UserProfile } from "../types/api";

type ProfileTab = "info" | "addresses" | "payment" | "security";

const TABS: { id: ProfileTab; labelKey: string; icon: typeof User }[] = [
  { id: "info", labelKey: "profile.tabs.info", icon: User },
  { id: "addresses", labelKey: "profile.tabs.addresses", icon: MapPin },
  { id: "payment", labelKey: "profile.tabs.payment", icon: CreditCard },
  { id: "security", labelKey: "profile.tabs.security", icon: Shield },
];

const EMPTY_ADDRESS: Address = {
  line1: "",
  ward: "",
  district: "",
  city: "",
  province: "",
  country: "VN",
  phone: "",
  isDefault: false,
};

function formatAddressLine(a: Address): string {
  return [a.line1, a.ward, a.district, a.city, a.province].filter(Boolean).join(", ");
}

export function ProfilePage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { authenticated, ready, profile: kcProfile, logout } = useAuth();
  const [activeTab, setActiveTab] = useState<ProfileTab>("info");
  const { t } = useTranslation();

  const profileQuery = useQuery({
    queryKey: ["users", "me"],
    queryFn: myProfile,
    enabled: ready && authenticated,
  });

  const profile = profileQuery.data;
  const addresses: Address[] = profile?.addresses ?? [];

  // Server-derived defaults for the info tab. We never mirror these into local
  // state; instead the form re-mounts with `key` whenever the user enters edit
  // mode, taking these values as its initial draft.
  const serverDefaults = useMemo(() => {
    if (profile) {
      return {
        name: profile.name ?? "",
        email: profile.email ?? "",
        phone: profile.phone ?? "",
      };
    }
    if (kcProfile) {
      const fullName = [kcProfile.firstName, kcProfile.lastName].filter(Boolean).join(" ").trim();
      return {
        name: fullName || kcProfile.username || "",
        email: kcProfile.email ?? "",
        phone: "",
      };
    }
    return { name: "", email: "", phone: "" };
  }, [profile, kcProfile]);

  // Info tab form state
  const [editing, setEditing] = useState(false);
  const [formDraft, setFormDraft] = useState(serverDefaults);
  // Display values come from the draft while editing, otherwise from the server.
  const formData = editing ? formDraft : serverDefaults;
  const setFormData = (updater: (prev: typeof formDraft) => typeof formDraft) =>
    setFormDraft(updater);

  const beginEditing = () => {
    setFormDraft(serverDefaults);
    setEditing(true);
  };
  const cancelEditing = () => setEditing(false);

  const updateProfileMutation = useMutation({
    mutationFn: () => updateProfile({ name: formData.name, phone: formData.phone }),
    onSuccess: (next) => {
      qc.setQueryData(["users", "me"], next);
      toast.success(t("profile.info.updatedOk"));
      setEditing(false);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("profile.info.updatedErr")),
  });

  // Address state
  const [showAddForm, setShowAddForm] = useState(false);
  const [newAddress, setNewAddress] = useState<Address>(EMPTY_ADDRESS);

  const addAddressMutation = useMutation({
    mutationFn: (body: Address) => addAddress(body),
    onSuccess: (next) => {
      // The address endpoints return the full up-to-date profile, so we use it
      // as-is. Falling back to a `prev ? merge : prev` pattern would silently
      // drop the response when the cache is empty (first address, cache miss,
      // or a mid-flight invalidation).
      qc.setQueryData<UserProfile>(["users", "me"], (prev) =>
        prev ? { ...prev, addresses: next.addresses ?? [] } : next,
      );
      toast.success(t("profile.addresses.addedOk"));
      setShowAddForm(false);
      setNewAddress(EMPTY_ADDRESS);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("profile.addresses.addedErr")),
  });

  const setDefaultMutation = useMutation({
    mutationFn: (index: number) => setDefaultAddress(index),
    onSuccess: (next) => {
      qc.setQueryData<UserProfile>(["users", "me"], (prev) =>
        prev ? { ...prev, addresses: next.addresses ?? [] } : next,
      );
      toast.success(t("profile.addresses.defaultedOk"));
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("profile.addresses.updateErr")),
  });

  const removeAddressMutation = useMutation({
    mutationFn: (index: number) => removeAddress(index),
    onSuccess: (next) => {
      qc.setQueryData<UserProfile>(["users", "me"], (prev) =>
        prev ? { ...prev, addresses: next.addresses ?? [] } : next,
      );
      toast.success(t("profile.addresses.removedOk"));
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("profile.addresses.removeErr")),
  });

  const menuItems = useMemo(
    () => [
      {
        icon: Package,
        label: t("profile.menu.ordersLabel"),
        desc: t("profile.menu.ordersDesc"),
        path: "/orders",
      },
      {
        icon: Heart,
        label: t("profile.menu.wishlistLabel"),
        desc: t("profile.menu.wishlistDesc"),
        path: "/wishlist",
      },
      {
        icon: Star,
        label: t("profile.menu.reviewsLabel"),
        desc: t("profile.menu.reviewsDesc"),
        path: "/orders",
      },
      {
        icon: Bell,
        label: t("profile.menu.notificationsLabel"),
        desc: t("profile.menu.notificationsDesc"),
        path: "/profile",
      },
    ],
    [t],
  );

  if (!ready) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-24 text-center text-sm text-gray-500">
        {t("profile.initSession")}
      </div>
    );
  }

  if (!authenticated) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-24 text-center">
        <h2 className="text-xl font-bold text-gray-600 mb-3">{t("profile.loginRequired")}</h2>
        <button
          onClick={() => navigate("/login?next=%2Fprofile")}
          className="px-6 py-2.5 rounded-xl text-white font-medium"
          style={{ background: "#00BFB3" }}
        >
          {t("auth.login")}
        </button>
      </div>
    );
  }

  const displayName =
    profile?.name ||
    [kcProfile?.firstName, kcProfile?.lastName].filter(Boolean).join(" ").trim() ||
    kcProfile?.username ||
    t("profile.displayNameFallback");
  const displayEmail = profile?.email ?? kcProfile?.email ?? "";

  return (
    <div className="max-w-5xl mx-auto px-4 py-6">
      <div className="grid md:grid-cols-[280px_1fr] gap-6">
        <div className="space-y-4">
          <div className="bg-white rounded-2xl p-6 shadow-sm text-center">
            <div className="relative inline-block mb-4">
              <div
                className="w-20 h-20 rounded-2xl flex items-center justify-center text-white text-2xl font-bold mx-auto"
                style={{ background: "linear-gradient(135deg, #00BFB3, #009990)" }}
              >
                {displayName.charAt(0).toUpperCase()}
              </div>
              <button
                className="absolute -bottom-1 -right-1 w-7 h-7 rounded-full flex items-center justify-center shadow"
                style={{ background: "#00BFB3" }}
              >
                <Camera size={13} color="white" />
              </button>
            </div>
            <h2 className="font-bold text-gray-800 text-lg">{displayName}</h2>
            <p className="text-sm text-gray-500 mt-0.5">{displayEmail}</p>
            <div className="flex items-center justify-center gap-2 mt-2">
              <div className="w-2 h-2 rounded-full bg-green-400" />
              <span className="text-xs text-green-600 font-medium">{t("profile.loggedInVia")}</span>
            </div>
          </div>

          <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
            {menuItems.map((item) => (
              <button
                key={item.label}
                onClick={() => navigate(item.path)}
                className="w-full flex items-center gap-3 px-5 py-4 hover:bg-gray-50 transition-colors text-left border-b border-gray-50 last:border-0"
              >
                <div
                  className="w-9 h-9 rounded-xl flex items-center justify-center"
                  style={{ background: "rgba(0,191,179,0.1)" }}
                >
                  <item.icon size={18} style={{ color: "#00BFB3" }} />
                </div>
                <div className="flex-1">
                  <p className="text-sm font-medium text-gray-800">{item.label}</p>
                  <p className="text-xs text-gray-400">{item.desc}</p>
                </div>
                <ChevronRight size={15} className="text-gray-400" />
              </button>
            ))}
          </div>

          <button
            onClick={logout}
            className="w-full py-3 rounded-2xl text-red-500 border border-red-200 flex items-center justify-center gap-2 text-sm font-medium hover:bg-red-50 transition-colors bg-white shadow-sm"
          >
            <LogOut size={16} /> {t("profile.logout")}
          </button>
        </div>

        <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
          <div className="flex border-b border-gray-100 overflow-x-auto">
            {TABS.map((tab) => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className="flex items-center gap-2 px-5 py-4 text-sm font-medium transition-colors whitespace-nowrap shrink-0"
                style={{
                  color: activeTab === tab.id ? "#00BFB3" : "#6b7280",
                  borderBottom:
                    activeTab === tab.id ? "2px solid #00BFB3" : "2px solid transparent",
                }}
              >
                <tab.icon size={15} />
                {t(tab.labelKey)}
              </button>
            ))}
          </div>

          <div className="p-6">
            {activeTab === "info" ? (
              <div>
                <div className="flex items-center justify-between mb-6">
                  <h3 className="font-bold text-gray-800">{t("profile.info.title")}</h3>
                  {editing ? (
                    <div className="flex gap-2">
                      <button
                        onClick={cancelEditing}
                        className="px-4 py-2 rounded-xl text-sm font-medium border border-gray-200 text-gray-500"
                      >
                        {t("profile.info.cancel")}
                      </button>
                      <button
                        onClick={() => updateProfileMutation.mutate()}
                        disabled={updateProfileMutation.isPending}
                        className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-white disabled:opacity-50"
                        style={{ background: "#00BFB3" }}
                      >
                        <Save size={14} />
                        {updateProfileMutation.isPending
                          ? t("profile.info.saving")
                          : t("profile.info.save")}
                      </button>
                    </div>
                  ) : (
                    <button
                      onClick={beginEditing}
                      className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium border"
                      style={{ borderColor: "#00BFB3", color: "#00BFB3" }}
                    >
                      <Edit3 size={14} /> {t("profile.info.edit")}
                    </button>
                  )}
                </div>

                {profileQuery.isLoading ? (
                  <p className="text-sm text-gray-400">{t("profile.info.loading")}</p>
                ) : null}

                {profileQuery.error && !profileQuery.isLoading ? (
                  <div className="rounded-xl bg-amber-50 border border-amber-200 p-4 text-sm text-amber-800 flex items-start gap-2">
                    <AlertCircle size={14} className="shrink-0 mt-0.5" />
                    <p>{t("profile.info.fallbackBanner")}</p>
                  </div>
                ) : null}

                <div className="space-y-5 mt-4">
                  {[
                    {
                      labelKey: "profile.info.fields.name",
                      key: "name" as const,
                      type: "text",
                      editable: true,
                    },
                    {
                      labelKey: "profile.info.fields.email",
                      key: "email" as const,
                      type: "email",
                      editable: false,
                    },
                    {
                      labelKey: "profile.info.fields.phone",
                      key: "phone" as const,
                      type: "tel",
                      editable: true,
                    },
                  ].map((field) => (
                    <div key={field.key}>
                      <label className="block text-sm font-medium text-gray-600 mb-1.5">
                        {t(field.labelKey)}
                      </label>
                      {editing && field.editable ? (
                        <input
                          type={field.type}
                          value={formData[field.key]}
                          onChange={(e) =>
                            setFormData((prev) => ({ ...prev, [field.key]: e.target.value }))
                          }
                          className="w-full px-4 py-3 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#00BFB3] transition-colors"
                        />
                      ) : (
                        <div
                          className="px-4 py-3 rounded-xl text-sm text-gray-800"
                          style={{ background: "#f9fafb" }}
                        >
                          {formData[field.key] || (
                            <span className="text-gray-400">{t("profile.info.notSet")}</span>
                          )}
                        </div>
                      )}
                      {field.key === "email" ? (
                        <p className="text-[11px] text-gray-400 mt-1">
                          {t("profile.info.emailHint")}
                        </p>
                      ) : null}
                    </div>
                  ))}
                </div>
              </div>
            ) : null}

            {activeTab === "addresses" ? (
              <div>
                <div className="flex items-center justify-between mb-5">
                  <h3 className="font-bold text-gray-800">{t("profile.addresses.title")}</h3>
                  <button
                    onClick={() => setShowAddForm((v) => !v)}
                    className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-white"
                    style={{ background: "#00BFB3" }}
                  >
                    <Plus size={14} />{" "}
                    {showAddForm
                      ? t("profile.addresses.addToggleClose")
                      : t("profile.addresses.addToggleOpen")}
                  </button>
                </div>

                {showAddForm ? (
                  <div className="border border-dashed border-gray-200 rounded-2xl p-4 mb-4 space-y-3">
                    {(
                      [
                        { key: "line1", labelKey: "profile.addresses.fields.line1" },
                        { key: "ward", labelKey: "profile.addresses.fields.ward" },
                        { key: "district", labelKey: "profile.addresses.fields.district" },
                        { key: "city", labelKey: "profile.addresses.fields.city" },
                        { key: "phone", labelKey: "profile.addresses.fields.phone" },
                      ] as const
                    ).map((field) => (
                      <div key={field.key}>
                        <label className="block text-xs font-medium text-gray-600 mb-1">
                          {t(field.labelKey)}
                        </label>
                        <input
                          value={newAddress[field.key] ?? ""}
                          onChange={(e) =>
                            setNewAddress((prev) => ({ ...prev, [field.key]: e.target.value }))
                          }
                          className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm outline-none focus:border-[#00BFB3]"
                        />
                      </div>
                    ))}
                    <label className="flex items-center gap-2 text-sm text-gray-600">
                      <input
                        type="checkbox"
                        checked={newAddress.isDefault ?? false}
                        onChange={(e) =>
                          setNewAddress((prev) => ({ ...prev, isDefault: e.target.checked }))
                        }
                      />
                      {t("profile.addresses.setDefault")}
                    </label>
                    <button
                      onClick={() => {
                        if (!newAddress.line1 || !newAddress.city) {
                          toast.error(t("profile.addresses.validateMissing"));
                          return;
                        }
                        addAddressMutation.mutate(newAddress);
                      }}
                      disabled={addAddressMutation.isPending}
                      className="w-full py-2.5 rounded-xl text-white text-sm font-semibold disabled:opacity-50"
                      style={{ background: "#FF6200" }}
                    >
                      {addAddressMutation.isPending
                        ? t("profile.addresses.saving")
                        : t("profile.addresses.saveAddress")}
                    </button>
                  </div>
                ) : null}

                {addresses.length === 0 && !showAddForm ? (
                  <div className="text-center py-12 text-sm text-gray-400">
                    {t("profile.addresses.empty")}
                  </div>
                ) : null}

                <div className="space-y-3">
                  {addresses.map((addr, i) => (
                    // eslint-disable-next-line react/no-array-index-key -- address list has no stable id; index is the address position
                    <div key={i} className="border border-gray-200 rounded-2xl p-4">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <div className="flex items-center gap-2 mb-1">
                            <span className="font-semibold text-sm text-gray-800">
                              {displayName}
                            </span>
                            {addr.phone ? (
                              <>
                                <span className="text-gray-300">|</span>
                                <span className="text-sm text-gray-600">{addr.phone}</span>
                              </>
                            ) : null}
                            {addr.isDefault ? (
                              <span
                                className="px-2 py-0.5 rounded text-xs font-medium border"
                                style={{ borderColor: "#FF6200", color: "#FF6200" }}
                              >
                                {t("profile.addresses.isDefaultBadge")}
                              </span>
                            ) : null}
                          </div>
                          <div className="flex items-start gap-1.5">
                            <MapPin size={13} className="mt-0.5 shrink-0 text-gray-400" />
                            <p className="text-sm text-gray-500 leading-relaxed">
                              {formatAddressLine(addr)}
                            </p>
                          </div>
                        </div>
                        <div className="flex gap-2 shrink-0">
                          {!addr.isDefault ? (
                            <button
                              onClick={() => removeAddressMutation.mutate(i)}
                              className="text-xs px-3 py-1.5 rounded-lg border border-red-200 text-red-400 hover:bg-red-50"
                            >
                              <Trash2 size={13} />
                            </button>
                          ) : null}
                        </div>
                      </div>
                      {!addr.isDefault ? (
                        <button
                          onClick={() => setDefaultMutation.mutate(i)}
                          disabled={setDefaultMutation.isPending}
                          className="mt-3 text-xs font-medium disabled:opacity-50"
                          style={{ color: "#00BFB3" }}
                        >
                          {t("profile.addresses.setDefault")}
                        </button>
                      ) : null}
                    </div>
                  ))}
                </div>
              </div>
            ) : null}

            {activeTab === "payment" ? (
              <div>
                <h3 className="font-bold text-gray-800 mb-3">{t("profile.payment.title")}</h3>
                <div className="rounded-xl bg-amber-50 border border-amber-200 p-4 text-sm text-amber-800 flex items-start gap-2">
                  <AlertCircle size={14} className="shrink-0 mt-0.5" />
                  <p>{t("profile.payment.comingSoonBanner")}</p>
                </div>
              </div>
            ) : null}

            {activeTab === "security" ? (
              <div>
                <h3 className="font-bold text-gray-800 mb-3">{t("profile.security.title")}</h3>
                <p className="text-sm text-gray-500 mb-4">{t("profile.security.subtitle")}</p>
                <button
                  onClick={() => {
                    const env = import.meta.env as Record<string, string | undefined>;
                    const url = (env.VITE_KEYCLOAK_URL ?? "").replace(/\/$/, "");
                    const realm = env.VITE_KEYCLOAK_REALM ?? "vnshop";
                    window.open(`${url}/realms/${realm}/account`, "_blank", "noopener");
                  }}
                  className="px-4 py-2.5 rounded-xl text-white text-sm font-semibold"
                  style={{ background: "#00BFB3" }}
                >
                  {t("profile.security.openKeycloak")}
                </button>
              </div>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
}
