import type { Address } from "../../types/api";

export function formatAddressLine(a: Address): string {
  return [a.line1, a.ward, a.district, a.city, a.province].filter(Boolean).join(", ");
}
