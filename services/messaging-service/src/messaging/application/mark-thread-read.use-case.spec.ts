import { NotFoundException } from "@nestjs/common";
import { Thread } from "../domain/thread";
import { ThreadRepository } from "../domain/thread.repository";
import { MarkThreadReadUseCase } from "./mark-thread-read.use-case";

class FakeThreadRepo implements ThreadRepository {
  current: Thread | null = null;
  saved: Thread[] = [];
  findOrCreate(): Promise<Thread> {
    throw new Error("not used");
  }
  findExisting(): Promise<Thread | null> {
    return Promise.resolve(null);
  }
  findById(): Promise<Thread | null> {
    return Promise.resolve(this.current);
  }
  findForUser(): Promise<never> {
    throw new Error("not used");
  }
  save(thread: Thread): Promise<Thread> {
    this.saved.push(thread);
    return Promise.resolve(thread);
  }
}

const baseThread = () =>
  new Thread({
    id: "t1",
    buyerId: "alice",
    sellerId: "zoe",
    productId: null,
    lastMessageAt: new Date(),
    buyerLastReadAt: null,
    sellerLastReadAt: null,
    createdAt: new Date(),
  });

describe("MarkThreadReadUseCase", () => {
  it("records a buyerLastReadAt when the caller is the buyer", async () => {
    const repo = new FakeThreadRepo();
    repo.current = baseThread();
    const useCase = new MarkThreadReadUseCase(repo);

    const { readAt } = await useCase.execute("alice", "t1");

    expect(repo.saved[0].buyerLastReadAt).toEqual(readAt);
    expect(repo.saved[0].sellerLastReadAt).toBeNull();
  });

  it("records a sellerLastReadAt when the caller is the seller", async () => {
    const repo = new FakeThreadRepo();
    repo.current = baseThread();
    const useCase = new MarkThreadReadUseCase(repo);

    await useCase.execute("zoe", "t1");

    expect(repo.saved[0].sellerLastReadAt).not.toBeNull();
    expect(repo.saved[0].buyerLastReadAt).toBeNull();
  });

  it("refuses non-participants", async () => {
    const repo = new FakeThreadRepo();
    repo.current = baseThread();
    const useCase = new MarkThreadReadUseCase(repo);

    await expect(useCase.execute("mallory", "t1")).rejects.toThrow(
      NotFoundException,
    );
  });
});
