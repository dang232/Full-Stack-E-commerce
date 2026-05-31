import type {
  ThreadListItem,
  ThreadRepository,
} from "../domain/thread.repository";
import { THREAD_REPOSITORY } from "../domain/thread.repository";
import { Inject, Injectable } from "@nestjs/common";

@Injectable()
export class ListThreadsUseCase {
  constructor(
    @Inject(THREAD_REPOSITORY) private readonly threads: ThreadRepository,
  ) {}

  async execute(userId: string, limit = 50): Promise<ThreadListItem[]> {
    const safe =
      Number.isFinite(limit) && limit > 0
        ? Math.min(Math.floor(limit), 100)
        : 50;
    return this.threads.findForUser(userId, safe);
  }
}
