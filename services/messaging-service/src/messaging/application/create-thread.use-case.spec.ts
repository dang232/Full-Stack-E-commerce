import { CreateThreadUseCase } from "./create-thread.use-case";
import { Thread } from "../domain/thread";
import { ThreadRepository } from "../domain/thread.repository";

class FakeThreadRepository implements ThreadRepository {
  store = new Map<string, Thread>();

  findOrCreate(thread: Thread): Promise<Thread> {
    const key = this.key(thread.buyerId, thread.sellerId, thread.productId);
    const existing = this.store.get(key);
    if (existing) return Promise.resolve(existing);
    this.store.set(key, thread);
    return Promise.resolve(thread);
  }

  findExisting(
    buyerId: string,
    sellerId: string,
    productId: string | null,
  ): Promise<Thread | null> {
    return Promise.resolve(
      this.store.get(this.key(buyerId, sellerId, productId)) ?? null,
    );
  }

  findById(id: string): Promise<Thread | null> {
    for (const thread of this.store.values()) {
      if (thread.id === id) return Promise.resolve(thread);
    }
    return Promise.resolve(null);
  }

  findForUser(): Promise<never> {
    throw new Error("not used in this test");
  }

  save(thread: Thread): Promise<Thread> {
    return Promise.resolve(thread);
  }

  private key(buyer: string, seller: string, product: string | null): string {
    return `${buyer}|${seller}|${product ?? ""}`;
  }
}

describe("CreateThreadUseCase", () => {
  it("orders participants deterministically so opening from either side resolves to the same thread", async () => {
    const repo = new FakeThreadRepository();
    const useCase = new CreateThreadUseCase(repo);

    const fromBuyer = await useCase.execute({
      callerId: "alice",
      recipientId: "zoe",
    });
    const fromSeller = await useCase.execute({
      callerId: "zoe",
      recipientId: "alice",
    });

    expect(fromBuyer.buyerId).toBe("alice");
    expect(fromBuyer.sellerId).toBe("zoe");
    expect(fromSeller.id).toBe(fromBuyer.id);
  });

  it("treats different products as separate threads between the same pair", async () => {
    const repo = new FakeThreadRepository();
    const useCase = new CreateThreadUseCase(repo);

    const a = await useCase.execute({
      callerId: "alice",
      recipientId: "zoe",
      productId: "P1",
    });
    const b = await useCase.execute({
      callerId: "alice",
      recipientId: "zoe",
      productId: "P2",
    });

    expect(a.id).not.toBe(b.id);
  });

  it("coalesces undefined and empty productId into the general (null-product) thread", async () => {
    const repo = new FakeThreadRepository();
    const useCase = new CreateThreadUseCase(repo);

    const general = await useCase.execute({
      callerId: "alice",
      recipientId: "zoe",
    });
    const alsoGeneral = await useCase.execute({
      callerId: "alice",
      recipientId: "zoe",
      productId: null,
    });

    expect(alsoGeneral.id).toBe(general.id);
  });

  it("rejects threads with the same caller and recipient", async () => {
    const repo = new FakeThreadRepository();
    const useCase = new CreateThreadUseCase(repo);

    await expect(
      useCase.execute({ callerId: "alice", recipientId: "alice" }),
    ).rejects.toThrow();
  });
});
