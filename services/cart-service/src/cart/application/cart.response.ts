import { Cart } from '../domain/cart';
import { CartItem } from '../domain/cart-item';
import { Money } from '../domain/money';

export interface MoneyResponse {
  amount: number;
  currency: string;
}

export interface CartItemResponse {
  productId: string;
  productName: string;
  productImage: string;
  unitPrice: MoneyResponse;
  quantity: number;
  subtotal: MoneyResponse;
  addedAt: string;
}

export interface CartResponse {
  userId: string;
  items: CartItemResponse[];
  itemCount: number;
  uniqueItemCount: number;
  totalAmount: MoneyResponse;
  updatedAt: string;
}

function toMoneyResponse(money: Money): MoneyResponse {
  return {
    amount: money.amount,
    currency: money.currency,
  };
}

function toCartItemResponse(item: CartItem): CartItemResponse {
  return {
    productId: item.productId,
    productName: item.productName,
    productImage: item.productImage,
    unitPrice: toMoneyResponse(item.unitPrice),
    quantity: item.quantity,
    subtotal: toMoneyResponse(item.subtotal),
    addedAt: item.addedAt.toISOString(),
  };
}

export function toCartResponse(cart: Cart): CartResponse {
  return {
    userId: cart.userId,
    items: cart.items.map(toCartItemResponse),
    itemCount: cart.itemCount,
    uniqueItemCount: cart.uniqueItemCount,
    totalAmount: toMoneyResponse(cart.totalAmount),
    updatedAt: cart.updatedAt.toISOString(),
  };
}
