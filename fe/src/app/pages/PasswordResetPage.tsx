import { Mail, Send, ChevronRight, AlertCircle, CheckCircle2 } from "lucide-react";
import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";

const env = import.meta.env as Record<string, string | undefined>;
const API_URL = env.VITE_API_URL ?? "http://localhost:8080";

/**
 * Native password-reset request page. Replaces the old "forgot password"
 * link that bounced users out to Keycloak's account console — Keycloak
 * chrome doesn't fit our brand and the redirect was the last seam where
 * KC hostnames leaked through to the buyer.
 *
 * The actual reset flow stays Keycloak-mediated: this page POSTs the email
 * to user-service /auth/password-reset-request, user-service asks Keycloak
 * to email an UPDATE_PASSWORD action token, and the buyer follows the
 * link from their inbox to KC's reset-credentials page. Hosting that
 * prompt natively too is a separate item — the email-and-redirect contract
 * is what KC's action-token system gives us out of the box.
 *
 * Always shows the generic success message regardless of whether the email
 * exists in the realm. The BE pattern matches: no enumeration of who
 * registered what.
 */
export function PasswordResetPage() {
  const { t } = useTranslation();
  const [email, setEmail] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (submitting) return;
    setError(null);
    setSubmitting(true);
    void (async () => {
      try {
        const res = await fetch(`${API_URL}/auth/password-reset-request`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify({ email: email.trim() }),
        });
        if (!res.ok && res.status !== 202) {
          // 4xx other than the expected 202: surface a generic error.
          // The BE never leaks "user not found" so any visible failure
          // is a transport / validation problem.
          setError(t("passwordReset.errorGeneric"));
          return;
        }
        setSubmitted(true);
      } catch {
        setError(t("passwordReset.errorGeneric"));
      } finally {
        setSubmitting(false);
      }
    })();
  };

  return (
    <div
      className="min-h-screen flex items-start justify-center p-6 lg:py-16"
      style={{ background: "linear-gradient(135deg, #f0fffe 0%, #fff8f0 100%)" }}
    >
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div
            className="w-12 h-12 rounded-2xl mx-auto mb-4 flex items-center justify-center"
            style={{ background: "rgba(0,191,179,0.1)" }}
          >
            <Mail size={24} color="#00BFB3" />
          </div>
          <h2 className="text-2xl font-bold text-gray-800 mb-2">
            {t("passwordReset.title")}
          </h2>
          <p className="text-sm text-gray-500">{t("passwordReset.subtitle")}</p>
        </div>

        {submitted ? (
          <div className="space-y-4">
            <div className="flex items-start gap-3 p-4 rounded-xl bg-emerald-50 border border-emerald-100 text-sm text-emerald-800">
              <CheckCircle2 size={18} className="flex-shrink-0 mt-0.5" />
              <div>
                <p className="font-medium">{t("passwordReset.successTitle")}</p>
                <p className="mt-1 text-emerald-700">{t("passwordReset.successBody")}</p>
              </div>
            </div>
            <Link
              to="/login"
              className="block w-full text-center px-4 py-2.5 rounded-xl text-white font-medium"
              style={{ background: "#00BFB3" }}
            >
              <ChevronRight size={16} className="inline mr-1" />
              {t("passwordReset.backToLogin")}
            </Link>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <div>
              <label
                htmlFor="email"
                className="block text-sm font-medium text-gray-700 mb-1.5"
              >
                {t("passwordReset.emailLabel")}
              </label>
              <input
                id="email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder={t("passwordReset.emailPlaceholder")}
                className="w-full px-3.5 py-2.5 rounded-xl border border-gray-200 text-sm outline-none focus:border-[#00BFB3] focus:ring-2 focus:ring-[#00BFB3]/20 bg-white"
              />
            </div>

            {error ? (
              <div className="flex items-start gap-2 p-3 rounded-xl bg-red-50 border border-red-100 text-sm text-red-700">
                <AlertCircle size={16} className="flex-shrink-0 mt-0.5" />
                <span>{error}</span>
              </div>
            ) : null}

            <button
              type="submit"
              disabled={submitting || email.trim().length === 0}
              className="w-full px-4 py-2.5 rounded-xl text-white font-medium disabled:opacity-60 disabled:cursor-not-allowed"
              style={{ background: "#00BFB3" }}
            >
              <Send size={16} className="inline mr-1.5" />
              {submitting ? t("passwordReset.submitting") : t("passwordReset.submit")}
            </button>

            <div className="text-center text-sm text-gray-500">
              <Link to="/login" className="font-medium text-[#00BFB3] hover:underline">
                {t("passwordReset.backToLogin")}
              </Link>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
