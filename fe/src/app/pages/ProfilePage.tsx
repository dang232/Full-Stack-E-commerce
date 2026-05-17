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
import { useNavigate } from "react-router";
import { toast } from "sonner";

import { useAuth } from "../hooks/use-auth";
import {
  myProfile,
  updateProfile,
  addAddress,
  setDefaultAddress,
  removeAddress,
} from "../lib/api/endpoints/users";
import { ApiError } from "../lib/api/envelope";
import type { Address, UserProfile } from "../types/api";

type ProfileTab = "info" | "addresses" | "payment" | "security";

const TABS: { id: ProfileTab; label: string; icon: typeof User }[] = [
  { id: "info", label: "Thông tin tài khoản", icon: User },
  { id: "addresses", label: "Địa chỉ", icon: MapPin },
  { id: "payment", label: "Phương thức thanh toán", icon: CreditCard },
  { id: "security", label: "Bảo mật", icon: Shield },
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
      toast.success("Đã cập nhật hồ sơ");
      setEditing(false);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không thể cập nhật hồ sơ"),
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
      toast.success("Đã thêm địa chỉ");
      setShowAddForm(false);
      setNewAddress(EMPTY_ADDRESS);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không thể thêm địa chỉ"),
  });

  const setDefaultMutation = useMutation({
    mutationFn: (index: number) => setDefaultAddress(index),
    onSuccess: (next) => {
      qc.setQueryData<UserProfile>(["users", "me"], (prev) =>
        prev ? { ...prev, addresses: next.addresses ?? [] } : next,
      );
      toast.success("Đã đặt làm địa chỉ mặc định");
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không thể cập nhật địa chỉ"),
  });

  const removeAddressMutation = useMutation({
    mutationFn: (index: number) => removeAddress(index),
    onSuccess: (next) => {
      qc.setQueryData<UserProfile>(["users", "me"], (prev) =>
        prev ? { ...prev, addresses: next.addresses ?? [] } : next,
      );
      toast.success("Đã xoá địa chỉ");
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không thể xoá địa chỉ"),
  });

  const menuItems = useMemo(
    () => [
      { icon: Package, label: "Đơn mua", desc: "Xem lịch sử đơn hàng", path: "/orders" },
      { icon: Heart, label: "Yêu thích", desc: "Danh sách sản phẩm yêu thích", path: "/wishlist" },
      { icon: Star, label: "Đánh giá của tôi", desc: "Đánh giá sản phẩm đã mua", path: "/orders" },
      { icon: Bell, label: "Thông báo", desc: "Cài đặt thông báo", path: "/profile" },
    ],
    [],
  );

  if (!ready) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-24 text-center text-sm text-gray-500">
        Đang khởi tạo phiên đăng nhập...
      </div>
    );
  }

  if (!authenticated) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-24 text-center">
        <h2 className="text-xl font-bold text-gray-600 mb-3">Vui lòng đăng nhập</h2>
        <button
          onClick={() => navigate("/login?next=%2Fprofile")}
          className="px-6 py-2.5 rounded-xl text-white font-medium"
          style={{ background: "#00BFB3" }}
        >
          Đăng nhập
        </button>
      </div>
    );
  }

  const displayName =
    profile?.name ||
    [kcProfile?.firstName, kcProfile?.lastName].filter(Boolean).join(" ").trim() ||
    kcProfile?.username ||
    "Người dùng";
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
              <span className="text-xs text-green-600 font-medium">Đã đăng nhập qua Keycloak</span>
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
            <LogOut size={16} /> Đăng xuất
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
                {tab.label}
              </button>
            ))}
          </div>

          <div className="p-6">
            {activeTab === "info" ? (
              <div>
                <div className="flex items-center justify-between mb-6">
                  <h3 className="font-bold text-gray-800">Thông tin cá nhân</h3>
                  {editing ? (
                    <div className="flex gap-2">
                      <button
                        onClick={cancelEditing}
                        className="px-4 py-2 rounded-xl text-sm font-medium border border-gray-200 text-gray-500"
                      >
                        Huỷ
                      </button>
                      <button
                        onClick={() => updateProfileMutation.mutate()}
                        disabled={updateProfileMutation.isPending}
                        className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-white disabled:opacity-50"
                        style={{ background: "#00BFB3" }}
                      >
                        <Save size={14} />
                        {updateProfileMutation.isPending ? "Đang lưu..." : "Lưu thay đổi"}
                      </button>
                    </div>
                  ) : (
                    <button
                      onClick={beginEditing}
                      className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium border"
                      style={{ borderColor: "#00BFB3", color: "#00BFB3" }}
                    >
                      <Edit3 size={14} /> Chỉnh sửa
                    </button>
                  )}
                </div>

                {profileQuery.isLoading ? (
                  <p className="text-sm text-gray-400">Đang tải hồ sơ...</p>
                ) : null}

                {profileQuery.error && !profileQuery.isLoading ? (
                  <div className="rounded-xl bg-amber-50 border border-amber-200 p-4 text-sm text-amber-800 flex items-start gap-2">
                    <AlertCircle size={14} className="shrink-0 mt-0.5" />
                    <p>
                      Không tải được hồ sơ từ máy chủ. Hiển thị thông tin từ Keycloak. Một số tính
                      năng có thể chưa hoạt động.
                    </p>
                  </div>
                ) : null}

                <div className="space-y-5 mt-4">
                  {[
                    { label: "Họ và tên", key: "name" as const, type: "text", editable: true },
                    { label: "Email", key: "email" as const, type: "email", editable: false },
                    { label: "Số điện thoại", key: "phone" as const, type: "tel", editable: true },
                  ].map((field) => (
                    <div key={field.key}>
                      <label className="block text-sm font-medium text-gray-600 mb-1.5">
                        {field.label}
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
                            <span className="text-gray-400">Chưa cập nhật</span>
                          )}
                        </div>
                      )}
                      {field.key === "email" ? (
                        <p className="text-[11px] text-gray-400 mt-1">
                          Email được Keycloak quản lý — cập nhật trong tài khoản Keycloak.
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
                  <h3 className="font-bold text-gray-800">Địa chỉ của tôi</h3>
                  <button
                    onClick={() => setShowAddForm((v) => !v)}
                    className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-white"
                    style={{ background: "#00BFB3" }}
                  >
                    <Plus size={14} /> {showAddForm ? "Đóng" : "Thêm địa chỉ"}
                  </button>
                </div>

                {showAddForm ? (
                  <div className="border border-dashed border-gray-200 rounded-2xl p-4 mb-4 space-y-3">
                    {(
                      [
                        { key: "line1", label: "Số nhà, đường" },
                        { key: "ward", label: "Phường/Xã" },
                        { key: "district", label: "Quận/Huyện" },
                        { key: "city", label: "Tỉnh/Thành phố" },
                        { key: "phone", label: "Số điện thoại liên hệ" },
                      ] as const
                    ).map((field) => (
                      <div key={field.key}>
                        <label className="block text-xs font-medium text-gray-600 mb-1">
                          {field.label}
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
                      Đặt làm địa chỉ mặc định
                    </label>
                    <button
                      onClick={() => {
                        if (!newAddress.line1 || !newAddress.city) {
                          toast.error("Vui lòng nhập số nhà/đường và thành phố");
                          return;
                        }
                        addAddressMutation.mutate(newAddress);
                      }}
                      disabled={addAddressMutation.isPending}
                      className="w-full py-2.5 rounded-xl text-white text-sm font-semibold disabled:opacity-50"
                      style={{ background: "#FF6200" }}
                    >
                      {addAddressMutation.isPending ? "Đang lưu..." : "Lưu địa chỉ"}
                    </button>
                  </div>
                ) : null}

                {addresses.length === 0 && !showAddForm ? (
                  <div className="text-center py-12 text-sm text-gray-400">
                    Bạn chưa có địa chỉ nào.
                  </div>
                ) : null}

                <div className="space-y-3">
                  {addresses.map((addr, i) => (
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
                                Mặc định
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
                          Đặt làm mặc định
                        </button>
                      ) : null}
                    </div>
                  ))}
                </div>
              </div>
            ) : null}

            {activeTab === "payment" ? (
              <div>
                <h3 className="font-bold text-gray-800 mb-3">Phương thức thanh toán</h3>
                <div className="rounded-xl bg-amber-50 border border-amber-200 p-4 text-sm text-amber-800 flex items-start gap-2">
                  <AlertCircle size={14} className="shrink-0 mt-0.5" />
                  <p>
                    Tính năng lưu phương thức thanh toán sẽ được hỗ trợ sau (F62, F63). Hiện tại,
                    bạn chọn phương thức thanh toán tại từng đơn hàng.
                  </p>
                </div>
              </div>
            ) : null}

            {activeTab === "security" ? (
              <div>
                <h3 className="font-bold text-gray-800 mb-3">Bảo mật tài khoản</h3>
                <p className="text-sm text-gray-500 mb-4">
                  Mật khẩu, xác thực 2 lớp và thiết bị đăng nhập do Keycloak quản lý.
                </p>
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
                  Mở trang quản lý tài khoản Keycloak
                </button>
              </div>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
}
