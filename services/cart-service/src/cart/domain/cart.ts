import {
  CartFullException,
  CartItemLimitExceededException,
  CartItemNotFoundException,
  InvalidCartOperationException,
} from './cart.exceptions';
import { CartItem } from './cart-item';
import { Money } from './money';

export class Cart {
  static readonly MAX_ITEMS = 99;
  static readonly MAX_PER_ITEM = 10;

  private constructor(
    public readonly userId: string,
    private _items: CartItem[],
    private _updatedAt: Date,
  ) {}

  static create(userId: string): Cart {
    if (!userId) {
      throw new InvalidCartOperationException('userId required');
    }

    return new Cart(userId, [], new Date());
  }

  static fromPersistence(
    userId: string,
    items: CartItem[],
    updatedAt: Date,
  ): Cart {
    return new Cart(userId, items, updatedAt);
  }

  get items(): readonly CartItem[] {
    return this._items;
  }

  get updatedAt(): Date {
    return this._updatedAt;
  }

  get itemCount(): number {
    return this._items.reduce((sum, item) => sum + item.quantity, 0);
  }

  get uniqueItemCount(): number {
    return this._items.length;
  }

  get totalAmount(): Money {
    return this._items.reduce(
      (total, item) => total.add(item.subtotal),
      Money.zero('VND'),
    );
  }

  get isEmpty(): boolean {
    return this._items.length === 0;
  }

  addItem(item: CartItem): void {
    const existing = this._items.find(
      (cartItem) => cartItem.productId === item.productId,
    );

    if (existing) {
      const quantity = existing.quantity + item.quantity;
      if (quantity > Cart.MAX_PER_ITEM) {
        throw new CartItemLimitExceededException(
          item.productId,
          Cart.MAX_PER_ITEM,
          quantity,
        );
      }

      existing.updateQuantity(quantity);
    } else {
      if (this._items.length >= Cart.MAX_ITEMS) {
        throw new CartFullException(Cart.MAX_ITEMS);
      }

      if (item.quantity > Cart.MAX_PER_ITEM) {
        throw new CartItemLimitExceededException(
          item.productId,
          Cart.MAX_PER_ITEM,
          item.quantity,
        );
      }

      this._items = [...this._items, item];
    }

    this._updatedAt = new Date();
  }

  removeItem(productId: string): void {
    const originalLength = this._items.length;
    this._items = this._items.filter((item) => item.productId !== productId);

    if (this._items.length === originalLength) {
      throw new CartItemNotFoundException(productId);
    }

    this._updatedAt = new Date();
  }

  updateItemQuantity(productId: string, quantity: number): void {
    if (quantity <= 0) {
      this.removeItem(productId);
      return;
    }

    if (quantity > Cart.MAX_PER_ITEM) {
      throw new CartItemLimitExceededException(
        productId,
        Cart.MAX_PER_ITEM,
        quantity,
      );
    }

    const item = this._items.find(
      (cartItem) => cartItem.productId === productId,
    );
    if (!item) {
      throw new CartItemNotFoundException(productId);
    }

    item.updateQuantity(quantity);
    this._updatedAt = new Date();
  }

  clear(): void {
    this._items = [];
    this._updatedAt = new Date();
  }
}
