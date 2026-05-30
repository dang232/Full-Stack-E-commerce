export class NotificationThread {
  private constructor(
    readonly threadId: string,
    readonly threadTitle: string,
  ) {}

  static create(threadId: string, threadTitle: string): NotificationThread {
    if (!threadId || !threadTitle) {
      throw new Error('threadId and threadTitle are required');
    }
    return new NotificationThread(threadId, threadTitle);
  }

  static reconstitute(threadId: string, threadTitle: string): NotificationThread {
    return new NotificationThread(threadId, threadTitle);
  }

  equals(other: NotificationThread): boolean {
    return this.threadId === other.threadId;
  }
}
