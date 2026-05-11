export abstract class CartDomainException extends Error {
  protected constructor(message: string, public readonly errorCode: string) {
    super(message);
    this.name = new.target.name;
  }
}

export class CartFullException extends CartDomainException {
  constructor(maxItems: number) {
    super(
      `Cart cannot contain more than ${maxItems} unique items`,
      'CART_FULL',
    );
  }
}

export class CartItemLimitExceededException extends CartDomainException {
  constructor(productId: string, max: number, actual: number) {
    super(
      `Product ${productId} quantity ${actual} exceeds limit ${max}`,
      'CART_ITEM_LIMIT_EXCEEDED',
    );
  }
}

export class CartItemNotFoundException extends CartDomainException {
  constructor(productId: string) {
    super(`Cart item ${productId} not found`, 'CART_ITEM_NOT_FOUND');
  }
}

export class InvalidCartOperationException extends CartDomainException {
  constructor(message: string) {
    super(message, 'INVALID_CART_OPERATION');
  }
}

export class CurrencyMismatchException extends CartDomainException {
  constructor(currency: string, otherCurrency: string) {
    super(
      `Currency mismatch: ${currency} does not match ${otherCurrency}`,
      'CURRENCY_MISMATCH',
    );
  }
}

export class ProductNotFoundException extends CartDomainException {
  constructor(productId: string) {
    super(`Product ${productId} not found`, 'PRODUCT_NOT_FOUND');
  }
}
