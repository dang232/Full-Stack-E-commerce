/**
 * Sanitizes a redirect URL to prevent open redirect attacks.
 * Only allows relative paths starting with "/" (but not "//").
 * Rejects absolute URLs, protocol-relative URLs, and other schemes.
 */
export function sanitizeRedirect(raw: string | null | undefined): string {
  if (!raw || typeof raw !== "string") return "/";
  const trimmed = raw.trim();
  // Only allow paths starting with single slash (not protocol-relative //)
  if (trimmed.startsWith("/") && !trimmed.startsWith("//")) return trimmed;
  return "/";
}
