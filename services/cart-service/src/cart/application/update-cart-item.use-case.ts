import { Cart } from '../domain/cart';
import { CartExpirationPolicy } from '../domain/cart-expiration-policy';
import { CartRepository } from '../domain/cart.repository';
import type { UpdateCartItemCommand } from './update-cart-item.command';
import { toCartResponse } from './cart-response.mapper';
import type { CartResponse } from './cart.response';

export class UpdateCartItemUseCase {
  constructor(private readonly cartRepository: CartRepository) {}

  async execute(command: UpdateCartItemCommand): Promise<CartResponse> {
    const cart =
      (await this.cartRepository.findByUserId(command.userId)) ??
      Cart.create(command.userId);

    cart.updateItemQuantity(command.productId, command.quantity);
    await this.cartRepository.save(cart, CartExpirationPolicy.TTL_SECONDS);

    return toCartResponse(cart);
  }
}
