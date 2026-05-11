import { Cart } from '../domain/cart';
import { CartExpirationPolicy } from '../domain/cart-expiration-policy';
import { CartRepository } from '../domain/cart.repository';
import { CartResponse, toCartResponse } from './cart.response';

export interface UpdateCartItemCommand {
  userId: string;
  productId: string;
  quantity: number;
}

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
