import type { z } from "zod";

import { getAccessToken, refreshTokens, setLiveTokenSet } from "../auth/native-auth";

import { apiResponseSchema, ApiError } from "./envelope";

export interface RequestContext {
  url: string;
  init: RequestInit;
  correlationId: string;
  meta: { auth: boolean; idempotencyKey?: string; hasBody: boolean };
}

export interface ResponseContext {
  request: RequestContext;
  response: Response;
  /** Pre-parsed JSON body. `null` until a response interceptor populates it. */
  parsed: unknown;
}

export type RequestInterceptor = (
  ctx: RequestContext,
) => Promise<RequestContext> | RequestContext;
export type ResponseInterceptor = (
  ctx: ResponseContext,
) => Promise<ResponseContext> | ResponseContext;
/**
 * Error interceptor. Returning a `Response` retries the response pipeline with
 * the new response; returning `void` / `undefined` re-throws the original error.
 */
export type ErrorInterceptor = (
  err: unknown,
  ctx: RequestContext,
) => Promise<Response | void> | Response | void;

/** Coerce `init.headers` into a mutable plain record for interceptor edits. */
function ensureHeaders(init: RequestInit): Record<string, string> {
  const existing = init.headers;
  if (!existing) {
    const headers: Record<string, string> = {};
    init.headers = headers;
    return headers;
  }
  if (existing instanceof Headers) {
    const out: Record<string, string> = {};
    existing.forEach((value, key) => {
      out[key] = value;
    });
    init.headers = out;
    return out;
  }
  if (Array.isArray(existing)) {
    const out: Record<string, string> = {};
    for (const [k, v] of existing) out[k] = v;
    init.headers = out;
    return out;
  }
  return existing;
}

// --- Request interceptors --------------------------------------------------

export const correlationIdInterceptor: RequestInterceptor = (ctx) => {
  const headers = ensureHeaders(ctx.init);
  headers["X-Correlation-Id"] = ctx.correlationId;
  return ctx;
};

export const contentTypeInterceptor: RequestInterceptor = (ctx) => {
  const headers = ensureHeaders(ctx.init);
  headers.Accept = "application/json";
  if (ctx.meta.hasBody) headers["Content-Type"] = "application/json";
  return ctx;
};

export const idempotencyInterceptor: RequestInterceptor = (ctx) => {
  if (!ctx.meta.idempotencyKey) return ctx;
  const headers = ensureHeaders(ctx.init);
  headers["Idempotency-Key"] = ctx.meta.idempotencyKey;
  return ctx;
};

export const authInterceptor: RequestInterceptor = (ctx) => {
  if (!ctx.meta.auth) return ctx;
  const token = getAccessToken();
  if (!token) return ctx;
  const headers = ensureHeaders(ctx.init);
  headers.Authorization = `Bearer ${token}`;
  return ctx;
};

// --- Response interceptors -------------------------------------------------

/** Read body once, parse JSON or throw `INVALID_JSON`. Sets `ctx.parsed`. */
export const jsonParseInterceptor: ResponseInterceptor = async (ctx) => {
  const serverCorrelationId =
    ctx.response.headers.get("x-correlation-id") ?? ctx.request.correlationId;
  const text = await ctx.response.text();
  if (text.length === 0) return { ...ctx, parsed: null };
  try {
    return { ...ctx, parsed: JSON.parse(text) };
  } catch {
    throw new ApiError(
      ctx.response.status,
      "INVALID_JSON",
      "Server returned non-JSON response",
      serverCorrelationId,
    );
  }
};

/** Maps non-2xx responses to `ApiError`, pulling errorCode/message from the body when present. */
export const errorStatusInterceptor: ResponseInterceptor = (ctx) => {
  if (ctx.response.ok) return ctx;
  const serverCorrelationId =
    ctx.response.headers.get("x-correlation-id") ?? ctx.request.correlationId;
  const parsed = ctx.parsed;
  const code =
    parsed &&
    typeof parsed === "object" &&
    "errorCode" in parsed &&
    typeof (parsed as Record<string, unknown>).errorCode === "string"
      ? ((parsed as Record<string, unknown>).errorCode as string)
      : null;
  const message =
    parsed &&
    typeof parsed === "object" &&
    "message" in parsed &&
    typeof (parsed as Record<string, unknown>).message === "string"
      ? ((parsed as Record<string, unknown>).message as string)
      : `HTTP ${ctx.response.status}`;
  throw new ApiError(ctx.response.status, code, message, serverCorrelationId);
};

/**
 * Builds a response interceptor that validates the API envelope against the
 * supplied zod schema. On success it replaces `ctx.parsed` with the unwrapped
 * inner `data`. Throws `ApiError` for malformed envelopes or `success: false`.
 */
export function envelopeInterceptor<TSchema extends z.ZodType>(
  schema: TSchema,
): ResponseInterceptor {
  return (ctx) => {
    const serverCorrelationId =
      ctx.response.headers.get("x-correlation-id") ?? ctx.request.correlationId;
    const envelope = apiResponseSchema(schema).safeParse(ctx.parsed);
    if (!envelope.success) {
      throw new ApiError(
        ctx.response.status,
        "MALFORMED_RESPONSE",
        envelope.error.message,
        serverCorrelationId,
      );
    }
    if (!envelope.data.success) {
      throw new ApiError(
        ctx.response.status,
        envelope.data.errorCode,
        envelope.data.message,
        serverCorrelationId,
      );
    }
    return { ...ctx, parsed: envelope.data.data };
  };
}

// --- Error interceptors ----------------------------------------------------

/** Sentinel thrown by the runner when a 401 response should give the error chain a chance. */
export class UnauthorizedError extends Error {
  readonly response: Response;
  constructor(response: Response) {
    super("Unauthorized");
    this.name = "UnauthorizedError";
    this.response = response;
  }
}

/**
 * On a 401 for an authenticated request, try once to refresh tokens via the
 * httpOnly refresh-token cookie. On success, replay the request. On failure
 * (no cookie, expired cookie, revoked token) clear local auth state and
 * dispatch `auth:unauthorized` so AuthProvider boots us back to /login.
 *
 * The refresh token never enters JS — it's sent automatically by the browser
 * because the auth client uses {@code credentials: "include"}.
 */
export const unauthorizedInterceptor: ErrorInterceptor = async (err, ctx) => {
  if (!(err instanceof UnauthorizedError)) return undefined;
  if (!ctx.meta.auth) return undefined;
  try {
    const next = await refreshTokens();
    setLiveTokenSet(next);
    const headers = ensureHeaders(ctx.init);
    headers.Authorization = `Bearer ${next.accessToken}`;
    return fetch(ctx.url, ctx.init);
  } catch {
    setLiveTokenSet(null);
    window.dispatchEvent(new Event("auth:unauthorized"));
    return undefined;
  }
};
