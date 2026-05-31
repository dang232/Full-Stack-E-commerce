import { IconSparkles, IconChevronRight, IconShieldCheck, IconAlertCircle, IconEye, IconEyeOff, IconRocket, IconStar, IconShoppingBag, IconLock } from "@tabler/icons-react";
import { useState, type FormEvent } from "react";
import { Trans, useTranslation } from "react-i18next";
import { Navigate, useNavigate, useSearchParams } from "react-router";

import { useAuth } from "../hooks/use-auth";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MIN_PASSWORD_LENGTH = 8;

export function RegisterPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { ready, authenticated, register } = useAuth();
  const { t } = useTranslation();
  const next = params.get("next") ?? "/";

  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [pwFocused, setPwFocused] = useState(false);
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

    if (!EMAIL_RE.test(email.trim())) {
      setError(t("register.form.errorEmailInvalid"));
      return;
    }
    if (password.length < MIN_PASSWORD_LENGTH) {
      setError(t("register.form.errorPasswordShort"));
      return;
    }
    if (password !== confirm) {
      setError(t("register.form.errorMismatch"));
      return;
    }

    setSubmitting(true);
    void (async () => {
      try {
        await register({
          email: email.trim(),
          password,
          firstName: firstName.trim(),
          lastName: lastName.trim(),
          phone: phone.trim() || undefined,
        });
        void navigate(next, { replace: true });
      } catch (err) {
        // See LoginPage for why we duck-type instead of using `instanceof`.
        const errorCode =
          err && typeof err === "object" && "errorCode" in err
            ? (err as { errorCode?: unknown }).errorCode
            : undefined;
        const message =
          err && typeof err === "object" && "message" in err
            ? (err as { message?: unknown }).message
            : undefined;
        if (errorCode === "email_taken") {
          setError(t("register.form.errorEmailTaken"));
        } else if (errorCode === "weak_password") {
          setError(t("register.form.errorWeakPassword"));
        } else if (typeof message === "string" && message) {
          setError(message);
        } else {
          setError(t("register.form.errorGeneric"));
        }
      } finally {
        setSubmitting(false);
      }
    })();
  };

  return (
    <div className="min-h-screen flex flex-col lg:flex-row bg-background">
      {/* Brand panel — same shell as LoginPage so the two pages feel like a pair. */}
      <div
        className="hidden lg:flex lg:w-1/2 flex-col items-center justify-center p-12 relative overflow-hidden lg:sticky lg:top-0 lg:max-h-screen"
        style={{ background: "linear-gradient(135deg, #EE4D2D 0%, #FF6633 50%, #CC3311 100%)" }}
      >
        <div
          className="absolute -top-20 -left-20 w-64 h-64 rounded-full opacity-20 bg-card pointer-events-none"
          style={{ filter: "blur(60px)" }}
        />
        <div
          className="absolute -bottom-20 -right-20 w-64 h-64 rounded-full opacity-20 bg-card pointer-events-none"
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

      {/* Right form */}
      <div className="flex-1 flex items-start justify-center p-6 lg:py-16">
        <div className="w-full max-w-md">
          <div className="flex items-center gap-3 mb-8 lg:hidden">
            <div
              className="w-10 h-10 rounded-xl flex items-center justify-center"
              style={{ background: "#EE4D2D" }}
            >
              <IconSparkles size={20} color="white" />
            </div>
            <span
              className="font-black text-xl text-foreground"
              style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
            >
              VNShop
            </span>
          </div>

          <div className="text-center mb-8">
            <h2 className="text-2xl font-bold text-foreground mb-2">{t("register.title")}</h2>
            <p className="text-sm text-muted-foreground">{t("register.subtitle")}</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-3.5" noValidate>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label htmlFor="firstName" className="block text-sm font-medium text-foreground mb-1.5">
                  {t("register.form.firstNameLabel")}
                </label>
                <input
                  id="firstName"
                  type="text"
                  autoComplete="given-name"
                  required
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl border border-border text-sm outline-none focus:border-[#EE4D2D] focus:ring-2 focus:ring-[#EE4D2D]/20 bg-card"
                />
              </div>
              <div>
                <label htmlFor="lastName" className="block text-sm font-medium text-foreground mb-1.5">
                  {t("register.form.lastNameLabel")}
                </label>
                <input
                  id="lastName"
                  type="text"
                  autoComplete="family-name"
                  required
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl border border-border text-sm outline-none focus:border-[#EE4D2D] focus:ring-2 focus:ring-[#EE4D2D]/20 bg-card"
                />
              </div>
            </div>

            <div>
              <label htmlFor="email" className="block text-sm font-medium text-foreground mb-1.5">
                {t("register.form.emailLabel")}
              </label>
              <input
                id="email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                aria-describedby={error ? "register-error" : undefined}
                className="w-full px-3.5 py-2.5 rounded-xl border border-border text-sm outline-none focus:border-[#EE4D2D] focus:ring-2 focus:ring-[#EE4D2D]/20 bg-card"
              />
            </div>

            <div>
              <label htmlFor="phone" className="block text-sm font-medium text-foreground mb-1.5">
                {t("register.form.phoneLabel")}
              </label>
              <input
                id="phone"
                type="tel"
                autoComplete="tel"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="+84..."
                className="w-full px-3.5 py-2.5 rounded-xl border border-border text-sm outline-none focus:border-[#EE4D2D] focus:ring-2 focus:ring-[#EE4D2D]/20 bg-card"
              />
            </div>

            <div>
              <label htmlFor="password" className="block text-sm font-medium text-foreground mb-1.5">
                {t("register.form.passwordLabel")}
              </label>
              <div className="relative">
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="new-password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  onFocus={() => setPwFocused(true)}
                  onBlur={() => setPwFocused(false)}
                  className="w-full px-3.5 py-2.5 pr-11 rounded-xl border border-border text-sm outline-none focus:border-[#EE4D2D] focus:ring-2 focus:ring-[#EE4D2D]/20 bg-card"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 rounded-lg text-muted-foreground hover:text-muted-foreground hover:bg-muted"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <IconEyeOff size={16} /> : <IconEye size={16} />}
                </button>
              </div>
              {pwFocused ? (
                <p className="text-xs text-muted-foreground mt-1.5">
                  {t("register.form.passwordHint")}
                </p>
              ) : null}
            </div>

            <div>
              <label htmlFor="confirm" className="block text-sm font-medium text-foreground mb-1.5">
                {t("register.form.confirmLabel")}
              </label>
              <input
                id="confirm"
                type={showPassword ? "text" : "password"}
                autoComplete="new-password"
                required
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-xl border border-border text-sm outline-none focus:border-[#EE4D2D] focus:ring-2 focus:ring-[#EE4D2D]/20 bg-card"
              />
            </div>

            {error ? (
              <div
                id="register-error"
                role="alert"
                className="flex items-start gap-2 p-3 rounded-xl bg-red-50 border border-red-100 text-sm text-red-700"
              >
                <IconAlertCircle size={16} className="flex-shrink-0 mt-0.5" />
                <span>{error}</span>
              </div>
            ) : null}

            <button
              type="submit"
              disabled={!ready || submitting}
              className="w-full py-3 rounded-xl text-white font-bold text-base shadow-lg hover:opacity-90 transition-all disabled:opacity-60 flex items-center justify-center gap-2"
              style={{ background: "linear-gradient(135deg, #EE4D2D, #FF6633)" }}
            >
              {submitting ? (
                <>
                  <span className="w-4 h-4 rounded-full border-2 border-white/30 border-t-white animate-spin" />
                  {t("register.form.submitting")}
                </>
              ) : (
                <>
                  {t("register.form.submit")} <IconChevronRight size={18} />
                </>
              )}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-muted-foreground">
            {t("register.haveAccount")}{" "}
            <button
              type="button"
              onClick={() => navigate(`/login?next=${encodeURIComponent(next)}`)}
              className="font-semibold text-[#EE4D2D] hover:underline"
            >
              {t("register.signIn")}
            </button>
          </p>

          <p className="mt-8 text-center text-xs text-muted-foreground">{t("login.termsNotice")}</p>
        </div>
      </div>
    </div>
  );
}
