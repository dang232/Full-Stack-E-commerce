/**
 * Native ROPC auth client. The FE owns the login form; this module talks
 * directly to Keycloak's token endpoint via the `vnshop-api` public client
 * (directAccessGrants enabled). Tokens are persisted in localStorage so a
 * page refresh keeps the session.
 *
 * Trade-offs called out in the plan:
 * - ROPC is OAuth-deprecated for third-party apps but acceptable for a
 *   first-party storefront UI where the password never leaves our origin.
 * - localStorage tokens are XSS-vulnerable. A future iteration should move
 *   refresh tokens to httpOnly cookies behind a BE proxy.
 */

const env = import.meta.env as Record<string, string | undefined>;
const KEYCLOAK_URL = env.VITE_KEYCLOAK_URL ?? "http://localhost:8085";
const REALM = env.VITE_KEYCLOAK_REALM ?? "vnshop";
const CLIENT_ID = env.VITE_KEYCLOAK_CLIENT_ID ?? "vnshop-api";

const TOKEN_ENDPOINT = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`;
const LOGOUT_ENDPOINT = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/logout`;

const STORAGE_KEY = "vnshop.auth.tokens";

export interface TokenSet {
  accessToken: string;
  refreshToken: string;
  /** Epoch milliseconds. */
  accessExpiresAt: number;
  /** Epoch milliseconds. */
  refreshExpiresAt: number;
}

export interface JwtClaims {
  sub: string;
  email?: string;
  given_name?: string;
  family_name?: string;
  preferred_username?: string;
  realm_access?: { roles?: string[] };
  exp?: number;
}

export class AuthError extends Error {
  readonly statusCode: number;
  readonly errorCode: string;
  constructor(statusCode: number, errorCode: string, message: string) {
    super(message);
    this.name = "AuthError";
    this.statusCode = statusCode;
    this.errorCode = errorCode;
  }
}

/** Module-level live reference. Kept in sync by the AuthProvider so the api client can read the current token without going through React. */
let liveTokenSet: TokenSet | null = null;
let inFlightRefresh: Promise<TokenSet> | null = null;

export function getAccessToken(): string | null {
  return liveTokenSet?.accessToken ?? null;
}

export function setLiveTokenSet(next: TokenSet | null): void {
  liveTokenSet = next;
}

export function loadStoredTokenSet(): TokenSet | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as TokenSet;
    if (
      typeof parsed?.accessToken !== "string" ||
      typeof parsed?.refreshToken !== "string" ||
      typeof parsed?.accessExpiresAt !== "number" ||
      typeof parsed?.refreshExpiresAt !== "number"
    ) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function saveTokenSet(tokens: TokenSet | null): void {
  if (tokens) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
  } else {
    localStorage.removeItem(STORAGE_KEY);
  }
}

interface KeycloakTokenResponse {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  refresh_expires_in: number;
}

function tokenSetFrom(response: KeycloakTokenResponse): TokenSet {
  const now = Date.now();
  return {
    accessToken: response.access_token,
    refreshToken: response.refresh_token,
    accessExpiresAt: now + response.expires_in * 1000,
    refreshExpiresAt: now + response.refresh_expires_in * 1000,
  };
}

async function postForm(body: URLSearchParams): Promise<Response> {
  return fetch(TOKEN_ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
}

export async function passwordLogin(username: string, password: string): Promise<TokenSet> {
  const body = new URLSearchParams({
    grant_type: "password",
    client_id: CLIENT_ID,
    username,
    password,
    scope: "openid profile email",
  });
  const res = await postForm(body);
  if (!res.ok) {
    if (res.status === 401) {
      throw new AuthError(401, "invalid_credentials", "Invalid email/username or password");
    }
    const text = await res.text().catch(() => "");
    throw new AuthError(res.status, "auth_failed", text || `Login failed (HTTP ${res.status})`);
  }
  return tokenSetFrom((await res.json()) as KeycloakTokenResponse);
}

export async function refreshTokens(refreshToken: string): Promise<TokenSet> {
  if (inFlightRefresh) return inFlightRefresh;
  inFlightRefresh = (async () => {
    const body = new URLSearchParams({
      grant_type: "refresh_token",
      client_id: CLIENT_ID,
      refresh_token: refreshToken,
    });
    const res = await postForm(body);
    if (!res.ok) {
      throw new AuthError(res.status, "refresh_failed", `Refresh failed (HTTP ${res.status})`);
    }
    return tokenSetFrom((await res.json()) as KeycloakTokenResponse);
  })().finally(() => {
    inFlightRefresh = null;
  });
  return inFlightRefresh;
}

export async function revokeTokens(refreshToken: string): Promise<void> {
  try {
    await fetch(LOGOUT_ENDPOINT, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        client_id: CLIENT_ID,
        refresh_token: refreshToken,
      }),
    });
  } catch {
    // Best-effort. Local state is cleared regardless.
  }
}

export function decodeJwt(token: string): JwtClaims | null {
  try {
    const segment = token.split(".")[1];
    if (!segment) return null;
    const padded = segment + "=".repeat((4 - (segment.length % 4)) % 4);
    const json = atob(padded.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(decodeURIComponent(escape(json))) as JwtClaims;
  } catch {
    return null;
  }
}
