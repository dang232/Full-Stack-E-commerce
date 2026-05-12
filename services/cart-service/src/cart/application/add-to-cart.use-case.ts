import { Cart } from '../domain/cart';
import { CartItem } from '../domain/cart-item';
import { CartExpirationPolicy } from '../domain/cart-expiration-policy';
import { CartRepository } from '../domain/cart.repository';
import type { AddToCartCommand } from './add-to-cart.command';
import type { ProductClientPort } from './product-client.port';
import { toCartResponse } from './cart-response.mapper';
import type { CartResponse } from './cart.response';

export class AddToCartUseCase {
  constructor(
    private readonly cartRepository: CartRepository,
    private readonly productClient: ProductClientPort,
  ) {}

  async execute(command: AddToCartCommand): Promise<CartResponse> {
    const cart =
      (await this.cartRepository.findByUserId(command.userId)) ??
      Cart.create(command.userId);
    const snapshot = await this.productClient.getSnapshot(command.productId);
    const item = CartItem.create(
      snapshot.productId,
      snapshot.productName,
      snapshot.productImage,
      snapshot.unitPrice,
      command.quantity,
    );

    cart.addItem(item);
    await this.cartRepository.save(cart, CartExpirationPolicy.TTL_SECONDS);

    return toCartResponse(cart);
  }
}
