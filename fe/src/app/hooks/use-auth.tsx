/* eslint-disable react-refresh/only-export-components --
 * AuthProvider colocates with its hooks intentionally — splitting them across
 * files would require updating every import site for marginal HMR benefit.
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import type { KeycloakProfile, KeycloakTokenParsed } from "keycloak-js";
import { getKeycloak } from "../lib/auth/keycloak";

export type Role = "BUYER" | "SELLER" | "ADMIN";

interface AuthState {
  ready: boolean;
  authenticated: boolean;
  token: string | undefined;
  profile: KeycloakProfile | undefined;
  roles: Role[];
  subject: string | undefined;
  login: (redirectTo?: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

function parseRoles(parsed: KeycloakTokenParsed | undefined): Role[] {
  const realm = (parsed?.realm_access?.roles ?? []) as string[];
  return realm.filter((r): r is Role => r === "BUYER" || r === "SELLER" || r === "ADMIN");
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [profile, setProfile] = useState<KeycloakProfile | undefined>(undefined);
  // Bumped on every token rotation. Used to recompute the memoised auth state.
  const [tokenVersion, setTokenVersion] = useState(0);
  const initStarted = useRef(false);

  useEffect(() => {
    if (initStarted.current) return;
    initStarted.current = true;
    const kc = getKeycloak();

    kc.onTokenExpired = () => {
      kc.updateToken(30).catch(() => kc.login());
    };
    const bump = () => setTokenVersion((n) => n + 1);
    kc.onAuthSuccess = bump;
    kc.onAuthRefreshSuccess = bump;
    kc.onAuthLogout = () => {
      setAuthenticated(false);
      setProfile(undefined);
      bump();
    };

    kc.init({
      onLoad: "check-sso",
      pkceMethod: "S256",
      silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
      checkLoginIframe: false,
    })
      .then(async (auth) => {
        setAuthenticated(auth);
        if (auth) {
          try {
            const p = await kc.loadUserProfile();
            setProfile(p);
          } catch {
            // Profile is optional; the token is what matters for API calls.
          }
        }
      })
      .catch(() => {
        // Network failure or invalid config: stay anonymous and let the UI render.
      })
      .finally(() => setReady(true));
  }, []);

  const login = useCallback((redirectTo?: string) => {
    const kc = getKeycloak();
    void kc.login({
      redirectUri: redirectTo ? `${window.location.origin}${redirectTo}` : window.location.href,
    });
  }, []);

  const logout = useCallback(() => {
    const kc = getKeycloak();
    void kc.logout({ redirectUri: window.location.origin });
  }, []);

  // Read the live token state once per render — `tokenVersion` is in deps so
  // we re-snapshot whenever Keycloak fires onAuthRefreshSuccess.
  const value = useMemo<AuthState>(() => {
    const kc = getKeycloak();
    return {
      ready,
      authenticated,
      token: kc.token,
      profile,
      roles: parseRoles(kc.tokenParsed),
      subject: kc.tokenParsed?.sub,
      login,
      logout,
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, authenticated, profile, tokenVersion, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}

export function useHasRole(role: Role): boolean {
  const { roles } = useAuth();
  return roles.includes(role);
}
