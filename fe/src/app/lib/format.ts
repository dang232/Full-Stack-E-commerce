/**
 * Shared formatting helpers. Extracted from `components/vnshop-data.ts` so production
 * code does not import from a mock-data module.
 */

const VND_FORMATTER = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
});

export function formatPrice(price: number): string {
  return VND_FORMATTER.format(price);
}

const DATE_FORMATTER = new Intl.DateTimeFormat("vi-VN", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
});

export function formatDate(value: string | number | Date | null | undefined): string {
  if (value === null || value === undefined || value === "") return "";
  const d = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(d.getTime())) return "";
  return DATE_FORMATTER.format(d);
}
