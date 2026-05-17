import { Message } from "./message";

export const MESSAGE_REPOSITORY = Symbol("MESSAGE_REPOSITORY");

export interface MessagePage {
  content: Message[];
  /** ISO timestamp to feed back as `cursor` for the next page; null when exhausted. */
  nextCursor: string | null;
  hasMore: boolean;
}

export interface MessageRepository {
  /**
   * Cursor-paginated message list ordered by `sentAt DESC`. `cursor` is the
   * `sentAt` ISO string returned in the previous page's `nextCursor` — older
   * messages are returned next. Cursor pagination beats page-number here
   * because new messages keep arriving and would shift offset-based pages.
   */
  findByThread(
    threadId: string,
    cursor: string | null,
    limit: number,
  ): Promise<MessagePage>;
  save(message: Message): Promise<Message>;
}
