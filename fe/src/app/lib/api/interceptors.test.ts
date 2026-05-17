import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { z } from "zod";

// Mock native-auth BEFORE importing anything that pulls it in.
let liveToken: string | null = null;
let storedTokenSet: { accessToken: string; refreshToken: string; accessExpiresAt: number; refreshExpiresAt: number } | null = null;
const refreshTokensMock = vi.fn();
vi.mock("../auth/native-auth", () => ({
  getAccessToken: () => liveToken,
  setLiveTokenSet: vi.fn((next: { accessToken: string } | null) => {
    liveToken = next?.accessToken ?? null;
  }),
  loadStoredTokenSet: () => storedTokenSet,
  saveTokenSet: vi.fn((next: typeof storedTokenSet) => {
    storedTokenSet = next;
  }),
  refreshTokens: (...args: unknown[]) => refreshTokensMock(...args),
}));

import { api, request } from "./client";
import { ApiError } from "./envelope";
import {
  authInterceptor,
  contentTypeInterceptor,
  correlationIdInterceptor,
  envelopeInterceptor,
  errorStatusInterceptor,
  idempotencyInterceptor,
  jsonParseInterceptor,
  type RequestContext,
  type ResponseContext,
} from "./interceptors";

const fetchSpy = vi.spyOn(global, "fetch");

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "content-type": "application/json", ...(init.headers ?? {}) },
    ...init,
  });
}

function makeRequestCtx(overrides: Partial<RequestContext> = {}): RequestContext {
  return {
    url: "http://localhost:8080/x",
    init: { method: "GET", headers: {} },
    correlationId: "cid-test",
    meta: { auth: false, hasBody: false },
    ...overrides,
  };
}

beforeEach(() => {
  fetchSpy.mockReset();
  refreshTokensMock.mockReset();
  liveToken = null;
  storedTokenSet = null;
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("request interceptors", () => {
  it("correlationIdInterceptor sets X-Correlation-Id from ctx", async () => {
    const ctx = makeRequestCtx({ correlationId: "abc-123" });
    const out = await correlationIdInterceptor(ctx);
    expect((out.init.headers as Record<string, string>)["X-Correlation-Id"]).toBe("abc-123");
  });

  it("contentTypeInterceptor sets Accept and Content-Type only when body is present", async () => {
    const noBody = await contentTypeInterceptor(makeRequestCtx());
    const noBodyHeaders = noBody.init.headers as Record<string, string>;
    expect(noBodyHeaders.Accept).toBe("application/json");
    expect(noBodyHeaders["Content-Type"]).toBeUndefined();

    const withBody = await contentTypeInterceptor(
      makeRequestCtx({ meta: { auth: false, hasBody: true } }),
    );
    expect((withBody.init.headers as Record<string, string>)["Content-Type"]).toBe(
      "application/json",
    );
  });

  it("idempotencyInterceptor only writes the header when a key is supplied", async () => {
    const without = await idempotencyInterceptor(makeRequestCtx());
    expect((without.init.headers as Record<string, string>)["Idempotency-Key"]).toBeUndefined();

    const withKey = await idempotencyInterceptor(
      makeRequestCtx({ meta: { auth: false, hasBody: false, idempotencyKey: "key-1" } }),
    );
    expect((withKey.init.headers as Record<string, string>)["Idempotency-Key"]).toBe("key-1");
  });

  it("authInterceptor skips Authorization when meta.auth is false", async () => {
    liveToken = "jwt";
    const out = await authInterceptor(makeRequestCtx({ meta: { auth: false, hasBody: false } }));
    expect((out.init.headers as Record<string, string>).Authorization).toBeUndefined();
  });

  it("authInterceptor sets Authorization from the live access token", async () => {
    liveToken = "jwt-xyz";
    const out = await authInterceptor(makeRequestCtx({ meta: { auth: true, hasBody: false } }));
    expect((out.init.headers as Record<string, string>).Authorization).toBe("Bearer jwt-xyz");
  });

  it("authInterceptor leaves Authorization unset when no token is loaded", async () => {
    liveToken = null;
    const out = await authInterceptor(makeRequestCtx({ meta: { auth: true, hasBody: false } }));
    expect((out.init.headers as Record<string, string>).Authorization).toBeUndefined();
  });
});

describe("response interceptors", () => {
  it("jsonParseInterceptor populates parsed for valid JSON", async () => {
    const ctx: ResponseContext = {
      request: makeRequestCtx(),
      response: jsonResponse({ hello: "world" }),
      parsed: null,
    };
    const out = await jsonParseInterceptor(ctx);
    expect(out.parsed).toEqual({ hello: "world" });
  });

  it("jsonParseInterceptor throws INVALID_JSON for non-JSON bodies", async () => {
    const ctx: ResponseContext = {
      request: makeRequestCtx(),
      response: new Response("<html>oops</html>", { status: 200 }),
      parsed: null,
    };
    await expect(jsonParseInterceptor(ctx)).rejects.toMatchObject({
      name: "ApiError",
      errorCode: "INVALID_JSON",
    });
  });

  it("errorStatusInterceptor passes 2xx through unchanged", async () => {
    const ctx: ResponseContext = {
      request: makeRequestCtx(),
      response: new Response(null, { status: 204 }),
      parsed: null,
    };
    const out = await errorStatusInterceptor(ctx);
    expect(out).toBe(ctx);
  });

  it("errorStatusInterceptor throws ApiError with errorCode/message from the body on non-2xx", () => {
    const ctx: ResponseContext = {
      request: makeRequestCtx(),
      response: new Response("ignored", { status: 500 }),
      parsed: { errorCode: "BOOM", message: "engine failed" },
    };
    expect(() => errorStatusInterceptor(ctx)).toThrow(ApiError);
    try {
      errorStatusInterceptor(ctx);
    } catch (err) {
      expect((err as ApiError).status).toBe(500);
      expect((err as ApiError).errorCode).toBe("BOOM");
      expect((err as ApiError).message).toBe("engine failed");
    }
  });

  it("envelopeInterceptor unwraps the data field on success", async () => {
    const schema = z.object({ id: z.string() });
    const interceptor = envelopeInterceptor(schema);
    const ctx: ResponseContext = {
      request: makeRequestCtx(),
      response: new Response(null, { status: 200 }),
      parsed: {
        success: true,
        message: "ok",
        data: { id: "p1" },
        errorCode: null,
        timestamp: "2026-05-15T00:00:00Z",
      },
    };
    const out = await interceptor(ctx);
    expect(out.parsed).toEqual({ id: "p1" });
  });

  it("envelopeInterceptor throws ApiError when envelope.success is false", () => {
    const interceptor = envelopeInterceptor(z.object({ id: z.string() }));
    const ctx: ResponseContext = {
      request: makeRequestCtx(),
      response: new Response(null, { status: 200, headers: { "x-correlation-id": "cid-9" } }),
      parsed: {
        success: false,
        message: "denied",
        data: { id: "p1" },
        errorCode: "FORBIDDEN",
        timestamp: "2026-05-15T00:00:00Z",
      },
    };
    expect(() => interceptor(ctx)).toThrow(ApiError);
  });
});

describe("interceptor chain ordering (via request())", () => {
  it("applies request interceptors in order: correlation-id, content-type, idempotency, auth", async () => {
    liveToken = "jwt";
    fetchSpy.mockResolvedValueOnce(
      jsonResponse({
        success: true,
        message: "ok",
        data: { id: "ord-1" },
        errorCode: null,
        timestamp: "2026-05-15T00:00:00Z",
      }),
    );

    await api.post(
      "/orders",
      z.object({ id: z.string() }),
      { items: [] },
      { idempotencyKey: "key-2" },
    );

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const init = fetchSpy.mock.calls[0][1];
    const headers = init?.headers as Record<string, string>;
    expect(headers["X-Correlation-Id"]).toMatch(/^[0-9a-f-]{36}$/);
    expect(headers.Accept).toBe("application/json");
    expect(headers["Content-Type"]).toBe("application/json");
    expect(headers["Idempotency-Key"]).toBe("key-2");
    expect(headers.Authorization).toBe("Bearer jwt");
  });
});

describe("401 retry path", () => {
  it("on 401: refreshes token then retries the same URL with the new bearer", async () => {
    liveToken = "old-jwt";
    storedTokenSet = {
      accessToken: "old-jwt",
      refreshToken: "rt",
      accessExpiresAt: Date.now() - 1,
      refreshExpiresAt: Date.now() + 60_000,
    };
    refreshTokensMock.mockImplementation(async () => ({
      accessToken: "new-jwt",
      refreshToken: "rt",
      accessExpiresAt: Date.now() + 60_000,
      refreshExpiresAt: Date.now() + 600_000,
    }));

    fetchSpy.mockResolvedValueOnce(new Response("unauthorized", { status: 401 }));
    fetchSpy.mockImplementationOnce(async () =>
      jsonResponse({
        success: true,
        message: "ok",
        data: { id: "p1" },
        errorCode: null,
        timestamp: "2026-05-15T00:00:00Z",
      }),
    );

    const result = await request({
      method: "GET",
      path: "/me",
      schema: z.object({ id: z.string() }),
    });

    expect(result).toEqual({ id: "p1" });
    expect(fetchSpy).toHaveBeenCalledTimes(2);
    const retryInit = fetchSpy.mock.calls[1][1];
    const retryHeaders = retryInit?.headers as Record<string, string>;
    expect(retryHeaders.Authorization).toBe("Bearer new-jwt");
  });

  it("on 401 then refresh fails: throws ApiError UNAUTHORIZED and dispatches auth:unauthorized", async () => {
    liveToken = "old-jwt";
    storedTokenSet = {
      accessToken: "old-jwt",
      refreshToken: "rt",
      accessExpiresAt: Date.now() - 1,
      refreshExpiresAt: Date.now() + 60_000,
    };
    refreshTokensMock.mockRejectedValue(new Error("refresh denied"));

    fetchSpy.mockResolvedValueOnce(new Response("unauthorized", { status: 401 }));

    const dispatchSpy = vi.spyOn(window, "dispatchEvent");
    const err = await request({
      method: "GET",
      path: "/me",
      schema: z.object({ id: z.string() }),
    }).catch((e) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(401);
    expect((err as ApiError).errorCode).toBe("UNAUTHORIZED");
    expect(dispatchSpy).toHaveBeenCalled();
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });

  it("on 401 retry that returns 401 again: throws UNAUTHORIZED", async () => {
    liveToken = "old-jwt";
    storedTokenSet = {
      accessToken: "old-jwt",
      refreshToken: "rt",
      accessExpiresAt: Date.now() - 1,
      refreshExpiresAt: Date.now() + 60_000,
    };
    refreshTokensMock.mockResolvedValue({
      accessToken: "new-jwt",
      refreshToken: "rt",
      accessExpiresAt: Date.now() + 60_000,
      refreshExpiresAt: Date.now() + 600_000,
    });

    fetchSpy.mockResolvedValueOnce(new Response("unauthorized", { status: 401 }));
    fetchSpy.mockResolvedValueOnce(new Response("still unauthorized", { status: 401 }));

    const err = await request({
      method: "GET",
      path: "/me",
      schema: z.object({ id: z.string() }),
    }).catch((e) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(401);
    expect(fetchSpy).toHaveBeenCalledTimes(2);
  });
});
