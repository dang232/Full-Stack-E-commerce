import { v4 as uuidv4 } from "uuid";
import type { z } from "zod";

import { getKeycloak } from "../auth/keycloak";

import { ApiError } from "./envelope";
import {
  authInterceptor,
  contentTypeInterceptor,
  correlationIdInterceptor,
  envelopeInterceptor,
  errorStatusInterceptor,
  idempotencyInterceptor,
  jsonParseInterceptor,
  unauthorizedInterceptor,
  UnauthorizedError,
  type ErrorInterceptor,
  type RequestContext,
  type RequestInterceptor,
  type ResponseContext,
  type ResponseInterceptor,
} from "./interceptors";

const BASE_URL = (
  (import.meta.env as Record<string, string | undefined>).VITE_API_URL ?? "http://localhost:8080"
).replace(/\/$/, "");

type Method = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export interface RequestOptions<TSchema extends z.ZodType> {
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

function buildUrl(path: string, query?: RequestOptions<z.ZodType>["query"]): string {
  const url = new URL(path.startsWith("/") ? `${BASE_URL}${path}` : `${BASE_URL}/${path}`);
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v === undefined || v === null) continue;
      url.searchParams.set(k, String(v));
    }
  }
  return url.toString();
}

/**
 * Default request interceptor chain. Order matters: correlation id first so it
 * appears on every request, content-type/idempotency before auth so an auth
 * refresh failure doesn't strip headers we already set.
 */
const REQUEST_CHAIN: readonly RequestInterceptor[] = [
  correlationIdInterceptor,
  contentTypeInterceptor,
  idempotencyInterceptor,
  authInterceptor,
];

/** Error interceptor chain. Currently only handles 401-with-refresh. */
const ERROR_CHAIN: readonly ErrorInterceptor[] = [unauthorizedInterceptor];

async function runRequestChain(ctx: RequestContext): Promise<RequestContext> {
  let current = ctx;
  for (const interceptor of REQUEST_CHAIN) {
    current = await interceptor(current);
  }
  return current;
}

async function runResponseChain(
  ctx: ResponseContext,
  responseChain: readonly ResponseInterceptor[],
): Promise<ResponseContext> {
  let current = ctx;
  for (const interceptor of responseChain) {
    current = await interceptor(current);
  }
  return current;
}

/**
 * Walks the error interceptor chain. The first interceptor that returns a
 * `Response` short-circuits and that response becomes the new pipeline result.
 * If every interceptor returns `void`, the original error is re-thrown.
 */
async function runErrorChain(err: unknown, ctx: RequestContext): Promise<Response> {
  for (const interceptor of ERROR_CHAIN) {
    const result = await interceptor(err, ctx);
    if (result instanceof Response) return result;
  }
  throw err;
}

export async function request<TSchema extends z.ZodType>(
  opts: RequestOptions<TSchema>,
): Promise<z.infer<TSchema>> {
  const method: Method = opts.method ?? "GET";
  const auth = opts.auth ?? true;
  const correlationId = uuidv4();
  const url = buildUrl(opts.path, opts.query);
  const hasBody = opts.body !== undefined && method !== "GET";

  const init: RequestInit = {
    method,
    headers: {},
    body: hasBody ? JSON.stringify(opts.body) : undefined,
    signal: opts.signal,
    credentials: "omit",
  };

  const requestCtx = await runRequestChain({
    url,
    init,
    correlationId,
    meta: { auth, idempotencyKey: opts.idempotencyKey, hasBody },
  });

  let response = await fetch(requestCtx.url, requestCtx.init);

  // 401 path: surface a sentinel through the error chain so interceptors can
  // attempt token refresh + retry. If still 401 after, force re-login.
  if (response.status === 401 && auth) {
    try {
      response = await runErrorChain(new UnauthorizedError(response), requestCtx);
    } catch {
      const kc = getKeycloak();
      void kc.login();
      throw new ApiError(401, "UNAUTHORIZED", "Authentication required", correlationId);
    }
    if (response.status === 401) {
      const kc = getKeycloak();
      void kc.login();
      throw new ApiError(401, "UNAUTHORIZED", "Authentication required", correlationId);
    }
  }

  const responseChain: readonly ResponseInterceptor[] = [
    jsonParseInterceptor,
    errorStatusInterceptor,
    envelopeInterceptor(opts.schema),
  ];

  const finalCtx = await runResponseChain(
    { request: requestCtx, response, parsed: null },
    responseChain,
  );
  return finalCtx.parsed as z.infer<TSchema>;
}

export const api = {
  get: <T extends z.ZodType>(
    path: string,
    schema: T,
    query?: RequestOptions<T>["query"],
    opts?: Pick<RequestOptions<T>, "auth" | "signal">,
  ) => request({ method: "GET", path, schema, query, ...opts }),
  post: <T extends z.ZodType>(
    path: string,
    schema: T,
    body?: unknown,
    opts?: Pick<RequestOptions<T>, "auth" | "signal" | "idempotencyKey">,
  ) => request({ method: "POST", path, schema, body, ...opts }),
  put: <T extends z.ZodType>(
    path: string,
    schema: T,
    body?: unknown,
    opts?: Pick<RequestOptions<T>, "auth" | "signal">,
  ) => request({ method: "PUT", path, schema, body, ...opts }),
  patch: <T extends z.ZodType>(
    path: string,
    schema: T,
    body?: unknown,
    opts?: Pick<RequestOptions<T>, "auth" | "signal">,
  ) => request({ method: "PATCH", path, schema, body, ...opts }),
  delete: <T extends z.ZodType>(
    path: string,
    schema: T,
    opts?: Pick<RequestOptions<T>, "auth" | "signal">,
  ) => request({ method: "DELETE", path, schema, ...opts }),
};
