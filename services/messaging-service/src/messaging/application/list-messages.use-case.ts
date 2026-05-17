import type {
  MessagePage,
  MessageRepository,
} from "../domain/message.repository";
import { MESSAGE_REPOSITORY } from "../domain/message.repository";
import type { ThreadRepository } from "../domain/thread.repository";
import { THREAD_REPOSITORY } from "../domain/thread.repository";
import {
  ForbiddenException,
  Inject,
  Injectable,
  NotFoundException,
} from "@nestjs/common";

@Injectable()
export class ListMessagesUseCase {
  constructor(
    @Inject(THREAD_REPOSITORY) private readonly threads: ThreadRepository,
    @Inject(MESSAGE_REPOSITORY) private readonly messages: MessageRepository,
  ) {}

  async execute(
    userId: string,
    threadId: string,
    cursor: string | null,
    limit: number,
  ): Promise<MessagePage> {
    const thread = await this.threads.findById(threadId);
    if (!thread) throw new NotFoundException("Thread not found");
    if (!thread.involves(userId)) {
      // 404 instead of 403 so we don't leak thread existence to non-participants.
      throw new NotFoundException("Thread not found");
    }
    void ForbiddenException; // keep import for future role checks
    return this.messages.findByThread(threadId, cursor, limit);
  }
}
