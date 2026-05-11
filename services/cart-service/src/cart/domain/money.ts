import {
  CurrencyMismatchException,
  InvalidCartOperationException,
} from './cart.exceptions';

export class Money {
  private constructor(
    private readonly _amount: number,
    private readonly _currency: string,
  ) {}

  static of(amount: number, currency = 'VND'): Money {
    if (amount < 0) {
      throw new InvalidCartOperationException(
        'Amount must be greater than or equal to 0',
      );
    }

    return new Money(Math.round(amount), currency);
  }

  static zero(currency = 'VND'): Money {
    return new Money(0, currency);
  }

  get amount(): number {
    return this._amount;
  }

  get currency(): string {
    return this._currency;
  }

  add(other: Money): Money {
    if (this._currency !== other._currency) {
      throw new CurrencyMismatchException(this._currency, other._currency);
    }

    return new Money(this._amount + other._amount, this._currency);
  }

  multiply(multiplier: number): Money {
    return new Money(this._amount * multiplier, this._currency);
  }

  equals(other: Money): boolean {
    return this._amount === other._amount && this._currency === other._currency;
  }
}
