import { describe, expect, it } from "vitest";

import { addressSchema, userProfileSchema } from "../../../types/api";

/**
 * Regression coverage for the addAddress / removeAddress / setDefaultAddress
 * shape mismatch surfaced by the Wave 1 audit. The BE returns the full
 * BuyerProfileResponse (see UserController#addAddress), and the FE schema
 * must accept that shape — not a bare array of addresses.
 *
 * The schemas live one layer above this test (in {@code types/api.ts}) but
 * are exercised here because they are what {@code endpoints/users.ts} uses
 * to validate every address mutation response.
 */
describe("users endpoint Zod schemas", () => {
  const buyerProfileResponse = {
    id: "kc-123",
    keycloakId: "kc-123",
    email: "buyer@example.com",
    name: "Nguyễn Văn A",
    phone: "0901234567",
    avatar: "",
    avatarUrl: "",
    addresses: [
      {
        line1: "12 Lê Lợi",
        ward: "Bến Nghé",
        district: "Quận 1",
        city: "Hồ Chí Minh",
        country: "VN",
        isDefault: true,
      },
    ],
    role: "BUYER",
  };

  it("userProfileSchema parses a BuyerProfileResponse-shaped payload", () => {
    const parsed = userProfileSchema.parse(buyerProfileResponse);
    expect(parsed.id).toBe("kc-123");
    expect(parsed.addresses).toHaveLength(1);
    expect(parsed.addresses?.[0].city).toBe("Hồ Chí Minh");
  });

  it("userProfileSchema rejects a bare address array — addAddress must return the full profile", () => {
    // The legacy bug used z.array(addressSchema) here; this assertion locks in
    // the invariant that the address mutation response is the full profile.
    const bareArray = [
      {
        line1: "12 Lê Lợi",
        city: "Hồ Chí Minh",
        country: "VN",
      },
    ];
    expect(() => userProfileSchema.parse(bareArray)).toThrow();
  });

  it("userProfileSchema tolerates BE-side extras through .loose()", () => {
    const parsed = userProfileSchema.parse({
      ...buyerProfileResponse,
      // Hypothetical future fields — must not break parsing.
      loyaltyTier: "GOLD",
      createdAt: "2026-05-17T00:00:00Z",
    });
    expect(parsed.id).toBe("kc-123");
  });

  it("addressSchema accepts the AddressResponse shape with optional fields", () => {
    const parsed = addressSchema.parse({
      line1: "12 Lê Lợi",
      city: "Hồ Chí Minh",
      country: "VN",
    });
    expect(parsed.line1).toBe("12 Lê Lợi");
    expect(parsed.country).toBe("VN");
  });

  it("addressSchema requires line1 and city", () => {
    expect(() => addressSchema.parse({ city: "HCM" })).toThrow();
    expect(() => addressSchema.parse({ line1: "12 Lê Lợi" })).toThrow();
  });
});
