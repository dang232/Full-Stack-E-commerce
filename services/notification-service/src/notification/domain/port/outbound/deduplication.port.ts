export interface DeduplicationPort {
  isDuplicate(idempotencyKey: string): Promise<boolean>;
  markProcessed(idempotencyKey: string, ttlSeconds?: number): Promise<void>;
  /** Atomically acquire the key. Returns true if acquired (first caller wins), false if already exists. */
  tryAcquire(idempotencyKey: string, ttlSeconds?: number): Promise<boolean>;
}

export const DEDUPLICATION_PORT = Symbol('DEDUPLICATION_PORT');
