import { randomUUID } from "node:crypto";

export interface ThreadProperties {
  id: string;
  buyerId: string;
  sellerId: string;
  productId: string | null;
  lastMessageAt: Date;
  buyerLastReadAt: Date | null;
  sellerLastReadAt: Date | null;
  createdAt: Date;
}

/**
 * A direct buyer-seller conversation. Optionally scoped to a product so opening
 * Chat from different product pages keeps conversations distinct (Shopee/Lazada
 * pattern). The same `(buyer, seller, product)` triple always resolves to the
 * same thread; `findOrCreate` enforces this at the repository layer.
 */
export class Thread {
  public readonly id: string;
  public readonly buyerId: string;
  public readonly sellerId: string;
  public readonly productId: string | null;
  public lastMessageAt: Date;
  public buyerLastReadAt: Date | null;
  public sellerLastReadAt: Date | null;
  public readonly createdAt: Date;

  constructor(properties: ThreadProperties) {
    this.id = properties.id;
    this.buyerId = properties.buyerId;
    this.sellerId = properties.sellerId;
    this.productId = properties.productId;
    this.lastMessageAt = properties.lastMessageAt;
    this.buyerLastReadAt = properties.buyerLastReadAt;
    this.sellerLastReadAt = properties.sellerLastReadAt;
    this.createdAt = properties.createdAt;
  }

  static create(
    properties: Omit<
      ThreadProperties,
      | "id"
      | "createdAt"
      | "lastMessageAt"
      | "buyerLastReadAt"
      | "sellerLastReadAt"
    > & {
      now?: Date;
    },
  ): Thread {
    const now = properties.now ?? new Date();
    return new Thread({
      id: randomUUID(),
      buyerId: properties.buyerId,
      sellerId: properties.sellerId,
      productId: properties.productId,
      lastMessageAt: now,
      buyerLastReadAt: null,
      sellerLastReadAt: null,
      createdAt: now,
    });
  }

  /** Returns true when the caller is a participant in this thread. */
  involves(userId: string): boolean {
    return this.buyerId === userId || this.sellerId === userId;
  }

  /** Returns the other participant from the caller's perspective. */
  otherParty(userId: string): string {
    return this.buyerId === userId ? this.sellerId : this.buyerId;
  }
}
