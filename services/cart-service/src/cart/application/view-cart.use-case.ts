import { Cart } from '../domain/cart';
import { CartRepository } from '../domain/cart.repository';
import { toCartResponse } from './cart-response.mapper';
import type { CartResponse } from './cart.response';

export class ViewCartUseCase {
  constructor(private readonly cartRepository: CartRepository) {}

  async execute(userId: string): Promise<CartResponse> {
    const cart =
      (await this.cartRepository.findByUserId(userId)) ?? Cart.create(userId);
    return toCartResponse(cart);
  }
}
