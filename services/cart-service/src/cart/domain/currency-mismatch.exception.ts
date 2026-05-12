import { CartDomainException } from './cart-domain.exception';

export class CurrencyMismatchException extends CartDomainException {
  constructor(currency: string, otherCurrency: string) {
    super(
      `Currency mismatch: ${currency} does not match ${otherCurrency}`,
      'CURRENCY_MISMATCH',
    );
  }
}
