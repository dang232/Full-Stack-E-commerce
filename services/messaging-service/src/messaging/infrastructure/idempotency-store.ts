import { Injectable } from "@nestjs/common";

/**
 * Tiny in-memory idempotency store for `POST /messaging/threads/:id/messages`.
 * Mirrors the pattern from payment-service's three-layer idempotency, but at
 * the lightest tier (per-pod, in-process) since messages are append-only and a
 * dedupe miss across pods only causes a duplicate row, not double-charging.
 *
 * The orchestrator in send-message stuffs the persisted result here keyed by
 * `(userId, idempotencyKey)`. Subsequent identical POSTs return the cached
 * payload without writing a new row.
 *
 * Entries auto-expire after 10 minutes — long enough for retry storms, short
 * enough that the table stays small. We sweep on every put.
 */
const TTL_MS = 10 * 60_000;

interface Entry<T> {
  value: T;
  expiresAt: number;
}

@Injectable()
export class IdempotencyStore {
  private readonly entries = new Map<string, Entry<unknown>>();

  get<T>(userId: string, key: string): T | undefined {
    const composite = `${userId}::${key}`;
    const entry = this.entries.get(composite);
    if (!entry) return undefined;
    if (entry.expiresAt < Date.now()) {
      this.entries.delete(composite);
      return undefined;
    }
    return entry.value as T;
  }

  put<T>(userId: string, key: string, value: T): void {
    const composite = `${userId}::${key}`;
    this.entries.set(composite, { value, expiresAt: Date.now() + TTL_MS });
    this.sweep();
  }

  private sweep(): void {
    if (this.entries.size < 256) return;
    const now = Date.now();
    for (const [k, v] of this.entries.entries()) {
      if (v.expiresAt < now) this.entries.delete(k);
    }
  }
}
