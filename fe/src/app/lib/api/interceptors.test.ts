import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { z } from "zod";

// Mock keycloak BEFORE importing anything that pulls it in.
const mockKeycloak = {
  authenticated: false,
  token: undefined as string | undefined,
  login: vi.fn(),
};
const refreshTokenMock = vi.fn().mockResolvedValue(true);
vi.mock("../auth/keycloak", () => ({
  getKeycloak: () => mockKeycloak,
  refreshToken: (...args: unknown[]) => refreshTokenMock(...args),
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
  refreshTokenMock.mockReset();
  refreshTokenMock.mockResolvedValue(true);
  mockKeycloak.authenticated = false;
  mockKeycloak.token = undefined;
  mockKeycloak.login.mockReset();
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

  it("authInterceptor skips refresh and Authorization when meta.auth is false", async () => {
    mockKeycloak.authenticated = true;
    mockKeycloak.token = "jwt";
    const out = await authInterceptor(makeRequestCtx({ meta: { auth: false, hasBody: false } }));
    expect(refreshTokenMock).not.toHaveBeenCalled();
    expect((out.init.headers as Record<string, string>).Authorization).toBeUndefined();
  });

  it("authInterceptor refreshes and sets Authorization when authenticated", async () => {
    mockKeycloak.authenticated = true;
    mockKeycloak.token = "jwt-xyz";
    const out = await authInterceptor(makeRequestCtx({ meta: { auth: true, hasBody: false } }));
    expect(refreshTokenMock).toHaveBeenCalledWith(30);
    expect((out.init.headers as Record<string, string>).Authorization).toBe("Bearer jwt-xyz");
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
    mockKeycloak.authenticated = true;
    mockKeycloak.token = "jwt";
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
    // correlationIdInterceptor
    expect(headers["X-Correlation-Id"]).toMatch(/^[0-9a-f-]{36}$/);
    // contentTypeInterceptor
    expect(headers.Accept).toBe("application/json");
    expect(headers["Content-Type"]).toBe("application/json");
    // idempotencyInterceptor
    expect(headers["Idempotency-Key"]).toBe("key-2");
    // authInterceptor
    expect(headers.Authorization).toBe("Bearer jwt");
  });
});

describe("401 retry path", () => {
  it("on 401: refreshes token then retries the same URL with the new bearer", async () => {
    mockKeycloak.authenticated = true;
    mockKeycloak.token = "old-jwt";

    // First send -> 401
    fetchSpy.mockResolvedValueOnce(new Response("unauthorized", { status: 401 }));
    // Second send -> 200 envelope
    fetchSpy.mockImplementationOnce(async () => {
      mockKeycloak.token = "new-jwt"; // simulate refresh side effect already happened
      return jsonResponse({
        success: true,
        message: "ok",
        data: { id: "p1" },
        errorCode: null,
        timestamp: "2026-05-15T00:00:00Z",
      });
    });

    // refreshToken called twice during the request: once at request build (minValidity=30)
    // and once on 401 (minValidity=0). Both return true, and the second hop swaps token.
    refreshTokenMock.mockImplementation(async () => {
      mockKeycloak.token = "new-jwt";
      return true;
    });

    const result = await request({
      method: "GET",
      path: "/me",
      schema: z.object({ id: z.string() }),
    });

    expect(result).toEqual({ id: "p1" });
    expect(fetchSpy).toHaveBeenCalledTimes(2);
    const retryInit = fetchSpy.mock.calls[1][1];
    const retryHeaders = retryInit?.headers as Record<string, string>;
    // Retry must use the refreshed token
    expect(retryHeaders.Authorization).toBe("Bearer new-jwt");
    expect(retryHeaders["X-Correlation-Id"]).toBe(
      (fetchSpy.mock.calls[0][1]?.headers as Record<string, string>)["X-Correlation-Id"],
    );
  });

  it("on 401 then refresh fails: throws ApiError UNAUTHORIZED and triggers login()", async () => {
    mockKeycloak.authenticated = true;
    mockKeycloak.token = "old-jwt";
    refreshTokenMock.mockResolvedValue(false);

    fetchSpy.mockResolvedValueOnce(new Response("unauthorized", { status: 401 }));

    const err = await request({
      method: "GET",
      path: "/me",
      schema: z.object({ id: z.string() }),
    }).catch((e) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(401);
    expect((err as ApiError).errorCode).toBe("UNAUTHORIZED");
    expect(mockKeycloak.login).toHaveBeenCalledTimes(1);
    // No retry was attempted because refresh failed
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });

  it("on 401 retry that returns 401 again: throws UNAUTHORIZED and forces login()", async () => {
    mockKeycloak.authenticated = true;
    mockKeycloak.token = "old-jwt";
    refreshTokenMock.mockResolvedValue(true);

    fetchSpy.mockResolvedValueOnce(new Response("unauthorized", { status: 401 }));
    fetchSpy.mockResolvedValueOnce(new Response("still unauthorized", { status: 401 }));

    const err = await request({
      method: "GET",
      path: "/me",
      schema: z.object({ id: z.string() }),
    }).catch((e) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(401);
    expect(mockKeycloak.login).toHaveBeenCalledTimes(1);
    expect(fetchSpy).toHaveBeenCalledTimes(2);
  });
});
