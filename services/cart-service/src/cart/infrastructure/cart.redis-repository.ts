import Redis from 'ioredis';
import { Cart } from '../domain/cart';
import { CartItem } from '../domain/cart-item';
import { CartRepository } from '../domain/cart.repository';
import { Money } from '../domain/money';

interface PersistedMoney {
  amount: number;
  currency: string;
}

interface PersistedCartItem {
  productId: string;
  productName: string;
  productImage: string;
  unitPrice: PersistedMoney;
  quantity: number;
  addedAt: string;
}

interface PersistedCart {
  userId: string;
  items: PersistedCartItem[];
  updatedAt: string;
}

export class CartRedisRepository implements CartRepository {
  constructor(private readonly redis: Redis) {}

  async findByUserId(userId: string): Promise<Cart | null> {
    const value = await this.redis.get(this.key(userId));

    if (!value) {
      return null;
    }

    const persisted = JSON.parse(value) as PersistedCart;
    const items = persisted.items.map((item) =>
      CartItem.fromPersistence(
        item.productId,
        item.productName,
        item.productImage,
        Money.of(item.unitPrice.amount, item.unitPrice.currency),
        item.quantity,
        new Date(item.addedAt),
      ),
    );

    return Cart.fromPersistence(
      persisted.userId,
      items,
      new Date(persisted.updatedAt),
    );
  }

  async save(cart: Cart, ttlSeconds: number): Promise<void> {
    await this.redis.setex(
      this.key(cart.userId),
      ttlSeconds,
      JSON.stringify(this.toPersistence(cart)),
    );
  }

  async delete(userId: string): Promise<void> {
    await this.redis.del(this.key(userId));
  }

  private key(userId: string): string {
    return `cart:${userId}`;
  }

  private toPersistence(cart: Cart): PersistedCart {
    return {
      userId: cart.userId,
      items: cart.items.map((item) => ({
        productId: item.productId,
        productName: item.productName,
        productImage: item.productImage,
        unitPrice: {
          amount: item.unitPrice.amount,
          currency: item.unitPrice.currency,
        },
        quantity: item.quantity,
        addedAt: item.addedAt.toISOString(),
      })),
      updatedAt: cart.updatedAt.toISOString(),
    };
  }
}
