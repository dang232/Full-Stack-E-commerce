import { NotFoundException } from "@nestjs/common";
import { Message } from "../domain/message";
import { MessageRepository } from "../domain/message.repository";
import { Thread } from "../domain/thread";
import { ThreadRepository } from "../domain/thread.repository";
import { ListMessagesUseCase } from "./list-messages.use-case";

class FakeThreadRepo implements ThreadRepository {
  thread: Thread | null = null;
  findOrCreate(): Promise<Thread> {
    throw new Error("not used");
  }
  findExisting(): Promise<Thread | null> {
    return Promise.resolve(null);
  }
  findById(): Promise<Thread | null> {
    return Promise.resolve(this.thread);
  }
  findForUser(): Promise<never> {
    throw new Error("not used");
  }
  save(thread: Thread): Promise<Thread> {
    return Promise.resolve(thread);
  }
}

class FakeMessageRepo implements MessageRepository {
  page = {
    content: [
      new Message({
        id: "m1",
        threadId: "t1",
        senderId: "alice",
        body: "hi",
        sentAt: new Date(),
      }),
    ],
    nextCursor: null as string | null,
    hasMore: false,
  };
  findByThread = jest.fn(() => Promise.resolve(this.page));
  save(message: Message): Promise<Message> {
    return Promise.resolve(message);
  }
}

const buildThread = (buyer: string, seller: string): Thread =>
  new Thread({
    id: "t1",
    buyerId: buyer,
    sellerId: seller,
    productId: null,
    lastMessageAt: new Date(),
    buyerLastReadAt: null,
    sellerLastReadAt: null,
    createdAt: new Date(),
  });

describe("ListMessagesUseCase", () => {
  it("returns the page when the caller is a participant", async () => {
    const threads = new FakeThreadRepo();
    threads.thread = buildThread("alice", "zoe");
    const messages = new FakeMessageRepo();
    const useCase = new ListMessagesUseCase(threads, messages);

    const page = await useCase.execute("alice", "t1", null, 30);
    expect(page.content).toHaveLength(1);
    expect(messages.findByThread).toHaveBeenCalledWith("t1", null, 30);
  });

  it("throws 404 (not 403) when the caller is not a participant — to avoid leaking thread existence", async () => {
    const threads = new FakeThreadRepo();
    threads.thread = buildThread("alice", "zoe");
    const useCase = new ListMessagesUseCase(threads, new FakeMessageRepo());

    await expect(useCase.execute("mallory", "t1", null, 30)).rejects.toThrow(
      NotFoundException,
    );
  });

  it("throws 404 when the thread does not exist", async () => {
    const useCase = new ListMessagesUseCase(
      new FakeThreadRepo(),
      new FakeMessageRepo(),
    );
    await expect(useCase.execute("alice", "missing", null, 30)).rejects.toThrow(
      NotFoundException,
    );
  });
});
