import { Logger } from '@nestjs/common';
import { Cart } from '../domain/cart';
import type { CartItem } from '../domain/cart-item';
import type { CartRepository } from '../domain/cart.repository';
import { toCartResponse } from './cart-response.mapper';
import type { CartResponse } from './cart.response';

export class MergeCartUseCase {
  private readonly logger = new Logger(MergeCartUseCase.name);

  constructor(private readonly cartRepo: CartRepository) {}

  async execute(userId: string, guestSessionId: string): Promise<CartResponse> {
    const guestKey = `guest:${guestSessionId}`;
    const [userCart, guestCart] = await Promise.all([
      this.cartRepo.findByUserId(userId),
      this.cartRepo.findByUserId(guestKey),
    ]);

    if (!guestCart || guestCart.items.length === 0) {
      return toCartResponse(userCart ?? Cart.create(userId));
    }

    const merged = userCart ?? Cart.create(userId);
    for (const guestItem of guestCart.items) {
      const existing = merged.items.find((i: CartItem) => i.itemKey === guestItem.itemKey);
      if (existing) {
        existing.updateQuantity(existing.quantity + guestItem.quantity);
      } else {
        merged.addItem(guestItem);
      }
    }

    await Promise.all([
      this.cartRepo.save(merged, 0),
      this.cartRepo.delete(guestKey),
    ]);
    this.logger.log(`Merged guest cart ${guestKey} into user ${userId}`);

    return toCartResponse(merged);
  }
}
