import { CartDomainException } from './cart-domain.exception';

export class ProductNotFoundException extends CartDomainException {
  constructor(productId: string) {
    super(`Product ${productId} not found`, 'PRODUCT_NOT_FOUND');
  }
}
