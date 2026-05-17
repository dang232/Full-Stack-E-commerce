import type { MessageRepository } from "../domain/message.repository";
import { MESSAGE_REPOSITORY } from "../domain/message.repository";
import type { ThreadRepository } from "../domain/thread.repository";
import { THREAD_REPOSITORY } from "../domain/thread.repository";
import { IdempotencyStore } from "../infrastructure/idempotency-store";
import type { MessagePublisher } from "./message-publisher";
import { MESSAGE_PUBLISHER } from "./message-publisher";
import { Inject, Injectable, NotFoundException } from "@nestjs/common";
import { Message } from "../domain/message";

export interface SendMessageInput {
  callerId: string;
  threadId: string;
  body: string;
  idempotencyKey?: string | null;
}

const MAX_BODY_LEN = 4000;

@Injectable()
export class SendMessageUseCase {
  constructor(
    @Inject(THREAD_REPOSITORY) private readonly threads: ThreadRepository,
    @Inject(MESSAGE_REPOSITORY) private readonly messages: MessageRepository,
    @Inject(MESSAGE_PUBLISHER) private readonly publisher: MessagePublisher,
    private readonly idempotency: IdempotencyStore,
  ) {}

  async execute(input: SendMessageInput): Promise<Message> {
    if (input.idempotencyKey) {
      const cached = this.idempotency.get<Message>(
        input.callerId,
        input.idempotencyKey,
      );
      if (cached) return cached;
    }

    const trimmed = input.body.trim();
    if (trimmed.length === 0) throw new Error("Message body cannot be empty");
    if (trimmed.length > MAX_BODY_LEN) {
      throw new Error(`Message body exceeds ${MAX_BODY_LEN} characters`);
    }

    const thread = await this.threads.findById(input.threadId);
    if (!thread) throw new NotFoundException("Thread not found");
    if (!thread.involves(input.callerId)) {
      throw new NotFoundException("Thread not found");
    }

    const message = Message.create({
      threadId: thread.id,
      senderId: input.callerId,
      body: trimmed,
    });

    const saved = await this.messages.save(message);
    thread.lastMessageAt = saved.sentAt;
    await this.threads.save(thread);

    // Fan-out via Kafka so subscribed WebSocket pods can push to the recipient.
    await this.publisher.publish({
      threadId: thread.id,
      message: saved,
      buyerId: thread.buyerId,
      sellerId: thread.sellerId,
      recipientId: thread.otherParty(input.callerId),
    });

    if (input.idempotencyKey) {
      this.idempotency.put(input.callerId, input.idempotencyKey, saved);
    }
    return saved;
  }
}
