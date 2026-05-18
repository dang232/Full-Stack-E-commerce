/**
 * Native auth client backed by the user-service /auth proxy.
 *
 * <p>Refresh token lives in an httpOnly cookie ({@code vnshop_rt},
 * {@code Path=/auth}, {@code SameSite=Lax}) issued by user-service on login.
 * The access token never touches localStorage — it sits in module memory
 * only ({@link liveTokenSet}). On page reload, the FE calls
 * {@link refreshTokens} which sends the cookie and gets a fresh access
 * token; if no cookie exists or it's expired, the user is unauthenticated
 * and bounces to /login.
 *
 * <p>This trades the "tokens survive a hard refresh in JS-readable storage"
 * shape for "refresh-token theft via XSS is impossible." Access-token theft
 * via XSS still works in principle, but the window is short (15min) and
 * the access token alone can't bootstrap a new session.
 */

const env = import.meta.env as Record<string, string | undefined>;
const API_URL = env.VITE_API_URL ?? "http://localhost:8080";

const LOGIN_ENDPOINT = `${API_URL}/auth/login`;
const REFRESH_ENDPOINT = `${API_URL}/auth/refresh`;
const LOGOUT_ENDPOINT = `${API_URL}/auth/logout`;

export interface TokenSet {
  accessToken: string;
  /** Epoch milliseconds. */
  accessExpiresAt: number;
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

interface AuthSessionResponse {
  accessToken: string;
  accessExpiresIn: number;
}

interface ApiEnvelope<T> {
  success: boolean;
  message?: string;
  data?: T;
  errorCode?: string;
}

function tokenSetFrom(payload: AuthSessionResponse): TokenSet {
  return {
    accessToken: payload.accessToken,
    accessExpiresAt: Date.now() + payload.accessExpiresIn * 1000,
  };
}

async function postAuth(url: string, body?: unknown): Promise<Response> {
  return fetch(url, {
    method: "POST",
    headers: body != null ? { "Content-Type": "application/json" } : {},
    // CRITICAL: send + receive the vnshop_rt cookie. Without this, the
    // browser strips the cookie on cross-origin requests and refresh
    // returns 401 forever.
    credentials: "include",
    body: body != null ? JSON.stringify(body) : undefined,
  });
}

async function readEnvelope(res: Response, fallbackErrorCode: string): Promise<TokenSet> {
  const text = await res.text().catch(() => "");
  let envelope: ApiEnvelope<AuthSessionResponse> | null = null;
  try {
    envelope = text ? (JSON.parse(text) as ApiEnvelope<AuthSessionResponse>) : null;
  } catch {
    /* keep envelope null and fall through */
  }
  if (!res.ok || !envelope?.data) {
    const code = envelope?.errorCode ?? fallbackErrorCode;
    const message = envelope?.message ?? `Auth failed (HTTP ${res.status})`;
    // Treat invalid-credentials and missing-cookie as 401 to the caller
    // regardless of the underlying transport status, so call sites have a
    // single check to make.
    const status = code === "invalid_credentials" || code === "no_session" ? 401 : res.status;
    throw new AuthError(status, code, message);
  }
  return tokenSetFrom(envelope.data);
}

export async function passwordLogin(username: string, password: string): Promise<TokenSet> {
  const res = await postAuth(LOGIN_ENDPOINT, { username, password });
  return readEnvelope(res, "auth_failed");
}

export async function refreshTokens(): Promise<TokenSet> {
  if (inFlightRefresh) return inFlightRefresh;
  inFlightRefresh = (async () => {
    const res = await postAuth(REFRESH_ENDPOINT);
    return readEnvelope(res, "refresh_failed");
  })().finally(() => {
    inFlightRefresh = null;
  });
  return inFlightRefresh;
}

export async function revokeTokens(): Promise<void> {
  try {
    await postAuth(LOGOUT_ENDPOINT);
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
