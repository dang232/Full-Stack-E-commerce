import { describe, expect, it } from "vitest";

import { adminPayoutSchema } from "./admin";

describe("adminPayoutSchema", () => {
  it("parses a PENDING row from the live BE shape", () => {
    const parsed = adminPayoutSchema.parse({
      payoutId: "p-1",
      sellerId: "s-1",
      amount: 125.5,
      status: "PENDING",
      createdAt: "2026-05-14T00:00:00Z",
      completedBy: null,
      completedAt: null,
    });
    expect(parsed.id).toBe("p-1");
    expect(parsed.requestedAt).toBe("2026-05-14T00:00:00Z");
    expect(parsed.completedBy).toBeUndefined();
    expect(parsed.completedAt).toBeUndefined();
  });

  it("surfaces completedBy and completedAt on a COMPLETED row", () => {
    const parsed = adminPayoutSchema.parse({
      payoutId: "p-2",
      sellerId: "s-1",
      amount: 80,
      status: "COMPLETED",
      createdAt: "2026-05-14T00:00:00Z",
      completedBy: "admin-42",
      completedAt: "2026-05-24T08:30:00Z",
    });
    expect(parsed.id).toBe("p-2");
    expect(parsed.completedBy).toBe("admin-42");
    expect(parsed.completedAt).toBe("2026-05-24T08:30:00Z");
  });

  it("accepts a legacy COMPLETED row with no audit fields", () => {
    // Rows that predate the V5 migration have no captured admin id —
    // historical history must still parse so the Completed tab can
    // render them (the UI shows an unknown-admin label in that case).
    const parsed = adminPayoutSchema.parse({
      id: "p-old",
      sellerId: "s-1",
      amount: 50,
      status: "COMPLETED",
      requestedAt: "2026-05-10T00:00:00Z",
    });
    expect(parsed.id).toBe("p-old");
    expect(parsed.completedBy).toBeUndefined();
    expect(parsed.completedAt).toBeUndefined();
  });
});
