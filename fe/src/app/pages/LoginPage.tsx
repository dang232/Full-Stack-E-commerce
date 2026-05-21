import { IconSparkles, IconChevronRight, IconShieldCheck, IconAlertCircle, IconEye, IconEyeOff, IconRocket, IconStar, IconShoppingBag, IconLock } from "@tabler/icons-react";
import type { Icon as TablerIcon } from "@tabler/icons-react";
import { useState, type FormEvent } from "react";
import { Trans, useTranslation } from "react-i18next";
import { Navigate, useNavigate, useSearchParams } from "react-router";

import { useAuth } from "../hooks/use-auth";

export function LoginPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { ready, authenticated, loginWithCredentials } = useAuth();
  const { t } = useTranslation();
  const next = params.get("next") ?? "/";

  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (ready && authenticated) {
    return <Navigate to={next} replace />;
  }

  const trustItems = [
    { Icon: IconRocket, labelKey: "login.trustItems.fastDelivery", val: "2h" },
    { Icon: IconStar, labelKey: "login.trustItems.ratingAvg", val: "4.9★" },
    { Icon: IconShoppingBag, labelKey: "login.trustItems.authentic", val: "10k+" },
    { Icon: IconLock, labelKey: "login.trustItems.secure", val: "SSL" },
  ];

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (submitting) return;
    setError(null);
    setSubmitting(true);
    void (async () => {
      try {
        await loginWithCredentials(identifier.trim(), password);
        void navigate(next, { replace: true });
      } catch (err) {
        // Use duck-typing on errorCode rather than `instanceof AuthError`.
        // Vite's manualChunks can split native-auth into a different chunk
        // than LoginPage, so the imported `AuthError` constructor and the
        // one thrown by passwordLogin become two distinct identities and
        // `instanceof` fails even though the shape is identical. Match the
        // structural contract instead.
        const errorCode =
          err && typeof err === "object" && "errorCode" in err
            ? (err as { errorCode?: unknown }).errorCode
            : undefined;
        if (errorCode === "invalid_credentials") {
          setError(t("login.form.errorInvalidCredentials"));
        } else {
          setError(t("login.form.errorGeneric"));
        }
      } finally {
        setSubmitting(false);
      }
    })();
  };

  return (
    <div
      className="min-h-screen flex flex-col lg:flex-row"
      style={{ background: "linear-gradient(135deg, #f0fffe 0%, #fff8f0 100%)" }}
    >
      <BrandPanel trustItems={trustItems} t={t} />

      {/* Right form */}
      <div className="flex-1 flex items-start justify-center p-6 lg:py-16">
        <div className="w-full max-w-md">
          <div className="flex items-center gap-3 mb-8 lg:hidden">
            <div
              className="w-10 h-10 rounded-xl flex items-center justify-center"
              style={{ background: "#00BFB3" }}
            >
              <IconSparkles size={20} color="white" />
            </div>
            <span
              className="font-black text-xl text-gray-800"
              style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
            >
              VNShop
            </span>
          </div>

          <div className="text-center mb-8">
            <h2 className="text-2xl font-bold text-gray-800 mb-2">{t("login.title")}</h2>
            <p className="text-sm text-gray-500">{t("login.subtitle")}</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <div>
              <label htmlFor="identifier" className="block text-sm font-medium text-gray-700 mb-1.5">
                {t("login.form.identifierLabel")}
              </label>
              <input
                id="identifier"
                type="text"
                autoComplete="username"
                required
                value={identifier}
                onChange={(e) => setIdentifier(e.target.value)}
                placeholder={t("login.form.identifierPlaceholder")}
                className="w-full px-3.5 py-2.5 rounded-xl border border-gray-200 text-sm outline-none focus:border-[#00BFB3] focus:ring-2 focus:ring-[#00BFB3]/20 bg-white"
              />
            </div>

            <div>
              <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1.5">
                {t("login.form.passwordLabel")}
              </label>
              <div className="relative">
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder={t("login.form.passwordPlaceholder")}
                  className="w-full px-3.5 py-2.5 pr-11 rounded-xl border border-gray-200 text-sm outline-none focus:border-[#00BFB3] focus:ring-2 focus:ring-[#00BFB3]/20 bg-white"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-100"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <IconEyeOff size={16} /> : <IconEye size={16} />}
                </button>
              </div>
            </div>

            {error ? (
              <div className="flex items-start gap-2 p-3 rounded-xl bg-red-50 border border-red-100 text-sm text-red-700">
                <IconAlertCircle size={16} className="flex-shrink-0 mt-0.5" />
                <span>{error}</span>
              </div>
            ) : null}

            <div className="flex items-center justify-between text-sm">
              <label className="flex items-center gap-2 text-gray-600 cursor-pointer">
                <input type="checkbox" className="rounded border-gray-300 text-[#00BFB3] focus:ring-[#00BFB3]" />
                {t("login.form.remember")}
              </label>
              <button
                type="button"
                onClick={() => void navigate("/password-reset")}
                className="font-medium text-[#00BFB3] hover:underline"
              >
                {t("login.form.forgot")}
              </button>
            </div>

            <button
              type="submit"
              disabled={!ready || submitting}
              className="w-full py-3 rounded-xl text-white font-bold text-base shadow-lg hover:opacity-90 transition-all disabled:opacity-60 flex items-center justify-center gap-2"
              style={{ background: "linear-gradient(135deg, #00BFB3, #009990)" }}
            >
              {submitting ? (
                <>
                  <span className="w-4 h-4 rounded-full border-2 border-white/30 border-t-white animate-spin" />
                  {t("login.form.submitting")}
                </>
              ) : (
                <>
                  {t("login.form.submit")} <IconChevronRight size={18} />
                </>
              )}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-gray-600">
            {t("login.form.noAccount")}{" "}
            <button
              type="button"
              onClick={() => navigate(`/register?next=${encodeURIComponent(next)}`)}
              className="font-semibold text-[#00BFB3] hover:underline"
            >
              {t("login.form.signUp")}
            </button>
          </p>

          <p className="mt-8 text-center text-xs text-gray-400">{t("login.termsNotice")}</p>
        </div>
      </div>
    </div>
  );
}

interface BrandPanelProps {
  trustItems: { Icon: TablerIcon; labelKey: string; val: string }[];
  t: (k: string) => string;
}

function BrandPanel({ trustItems, t }: BrandPanelProps) {
  return (
    <div
      className="hidden lg:flex lg:w-1/2 flex-col items-center justify-center p-12 relative overflow-hidden lg:sticky lg:top-0 lg:max-h-screen"
      style={{ background: "linear-gradient(135deg, #00BFB3 0%, #009990 50%, #006b65 100%)" }}
    >
      <div
        className="absolute -top-20 -left-20 w-64 h-64 rounded-full opacity-20 bg-white pointer-events-none"
        style={{ filter: "blur(60px)" }}
      />
      <div
        className="absolute -bottom-20 -right-20 w-64 h-64 rounded-full opacity-20 bg-white pointer-events-none"
        style={{ filter: "blur(60px)" }}
      />
      <div className="relative z-10 text-center text-white max-w-sm">
        <div className="flex items-center justify-center gap-3 mb-6">
          <div className="w-14 h-14 rounded-2xl bg-white/20 flex items-center justify-center">
            <IconSparkles size={28} />
          </div>
        </div>
        <h1
          className="text-3xl font-black mb-3"
          style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
        >
          VNShop
        </h1>
        <p className="text-white/80 text-base mb-6">{t("login.tagline")}</p>

        <div className="grid grid-cols-2 gap-3">
          {trustItems.map((item) => (
            <div
              key={item.labelKey}
              className="bg-white/10 rounded-2xl p-3.5 text-center backdrop-blur-sm"
            >
              <item.Icon size={22} stroke={1.75} className="mx-auto mb-1 text-white/90" />
              <p className="font-black text-base">{item.val}</p>
              <p className="text-white/70 text-xs">{t(item.labelKey)}</p>
            </div>
          ))}
        </div>

        <p className="mt-6 text-white/60 text-xs flex items-center justify-center gap-2">
          <IconShieldCheck size={14} />
          <Trans
            i18nKey="login.trust5m"
            components={{ 1: <span className="text-white font-bold" /> }}
          />
        </p>
      </div>
    </div>
  );
}
