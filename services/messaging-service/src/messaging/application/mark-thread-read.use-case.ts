import type { ThreadRepository } from "../domain/thread.repository";
import { THREAD_REPOSITORY } from "../domain/thread.repository";
import { Inject, Injectable, NotFoundException } from "@nestjs/common";

@Injectable()
export class MarkThreadReadUseCase {
  constructor(
    @Inject(THREAD_REPOSITORY) private readonly threads: ThreadRepository,
  ) {}

  async execute(userId: string, threadId: string): Promise<{ readAt: Date }> {
    const thread = await this.threads.findById(threadId);
    if (!thread) throw new NotFoundException("Thread not found");
    if (!thread.involves(userId))
      throw new NotFoundException("Thread not found");

    const now = new Date();
    if (thread.buyerId === userId) {
      thread.buyerLastReadAt = now;
    } else {
      thread.sellerLastReadAt = now;
    }
    await this.threads.save(thread);
    return { readAt: now };
  }
}
