import { CartDomainException } from './cart-domain.exception';

export class CartItemLimitExceededException extends CartDomainException {
  constructor(productId: string, max: number, actual: number) {
    super(
      `Product ${productId} quantity ${actual} exceeds limit ${max}`,
      'CART_ITEM_LIMIT_EXCEEDED',
    );
  }
}
