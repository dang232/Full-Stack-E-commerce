import { NotFoundException } from "@nestjs/common";
import { Message } from "../domain/message";
import { MessagePage, MessageRepository } from "../domain/message.repository";
import { Thread } from "../domain/thread";
import { ThreadRepository } from "../domain/thread.repository";
import { IdempotencyStore } from "../infrastructure/idempotency-store";
import { MessagePublisher, PublishMessageInput } from "./message-publisher";
import { SendMessageUseCase } from "./send-message.use-case";

class FakeThreadRepository implements ThreadRepository {
  threads = new Map<string, Thread>();
  saveCalls: Thread[] = [];

  findOrCreate(thread: Thread): Promise<Thread> {
    this.threads.set(thread.id, thread);
    return Promise.resolve(thread);
  }
  findExisting(): Promise<Thread | null> {
    return Promise.resolve(null);
  }
  findById(id: string): Promise<Thread | null> {
    return Promise.resolve(this.threads.get(id) ?? null);
  }
  findForUser(): Promise<never> {
    throw new Error("not used");
  }
  save(thread: Thread): Promise<Thread> {
    this.saveCalls.push(thread);
    return Promise.resolve(thread);
  }
}

class FakeMessageRepository implements MessageRepository {
  saved: Message[] = [];

  findByThread(): Promise<MessagePage> {
    throw new Error("not used");
  }
  save(message: Message): Promise<Message> {
    this.saved.push(message);
    return Promise.resolve(message);
  }
}

class CapturingPublisher implements MessagePublisher {
  events: PublishMessageInput[] = [];
  publish(event: PublishMessageInput): Promise<void> {
    this.events.push(event);
    return Promise.resolve();
  }
}

const seedThread = (): Thread =>
  new Thread({
    id: "thread-1",
    buyerId: "alice",
    sellerId: "zoe",
    productId: null,
    lastMessageAt: new Date("2026-01-01T00:00:00Z"),
    buyerLastReadAt: null,
    sellerLastReadAt: null,
    createdAt: new Date("2026-01-01T00:00:00Z"),
  });

describe("SendMessageUseCase", () => {
  it("persists, bumps thread.lastMessageAt, and publishes to the recipient", async () => {
    const threads = new FakeThreadRepository();
    const messages = new FakeMessageRepository();
    const publisher = new CapturingPublisher();
    const idem = new IdempotencyStore();
    threads.threads.set("thread-1", seedThread());

    const useCase = new SendMessageUseCase(threads, messages, publisher, idem);

    const result = await useCase.execute({
      callerId: "alice",
      threadId: "thread-1",
      body: "  hello  ",
    });

    expect(result.body).toBe("hello");
    expect(messages.saved).toHaveLength(1);
    expect(threads.saveCalls).toHaveLength(1);
    expect(threads.saveCalls[0].lastMessageAt).toBe(result.sentAt);
    expect(publisher.events).toHaveLength(1);
    expect(publisher.events[0].recipientId).toBe("zoe");
  });

  it("returns the cached message on a repeated idempotency key without writing again", async () => {
    const threads = new FakeThreadRepository();
    const messages = new FakeMessageRepository();
    const publisher = new CapturingPublisher();
    const idem = new IdempotencyStore();
    threads.threads.set("thread-1", seedThread());

    const useCase = new SendMessageUseCase(threads, messages, publisher, idem);
    const first = await useCase.execute({
      callerId: "alice",
      threadId: "thread-1",
      body: "hi",
      idempotencyKey: "k1",
    });
    const second = await useCase.execute({
      callerId: "alice",
      threadId: "thread-1",
      body: "hi-different-body-but-cached",
      idempotencyKey: "k1",
    });

    expect(second.id).toBe(first.id);
    expect(messages.saved).toHaveLength(1);
    expect(publisher.events).toHaveLength(1);
  });

  it("rejects empty bodies and bodies that are too long", async () => {
    const threads = new FakeThreadRepository();
    threads.threads.set("thread-1", seedThread());
    const useCase = new SendMessageUseCase(
      threads,
      new FakeMessageRepository(),
      new CapturingPublisher(),
      new IdempotencyStore(),
    );

    await expect(
      useCase.execute({ callerId: "alice", threadId: "thread-1", body: "   " }),
    ).rejects.toThrow();
    await expect(
      useCase.execute({
        callerId: "alice",
        threadId: "thread-1",
        body: "x".repeat(5000),
      }),
    ).rejects.toThrow();
  });

  it("returns 404 when the caller is not a participant", async () => {
    const threads = new FakeThreadRepository();
    threads.threads.set("thread-1", seedThread());
    const useCase = new SendMessageUseCase(
      threads,
      new FakeMessageRepository(),
      new CapturingPublisher(),
      new IdempotencyStore(),
    );

    await expect(
      useCase.execute({
        callerId: "mallory",
        threadId: "thread-1",
        body: "hi",
      }),
    ).rejects.toThrow(NotFoundException);
  });

  it("returns 404 when the thread does not exist", async () => {
    const useCase = new SendMessageUseCase(
      new FakeThreadRepository(),
      new FakeMessageRepository(),
      new CapturingPublisher(),
      new IdempotencyStore(),
    );

    await expect(
      useCase.execute({ callerId: "alice", threadId: "missing", body: "hi" }),
    ).rejects.toThrow(NotFoundException);
  });
});
