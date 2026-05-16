import { Sparkles, ChevronRight, ShieldCheck } from "lucide-react";
import { Navigate, useSearchParams } from "react-router";

import { useAuth } from "../hooks/use-auth";

export function LoginPage() {
  const [params] = useSearchParams();
  const { ready, authenticated, login } = useAuth();
  const next = params.get("next") ?? "/";

  // Already signed in → bounce to the requested next page (no effect needed).
  if (ready && authenticated) {
    return <Navigate to={next} replace />;
  }

  return (
    <div
      className="min-h-screen flex"
      style={{ background: "linear-gradient(135deg, #f0fffe 0%, #fff8f0 100%)" }}
    >
      {/* Left visual */}
      <div
        className="hidden lg:flex lg:w-1/2 flex-col items-center justify-center p-12 relative overflow-hidden"
        style={{ background: "linear-gradient(135deg, #00BFB3 0%, #009990 50%, #006b65 100%)" }}
      >
        <div
          className="absolute -top-20 -left-20 w-64 h-64 rounded-full opacity-20 bg-white"
          style={{ filter: "blur(60px)" }}
        />
        <div
          className="absolute -bottom-20 -right-20 w-64 h-64 rounded-full opacity-20 bg-white"
          style={{ filter: "blur(60px)" }}
        />
        <div className="relative z-10 text-center text-white">
          <div className="flex items-center justify-center gap-3 mb-8">
            <div className="w-14 h-14 rounded-2xl bg-white/20 flex items-center justify-center">
              <Sparkles size={28} />
            </div>
          </div>
          <h1
            className="text-4xl font-black mb-4"
            style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
          >
            VNShop
          </h1>
          <p className="text-white/80 text-lg mb-8">
            Nền tảng mua sắm trực tuyến hàng đầu Việt Nam
          </p>

          <div className="grid grid-cols-2 gap-4 max-w-xs mx-auto">
            {[
              { emoji: "🚀", label: "Giao hàng siêu tốc", val: "2h" },
              { emoji: "⭐", label: "Đánh giá trung bình", val: "4.9★" },
              { emoji: "🛍️", label: "Sản phẩm chính hãng", val: "10k+" },
              { emoji: "🔒", label: "Bảo mật tuyệt đối", val: "SSL" },
            ].map((item) => (
              <div
                key={item.label}
                className="bg-white/10 rounded-2xl p-4 text-center backdrop-blur-sm"
              >
                <p className="text-2xl mb-1">{item.emoji}</p>
                <p className="font-black text-lg">{item.val}</p>
                <p className="text-white/70 text-xs">{item.label}</p>
              </div>
            ))}
          </div>

          <p className="mt-10 text-white/60 text-sm">
            Tin dùng bởi hơn <span className="text-white font-bold">5 triệu</span> khách hàng
          </p>
        </div>
      </div>

      {/* Right form */}
      <div className="flex-1 flex items-center justify-center p-6">
        <div className="w-full max-w-md">
          <div className="flex items-center gap-3 mb-8 lg:hidden">
            <div
              className="w-10 h-10 rounded-xl flex items-center justify-center"
              style={{ background: "#00BFB3" }}
            >
              <Sparkles size={20} color="white" />
            </div>
            <span
              className="font-black text-xl text-gray-800"
              style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
            >
              VNShop
            </span>
          </div>

          <div className="text-center mb-10">
            <h2 className="text-2xl font-bold text-gray-800 mb-2">Đăng nhập VNShop</h2>
            <p className="text-sm text-gray-500">
              Bạn sẽ được chuyển sang Keycloak để xác thực an toàn
            </p>
          </div>

          <button
            type="button"
            onClick={() => login(next)}
            disabled={!ready}
            className="w-full py-3.5 rounded-xl text-white font-bold text-base shadow-lg hover:opacity-90 transition-all disabled:opacity-60 flex items-center justify-center gap-2"
            style={{ background: "linear-gradient(135deg, #00BFB3, #009990)" }}
          >
            {ready ? (
              <>
                Đăng nhập với Keycloak <ChevronRight size={18} />
              </>
            ) : (
              <span className="flex items-center gap-2">
                <span className="w-4 h-4 rounded-full border-2 border-white/30 border-t-white animate-spin" />
                Đang khởi tạo...
              </span>
            )}
          </button>

          <div className="mt-6 flex items-start gap-3 p-4 rounded-xl bg-gray-50 text-sm text-gray-600">
            <ShieldCheck size={20} className="text-teal-600 flex-shrink-0 mt-0.5" />
            <p>
              Tài khoản, mật khẩu và xác thực 2 lớp do Keycloak quản lý. Việc tạo tài khoản mới và
              đặt lại mật khẩu được thực hiện trực tiếp trên trang đăng nhập.
            </p>
          </div>

          <p className="mt-8 text-center text-xs text-gray-400">
            Bằng cách đăng nhập, bạn đồng ý với Điều khoản dịch vụ và Chính sách bảo mật của VNShop.
          </p>
        </div>
      </div>
    </div>
  );
}
