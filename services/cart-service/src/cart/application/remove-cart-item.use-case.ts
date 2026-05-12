import { Cart } from '../domain/cart';
import { CartExpirationPolicy } from '../domain/cart-expiration-policy';
import { CartRepository } from '../domain/cart.repository';
import type { RemoveCartItemCommand } from './remove-cart-item.command';
import { toCartResponse } from './cart-response.mapper';
import type { CartResponse } from './cart.response';

export class RemoveCartItemUseCase {
  constructor(private readonly cartRepository: CartRepository) {}

  async execute(command: RemoveCartItemCommand): Promise<CartResponse> {
    const cart =
      (await this.cartRepository.findByUserId(command.userId)) ??
      Cart.create(command.userId);

    cart.removeItem(command.productId);
    await this.cartRepository.save(cart, CartExpirationPolicy.TTL_SECONDS);

    return toCartResponse(cart);
  }
}
