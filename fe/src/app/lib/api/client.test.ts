import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { z } from "zod";

// Mock keycloak BEFORE importing the client.
const mockKeycloak = { authenticated: false, token: undefined as string | undefined, login: vi.fn() };
vi.mock("../auth/keycloak", () => ({
  getKeycloak: () => mockKeycloak,
  refreshToken: vi.fn().mockResolvedValue(true),
}));

import { api, request } from "./client";
import { ApiError } from "./envelope";

interface MockResponseInit {
  status?: number;
  body: unknown;
  headers?: Record<string, string>;
  bodyText?: string;
}

function mockResponse(init: MockResponseInit): Response {
  const status = init.status ?? 200;
  const text = init.bodyText ?? JSON.stringify(init.body);
  return new Response(text, {
    status,
    headers: { "content-type": "application/json", ...init.headers },
  });
}

const fetchSpy = vi.spyOn(global, "fetch");

beforeEach(() => {
  fetchSpy.mockReset();
  mockKeycloak.authenticated = false;
  mockKeycloak.token = undefined;
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("request", () => {
  const productSchema = z.object({ id: z.string(), name: z.string() });

  it("decodes a successful envelope and returns inner data", async () => {
    fetchSpy.mockResolvedValueOnce(
      mockResponse({
        body: {
          success: true,
          message: "ok",
          data: { id: "p1", name: "Tai nghe" },
          errorCode: null,
          timestamp: "2026-05-15T00:00:00Z",
        },
      }),
    );

    const result = await request({
      method: "GET",
      path: "/products/p1",
      schema: productSchema,
      auth: false,
    });

    expect(result).toEqual({ id: "p1", name: "Tai nghe" });
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const [url, init] = fetchSpy.mock.calls[0];
    expect(String(url)).toContain("/products/p1");
    expect(init?.method).toBe("GET");
    const headers = init?.headers as Record<string, string>;
    expect(headers["X-Correlation-Id"]).toMatch(/^[0-9a-f-]{36}$/);
    expect(headers.Authorization).toBeUndefined();
  });

  it("throws ApiError when envelope.success is false, capturing errorCode and correlation id", async () => {
    fetchSpy.mockResolvedValueOnce(
      mockResponse({
        body: {
          success: false,
          message: "Not found",
          data: null,
          errorCode: "PRODUCT_NOT_FOUND",
          timestamp: "2026-05-15T00:00:00Z",
        },
        headers: { "x-correlation-id": "cid-123" },
      }),
    );

    await expect(
      request({
        method: "GET",
        path: "/products/x",
        schema: z.null(),
        auth: false,
      }),
    ).rejects.toMatchObject({
      name: "ApiError",
      status: 200,
      errorCode: "PRODUCT_NOT_FOUND",
      message: "Not found",
      correlationId: "cid-123",
    });
  });

  it("throws ApiError on HTTP 5xx with the server message", async () => {
    fetchSpy.mockResolvedValueOnce(
      mockResponse({
        status: 503,
        body: { message: "downstream is down", errorCode: "SERVICE_UNAVAILABLE" },
      }),
    );

    const err = await request({
      method: "GET",
      path: "/orders",
      schema: z.array(z.unknown()),
      auth: false,
    }).catch((e) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(503);
    expect((err as ApiError).errorCode).toBe("SERVICE_UNAVAILABLE");
    expect((err as ApiError).message).toBe("downstream is down");
  });

  it("throws MALFORMED_RESPONSE when the envelope shape doesn't match", async () => {
    fetchSpy.mockResolvedValueOnce(
      mockResponse({
        body: { not: "an envelope" },
      }),
    );

    const err = await request({
      method: "GET",
      path: "/anything",
      schema: z.object({}),
      auth: false,
    }).catch((e) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).errorCode).toBe("MALFORMED_RESPONSE");
  });

  it("throws INVALID_JSON when the body isn't JSON", async () => {
    fetchSpy.mockResolvedValueOnce(
      new Response("<html>oops</html>", { status: 200, headers: { "content-type": "text/html" } }),
    );

    const err = await request({
      method: "GET",
      path: "/anything",
      schema: z.object({}),
      auth: false,
    }).catch((e) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).errorCode).toBe("INVALID_JSON");
  });

  it("attaches Authorization, Idempotency-Key, and JSON body for POST", async () => {
    mockKeycloak.authenticated = true;
    mockKeycloak.token = "jwt-abc";
    fetchSpy.mockResolvedValueOnce(
      mockResponse({
        body: {
          success: true,
          message: "ok",
          data: { id: "ord1" },
          errorCode: null,
          timestamp: "2026-05-15T00:00:00Z",
        },
      }),
    );

    const result = await api.post(
      "/orders",
      z.object({ id: z.string() }),
      { items: [{ productId: "p1", quantity: 2 }] },
      { idempotencyKey: "abc-key" },
    );

    expect(result).toEqual({ id: "ord1" });
    const [, init] = fetchSpy.mock.calls[0];
    const headers = init?.headers as Record<string, string>;
    expect(headers.Authorization).toBe("Bearer jwt-abc");
    expect(headers["Idempotency-Key"]).toBe("abc-key");
    expect(headers["Content-Type"]).toBe("application/json");
    expect(init?.body).toBe(JSON.stringify({ items: [{ productId: "p1", quantity: 2 }] }));
  });

  it("appends defined query params and skips undefined/null", async () => {
    fetchSpy.mockResolvedValueOnce(
      mockResponse({
        body: {
          success: true,
          message: "ok",
          data: [],
          errorCode: null,
          timestamp: "2026-05-15T00:00:00Z",
        },
      }),
    );

    await api.get("/products", z.array(z.unknown()), {
      q: "tai nghe",
      page: 2,
      brand: undefined,
      category: null,
    });

    const [url] = fetchSpy.mock.calls[0];
    const u = new URL(String(url));
    expect(u.searchParams.get("q")).toBe("tai nghe");
    expect(u.searchParams.get("page")).toBe("2");
    expect(u.searchParams.has("brand")).toBe(false);
    expect(u.searchParams.has("category")).toBe(false);
  });
});
