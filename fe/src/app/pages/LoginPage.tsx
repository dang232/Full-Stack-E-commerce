import { Sparkles, ChevronRight, ShieldCheck, AlertCircle, Eye, EyeOff, Rocket, Star, ShoppingBag, Lock, ArrowRight } from "lucide-react";
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
  const [rememberMe, setRememberMe] = useState(false);

  if (ready && authenticated) {
    return <Navigate to={next} replace />;
  }

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (submitting) return;
    setError(null);
    setSubmitting(true);
    void (async () => {
      try {
        await loginWithCredentials(identifier.trim(), password);
        if (rememberMe) localStorage.setItem("rememberMe", "true");
        void navigate(next, { replace: true });
      } catch (err) {
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
    <div className="min-h-screen flex flex-col lg:flex-row">
      {/* Brand Panel — left side, hidden on mobile */}
      <div
        className="hidden lg:flex lg:flex-1 flex-col justify-center p-16 relative overflow-hidden"
        style={{ background: "linear-gradient(135deg, oklch(52% 0.2 270) 0%, oklch(48% 0.22 290) 100%)" }}
      >
        {/* Decorative circle */}
        <div className="absolute -top-[30%] -right-[20%] w-[500px] h-[500px] rounded-full bg-white/[0.04]" />

        <div className="relative z-10 max-w-md">
          <div className="text-[32px] font-extrabold text-white mb-8 tracking-tight">VNShop</div>
          <h2 className="text-4xl font-bold text-white leading-tight mb-4">
            {t("login.tagline", { defaultValue: "Welcome back to\nVietnam's #1 Marketplace" })}
          </h2>
          <p className="text-white/75 text-base leading-relaxed max-w-sm">
            {t("login.brandDescription", { defaultValue: "Join millions of buyers and sellers. Find everything from electronics to software, fashion to home goods." })}
          </p>

          {/* Trust stats */}
          <div className="grid grid-cols-2 gap-4 mt-12">
            {[
              { icon: Rocket, val: "2h", label: t("login.trustItems.fastDelivery", { defaultValue: "Fast Delivery" }) },
              { icon: Star, val: "4.9★", label: t("login.trustItems.ratingAvg", { defaultValue: "Average Rating" }) },
              { icon: ShoppingBag, val: "10k+", label: t("login.trustItems.authentic", { defaultValue: "Verified Products" }) },
              { icon: Lock, val: "SSL", label: t("login.trustItems.secure", { defaultValue: "Secure Checkout" }) },
            ].map((item) => (
              <div
                key={item.label}
                className="bg-white/10 backdrop-blur-sm rounded-[var(--radius-lg)] p-4"
              >
                <div className="flex items-center gap-2 mb-1">
                  <item.icon className="w-[18px] h-[18px] text-white/90" />
                  <span className="text-xl font-bold text-white">{item.val}</span>
                </div>
                <span className="text-xs text-white/70">{item.label}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Form Panel — right side */}
      <div className="flex-1 flex items-start lg:items-center justify-center p-6 pt-16 lg:pt-6 bg-muted">
        <div className="w-full max-w-[420px]">
          {/* Mobile logo */}
          <div className="flex items-center gap-3 mb-8 lg:hidden">
            <div className="w-10 h-10 rounded-[var(--radius-lg)] bg-primary flex items-center justify-center text-white">
              <Sparkles className="w-5 h-5" />
            </div>
            <span className="font-extrabold text-xl text-foreground">VNShop</span>
          </div>

          {/* Header */}
          <div className="text-center mb-8">
            <h2 className="text-2xl font-bold text-foreground mb-2">{t("login.title", { defaultValue: "Sign in to your account" })}</h2>
            <p className="text-sm text-muted-foreground">{t("login.subtitle", { defaultValue: "Enter your credentials to access the marketplace" })}</p>
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <div>
              <label htmlFor="identifier" className="block text-[13px] font-medium text-foreground mb-1.5">
                {t("login.form.identifierLabel", { defaultValue: "Email or Username" })}
              </label>
              <input
                id="identifier"
                type="text"
                autoComplete="username"
                required
                value={identifier}
                onChange={(e) => setIdentifier(e.target.value)}
                placeholder={t("login.form.identifierPlaceholder", { defaultValue: "your@email.com" })}
                aria-describedby={error ? "login-error" : undefined}
                className="w-full px-3.5 py-3 rounded-[var(--radius-lg)] border-[1.5px] border-border text-sm bg-card text-foreground placeholder:text-muted-foreground outline-none focus:border-primary focus:shadow-[0_0_0_3px_var(--primary-light)] transition-all"
              />
            </div>

            <div>
              <label htmlFor="password" className="block text-[13px] font-medium text-foreground mb-1.5">
                {t("login.form.passwordLabel", { defaultValue: "Password" })}
              </label>
              <div className="relative">
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder={t("login.form.passwordPlaceholder", { defaultValue: "Enter your password" })}
                  className="w-full px-3.5 py-3 pr-11 rounded-[var(--radius-lg)] border-[1.5px] border-border text-sm bg-card text-foreground placeholder:text-muted-foreground outline-none focus:border-primary focus:shadow-[0_0_0_3px_var(--primary-light)] transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 rounded-[var(--radius-sm)] text-muted-foreground hover:text-foreground hover:bg-surface-elevated transition-colors"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            {error ? (
              <div
                id="login-error"
                role="alert"
                className="flex items-start gap-2 p-3 rounded-[var(--radius-lg)] bg-error-light border border-error/20 text-sm text-error"
              >
                <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
                <span>{error}</span>
              </div>
            ) : null}

            <div className="flex items-center justify-between text-sm">
              <label className="flex items-center gap-2 text-muted-foreground cursor-pointer">
                <input
                  type="checkbox"
                  className="accent-[oklch(52%_0.2_270)] rounded"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                />
                {t("login.form.remember", { defaultValue: "Remember me" })}
              </label>
              <button
                type="button"
                onClick={() => void navigate("/password-reset")}
                className="font-medium text-primary hover:underline text-[13px]"
              >
                {t("login.form.forgot", { defaultValue: "Forgot password?" })}
              </button>
            </div>

            <button
              type="submit"
              disabled={!ready || submitting}
              className="w-full py-3.5 rounded-[var(--radius-lg)] text-white font-bold text-[15px] hover:opacity-90 hover:-translate-y-0.5 transition-all disabled:opacity-60 disabled:cursor-not-allowed flex items-center justify-center gap-2"
              style={{ background: "linear-gradient(135deg, oklch(52% 0.2 270), oklch(48% 0.22 290))" }}
            >
              {submitting ? (
                <>
                  <span className="w-4 h-4 rounded-full border-2 border-white/30 border-t-white animate-spin" />
                  {t("login.form.submitting", { defaultValue: "Signing in..." })}
                </>
              ) : (
                <>
                  {t("login.form.submit", { defaultValue: "Sign In" })}
                  <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </form>

          {/* Divider */}
          <div className="flex items-center gap-3 my-6">
            <div className="flex-1 h-px bg-border" />
            <span className="text-xs text-muted-foreground">{t("login.form.orContinueWith", { defaultValue: "or continue with" })}</span>
            <div className="flex-1 h-px bg-border" />
          </div>

          {/* Social buttons */}
          <div className="flex gap-3">
            <button className="flex-1 flex items-center justify-center gap-2 py-2.5 rounded-[var(--radius-lg)] border border-border text-sm font-medium text-foreground hover:bg-muted hover:border-border-hover hover:-translate-y-0.5 transition-all">
              Google
            </button>
            <button className="flex-1 flex items-center justify-center gap-2 py-2.5 rounded-[var(--radius-lg)] border border-border text-sm font-medium text-foreground hover:bg-muted hover:border-border-hover hover:-translate-y-0.5 transition-all">
              Facebook
            </button>
          </div>

          {/* Register link */}
          <p className="mt-6 text-center text-sm text-muted-foreground">
            {t("login.form.noAccount", { defaultValue: "Don't have an account?" })}{" "}
            <button
              type="button"
              onClick={() => navigate(`/register?next=${encodeURIComponent(next)}`)}
              className="font-medium text-primary hover:underline"
            >
              {t("login.form.signUp", { defaultValue: "Create one" })}
            </button>
          </p>
        </div>
      </div>
    </div>
  );
}
