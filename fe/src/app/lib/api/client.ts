import { v4 as uuidv4 } from "uuid";
import type { z } from "zod";
import { apiResponseSchema, ApiError } from "./envelope";
import { getKeycloak, refreshToken } from "../auth/keycloak";

const BASE_URL = (import.meta.env.VITE_API_URL ?? "http://localhost:8080").replace(/\/$/, "");

type Method = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export interface RequestOptions<TSchema extends z.ZodTypeAny> {
  method?: Method;
  path: string;
  query?: Record<string, string | number | boolean | undefined | null>;
  body?: unknown;
  schema: TSchema;
  signal?: AbortSignal;
  /** Send Authorization even when anonymous endpoints would work — defaults to true. */
  auth?: boolean;
  /** Adds Idempotency-Key header (used by POST /orders). */
  idempotencyKey?: string;
}

function buildUrl(path: string, query?: RequestOptions<z.ZodTypeAny>["query"]): string {
  const url = new URL(path.startsWith("/") ? `${BASE_URL}${path}` : `${BASE_URL}/${path}`);
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v === undefined || v === null) continue;
      url.searchParams.set(k, String(v));
    }
  }
  return url.toString();
}

async function buildHeaders(opts: { auth: boolean; correlationId: string; idempotencyKey?: string; hasBody: boolean }) {
  const headers: Record<string, string> = {
    Accept: "application/json",
    "X-Correlation-Id": opts.correlationId,
  };
  if (opts.hasBody) headers["Content-Type"] = "application/json";
  if (opts.idempotencyKey) headers["Idempotency-Key"] = opts.idempotencyKey;
  if (opts.auth) {
    const kc = getKeycloak();
    if (kc.authenticated) {
      // Refresh if it expires in <30s; ignore failure here, the 401 retry path will handle it.
      await refreshToken(30).catch(() => undefined);
      if (kc.token) headers.Authorization = `Bearer ${kc.token}`;
    }
  }
  return headers;
}

export async function request<TSchema extends z.ZodTypeAny>(
  opts: RequestOptions<TSchema>,
): Promise<z.infer<TSchema>> {
  const method: Method = opts.method ?? "GET";
  const auth = opts.auth ?? true;
  const correlationId = uuidv4();
  const url = buildUrl(opts.path, opts.query);
  const hasBody = opts.body !== undefined && method !== "GET";

  const send = async (): Promise<Response> => {
    const headers = await buildHeaders({
      auth,
      correlationId,
      idempotencyKey: opts.idempotencyKey,
      hasBody,
    });
    return fetch(url, {
      method,
      headers,
      body: hasBody ? JSON.stringify(opts.body) : undefined,
      signal: opts.signal,
      credentials: "omit",
    });
  };

  let response = await send();

  if (response.status === 401 && auth) {
    const refreshed = await refreshToken(0).catch(() => false);
    if (refreshed) {
      response = await send();
    }
    if (response.status === 401) {
      const kc = getKeycloak();
      // Best-effort redirect to login. Caller still gets the rejection so React Query stops.
      void kc.login();
      throw new ApiError(401, "UNAUTHORIZED", "Authentication required", correlationId);
    }
  }

  const serverCorrelationId = response.headers.get("x-correlation-id") ?? correlationId;
  const text = await response.text();
  let parsed: unknown = null;
  if (text.length > 0) {
    try {
      parsed = JSON.parse(text);
    } catch {
      throw new ApiError(response.status, "INVALID_JSON", "Server returned non-JSON response", serverCorrelationId);
    }
  }

  if (!response.ok) {
    const code =
      parsed && typeof parsed === "object" && "errorCode" in parsed && typeof (parsed as Record<string, unknown>).errorCode === "string"
        ? ((parsed as Record<string, unknown>).errorCode as string)
        : null;
    const message =
      parsed && typeof parsed === "object" && "message" in parsed && typeof (parsed as Record<string, unknown>).message === "string"
        ? ((parsed as Record<string, unknown>).message as string)
        : `HTTP ${response.status}`;
    throw new ApiError(response.status, code, message, serverCorrelationId);
  }

  const envelope = apiResponseSchema(opts.schema).safeParse(parsed);
  if (!envelope.success) {
    throw new ApiError(response.status, "MALFORMED_RESPONSE", envelope.error.message, serverCorrelationId);
  }
  if (!envelope.data.success) {
    throw new ApiError(response.status, envelope.data.errorCode, envelope.data.message, serverCorrelationId);
  }
  return envelope.data.data;
}

export const api = {
  get: <T extends z.ZodTypeAny>(path: string, schema: T, query?: RequestOptions<T>["query"], opts?: Pick<RequestOptions<T>, "auth" | "signal">) =>
    request({ method: "GET", path, schema, query, ...opts }),
  post: <T extends z.ZodTypeAny>(path: string, schema: T, body?: unknown, opts?: Pick<RequestOptions<T>, "auth" | "signal" | "idempotencyKey">) =>
    request({ method: "POST", path, schema, body, ...opts }),
  put: <T extends z.ZodTypeAny>(path: string, schema: T, body?: unknown, opts?: Pick<RequestOptions<T>, "auth" | "signal">) =>
    request({ method: "PUT", path, schema, body, ...opts }),
  patch: <T extends z.ZodTypeAny>(path: string, schema: T, body?: unknown, opts?: Pick<RequestOptions<T>, "auth" | "signal">) =>
    request({ method: "PATCH", path, schema, body, ...opts }),
  delete: <T extends z.ZodTypeAny>(path: string, schema: T, opts?: Pick<RequestOptions<T>, "auth" | "signal">) =>
    request({ method: "DELETE", path, schema, ...opts }),
};
