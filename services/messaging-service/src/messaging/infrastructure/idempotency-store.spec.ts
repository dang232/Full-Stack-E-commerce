import { IdempotencyStore } from "./idempotency-store";

describe("IdempotencyStore", () => {
  it("stores values keyed by (userId, key)", () => {
    const store = new IdempotencyStore();
    store.put("alice", "k1", { id: "m1" });
    expect(store.get<{ id: string }>("alice", "k1")).toEqual({ id: "m1" });
  });

  it("isolates entries by user", () => {
    const store = new IdempotencyStore();
    store.put("alice", "k1", { id: "m1" });
    expect(store.get("zoe", "k1")).toBeUndefined();
  });

  it("returns undefined when nothing is stored", () => {
    const store = new IdempotencyStore();
    expect(store.get("alice", "whatever")).toBeUndefined();
  });
});
