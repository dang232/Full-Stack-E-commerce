import { CartDomainException } from './cart-domain.exception';

export class InvalidCartOperationException extends CartDomainException {
  constructor(message: string) {
    super(message, 'INVALID_CART_OPERATION');
  }
}
