import { CartDomainException } from './cart-domain.exception';

export class CartFullException extends CartDomainException {
  constructor(maxItems: number) {
    super(
      `Cart cannot contain more than ${maxItems} unique items`,
      'CART_FULL',
    );
  }
}
