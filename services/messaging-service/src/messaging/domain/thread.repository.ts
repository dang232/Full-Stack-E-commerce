import { Thread } from "./thread";

export const THREAD_REPOSITORY = Symbol("THREAD_REPOSITORY");

export interface ThreadListItem {
  thread: Thread;
  lastMessageBody: string | null;
  lastMessageSenderId: string | null;
  unreadCount: number;
}

export interface ThreadRepository {
  /**
   * Returns the existing thread for `(buyerId, sellerId, productId)` or persists
   * a new one. The unique index on those three columns makes this idempotent
   * even under concurrent create requests — `findExisting` is consulted first
   * and on insert collision we re-fetch.
   */
  findOrCreate(thread: Thread): Promise<Thread>;
  findExisting(
    buyerId: string,
    sellerId: string,
    productId: string | null,
  ): Promise<Thread | null>;
  findById(id: string): Promise<Thread | null>;
  /**
   * Threads where the caller is buyer OR seller, ordered by `lastMessageAt DESC`.
   * Each item is enriched with the most recent message body + per-side unread
   * count so the FE thread list renders without an N+1 round-trip.
   */
  findForUser(userId: string, limit: number): Promise<ThreadListItem[]>;
  save(thread: Thread): Promise<Thread>;
}
