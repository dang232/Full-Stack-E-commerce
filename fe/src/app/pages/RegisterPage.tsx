import { Sparkles, AlertCircle, Eye, EyeOff, ChevronRight } from "lucide-react";
import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Navigate, useNavigate, useSearchParams } from "react-router";

import { useAuth } from "../hooks/use-auth";
import { sanitizeRedirect } from "../lib/auth/sanitize-redirect";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MIN_PASSWORD_LENGTH = 8;

export function RegisterPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { ready, authenticated, register } = useAuth();
  const { t } = useTranslation();
  const next = sanitizeRedirect(params.get("next"));

  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [pwFocused, setPwFocused] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState<string | null>(null);

  if (ready && authenticated) {
    return <Navigate to={next} replace />;
  }

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (submitting) return;
    setErrors({});
    setServerError(null);

    const validationErrors: Record<string, string> = {};

    if (!firstName.trim()) {
      validationErrors.firstName = t("register.form.errorFirstNameRequired", { defaultValue: "First name is required" });
    }
    if (!lastName.trim()) {
      validationErrors.lastName = t("register.form.errorLastNameRequired", { defaultValue: "Last name is required" });
    }
    if (!EMAIL_RE.test(email.trim())) {
      validationErrors.email = t("register.form.errorEmailInvalid");
    }
    if (password.length < MIN_PASSWORD_LENGTH) {
      validationErrors.password = t("register.form.errorPasswordShort");
    }
    if (password !== confirm) {
      validationErrors.confirm = t("register.form.errorMismatch");
    }

    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
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
        const errorCode =
          err && typeof err === "object" && "errorCode" in err
            ? (err as { errorCode?: unknown }).errorCode
            : undefined;
        const message =
          err && typeof err === "object" && "message" in err
            ? (err as { message?: unknown }).message
            : undefined;
        if (errorCode === "email_taken") {
          setServerError(t("register.form.errorEmailTaken"));
        } else if (errorCode === "weak_password") {
          setServerError(t("register.form.errorWeakPassword"));
        } else if (typeof message === "string" && message) {
          setServerError(message);
        } else {
          setServerError(t("register.form.errorGeneric"));
        }
      } finally {
        setSubmitting(false);
      }
    })();
  };

  const inputClass =
    "w-full py-3 px-3.5 border-[1.5px] border-border rounded-[var(--radius-lg)] text-sm bg-card text-foreground placeholder:text-muted-foreground outline-none focus:border-primary focus:shadow-[0_0_0_3px_var(--primary-light)] transition-all";

  const inputErrorClass =
    "w-full py-3 px-3.5 border-[1.5px] border-red-400 rounded-[var(--radius-lg)] text-sm bg-card text-foreground placeholder:text-muted-foreground outline-none focus:border-red-500 focus:shadow-[0_0_0_3px_rgba(248,113,113,0.2)] transition-all";

  return (
    <div className="min-h-[80vh] flex items-center justify-center p-6 bg-background">
      <div className="w-full max-w-[480px] bg-card border border-border rounded-[var(--radius-xl)] p-10">
        {/* Header */}
        <div className="text-center mb-8">
          <div className="flex items-center justify-center gap-2 mb-4">
            <div className="w-10 h-10 rounded-[var(--radius-lg)] bg-primary flex items-center justify-center text-primary-foreground">
              <Sparkles className="w-5 h-5" />
            </div>
            <span className="font-extrabold text-xl text-foreground">VNShop</span>
          </div>
          <h2 className="text-2xl font-bold text-foreground mb-2">
            {t("register.title", { defaultValue: "Create your account" })}
          </h2>
          <p className="text-sm text-muted-foreground">
            {t("register.subtitle", { defaultValue: "Join millions of buyers and sellers today" })}
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          {/* First name + Last name side by side */}
          <div className="grid grid-cols-2 gap-3">
            <div className="mb-4">
              <label
                htmlFor="firstName"
                className="block text-[13px] font-medium text-foreground mb-1.5"
              >
                {t("register.form.firstNameLabel", { defaultValue: "First Name" })}
              </label>
              <input
                id="firstName"
                type="text"
                autoComplete="given-name"
                required
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
                className={errors.firstName ? inputErrorClass : inputClass}
              />
              {errors.firstName ? (
                <p className="flex items-center gap-1 mt-1.5 text-xs text-red-600">
                  <AlertCircle className="w-3.5 h-3.5 flex-shrink-0" />
                  <span>{errors.firstName}</span>
                </p>
              ) : null}
            </div>
            <div className="mb-4">
              <label
                htmlFor="lastName"
                className="block text-[13px] font-medium text-foreground mb-1.5"
              >
                {t("register.form.lastNameLabel", { defaultValue: "Last Name" })}
              </label>
              <input
                id="lastName"
                type="text"
                autoComplete="family-name"
                required
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
                className={errors.lastName ? inputErrorClass : inputClass}
              />
              {errors.lastName ? (
                <p className="flex items-center gap-1 mt-1.5 text-xs text-red-600">
                  <AlertCircle className="w-3.5 h-3.5 flex-shrink-0" />
                  <span>{errors.lastName}</span>
                </p>
              ) : null}
            </div>
          </div>

          {/* Email */}
          <div className="mb-4">
            <label
              htmlFor="email"
              className="block text-[13px] font-medium text-foreground mb-1.5"
            >
              {t("register.form.emailLabel", { defaultValue: "Email" })}
            </label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              aria-describedby={errors.email ? "register-error-email" : undefined}
              className={errors.email ? inputErrorClass : inputClass}
            />
            {errors.email ? (
              <p id="register-error-email" className="flex items-center gap-1 mt-1.5 text-xs text-red-600">
                <AlertCircle className="w-3.5 h-3.5 flex-shrink-0" />
                <span>{errors.email}</span>
              </p>
            ) : null}
          </div>

          {/* Phone */}
          <div className="mb-4">
            <label
              htmlFor="phone"
              className="block text-[13px] font-medium text-foreground mb-1.5"
            >
              {t("register.form.phoneLabel", { defaultValue: "Phone Number" })}
            </label>
            <input
              id="phone"
              type="tel"
              autoComplete="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="+84..."
              className={inputClass}
            />
          </div>

          {/* Password */}
          <div className="mb-4">
            <label
              htmlFor="password"
              className="block text-[13px] font-medium text-foreground mb-1.5"
            >
              {t("register.form.passwordLabel", { defaultValue: "Password" })}
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
                placeholder={t("register.form.passwordHint", { defaultValue: "At least 8 characters" })}
                className={`${errors.password ? inputErrorClass : inputClass} pr-11`}
              />
              <button
                type="button"
                onClick={() => setShowPassword((v) => !v)}
                className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 rounded text-muted-foreground hover:text-foreground transition-colors"
                aria-label={showPassword ? "Hide password" : "Show password"}
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
            {errors.password ? (
              <p className="flex items-center gap-1 mt-1.5 text-xs text-red-600">
                <AlertCircle className="w-3.5 h-3.5 flex-shrink-0" />
                <span>{errors.password}</span>
              </p>
            ) : pwFocused ? (
              <p className="text-xs text-muted-foreground mt-1.5">
                {t("register.form.passwordHint", { defaultValue: "At least 8 characters" })}
              </p>
            ) : null}
          </div>

          {/* Confirm password */}
          <div className="mb-4">
            <label
              htmlFor="confirm"
              className="block text-[13px] font-medium text-foreground mb-1.5"
            >
              {t("register.form.confirmLabel", { defaultValue: "Confirm Password" })}
            </label>
            <input
              id="confirm"
              type={showPassword ? "text" : "password"}
              autoComplete="new-password"
              required
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              className={errors.confirm ? inputErrorClass : inputClass}
            />
            {errors.confirm ? (
              <p className="flex items-center gap-1 mt-1.5 text-xs text-red-600">
                <AlertCircle className="w-3.5 h-3.5 flex-shrink-0" />
                <span>{errors.confirm}</span>
              </p>
            ) : null}
          </div>

          {/* Server Error */}
          {serverError ? (
            <div
              id="register-error"
              role="alert"
              className="flex items-start gap-2 p-3 rounded-[var(--radius-lg)] bg-red-50 border border-red-100 text-sm text-red-700"
            >
              <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
              <span>{serverError}</span>
            </div>
          ) : null}

          {/* Submit */}
          <button
            type="submit"
            disabled={!ready || submitting}
            className="w-full py-3.5 rounded-[var(--radius-lg)] text-white font-bold text-[15px] bg-gradient-to-br from-[#4f46e5] to-[#7c3aed] hover:opacity-90 hover:-translate-y-0.5 transition-all disabled:opacity-60 disabled:cursor-not-allowed flex items-center justify-center gap-2"
          >
            {submitting ? (
              <>
                <span className="w-4 h-4 rounded-full border-2 border-white/30 border-t-white animate-spin" />
                {t("register.form.submitting", { defaultValue: "Creating account..." })}
              </>
            ) : (
              <>
                {t("register.form.submit", { defaultValue: "Create Account" })}
                <ChevronRight className="w-4 h-4" />
              </>
            )}
          </button>
        </form>

        {/* Terms */}
        <p className="mt-4 text-center text-xs text-muted-foreground">
          {t("login.termsNotice", {
            defaultValue: "By creating an account you agree to our Terms of Service and Privacy Policy.",
          })}
        </p>

        {/* Login link */}
        <p className="mt-4 text-center text-sm text-muted-foreground">
          {t("register.haveAccount", { defaultValue: "Already have an account?" })}{" "}
          <button
            type="button"
            onClick={() => void navigate(`/login?next=${encodeURIComponent(next)}`)}
            className="font-medium text-primary hover:underline"
          >
            {t("register.signIn", { defaultValue: "Sign in" })}
          </button>
        </p>
      </div>
    </div>
  );
}
