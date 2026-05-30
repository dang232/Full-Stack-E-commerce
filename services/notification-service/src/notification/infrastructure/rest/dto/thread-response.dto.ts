import { ThreadSummary } from '../../../domain/port/outbound/notification.repository';

export class ThreadResponseDto {
  readonly threadId: string;
  readonly threadTitle: string;
  readonly unreadCount: number;
  readonly totalCount: number;
  readonly updatedAt: string;

  private constructor(props: ThreadResponseDto) {
    Object.assign(this, props);
  }

  static from(summary: ThreadSummary): ThreadResponseDto {
    return new ThreadResponseDto({
      threadId: summary.threadId,
      threadTitle: summary.threadTitle,
      unreadCount: summary.unreadCount,
      totalCount: summary.totalCount,
      updatedAt: summary.latestAt.toISOString(),
    });
  }
}
