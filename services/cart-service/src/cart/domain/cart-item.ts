import { InvalidCartOperationException } from './invalid-cart-operation.exception';
import { Money } from './money';

export class CartItem {
  private constructor(
    public readonly productId: string,
    public readonly productName: string,
    public readonly productImage: string,
    public readonly unitPrice: Money,
    private _quantity: number,
    public readonly addedAt: Date,
  ) {}

  static create(
    productId: string,
    productName: string,
    productImage: string,
    unitPrice: Money,
    quantity: number,
  ): CartItem {
    if (quantity < 1) {
      throw new InvalidCartOperationException(
        'Quantity must be greater than or equal to 1',
      );
    }

    if (!productId) {
      throw new InvalidCartOperationException('productId required');
    }

    return new CartItem(
      productId,
      productName,
      productImage,
      unitPrice,
      quantity,
      new Date(),
    );
  }

  static fromPersistence(
    productId: string,
    productName: string,
    productImage: string,
    unitPrice: Money,
    quantity: number,
    addedAt: Date,
  ): CartItem {
    return new CartItem(
      productId,
      productName,
      productImage,
      unitPrice,
      quantity,
      addedAt,
    );
  }

  get quantity(): number {
    return this._quantity;
  }

  get subtotal(): Money {
    return this.unitPrice.multiply(this._quantity);
  }

  updateQuantity(quantity: number): void {
    this._quantity = quantity;
  }
}
