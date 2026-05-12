import { CartDomainException } from './cart-domain.exception';

export class CartItemNotFoundException extends CartDomainException {
  constructor(productId: string) {
    super(`Cart item ${productId} not found`, 'CART_ITEM_NOT_FOUND');
  }
}
