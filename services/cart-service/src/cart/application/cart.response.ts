import type { CartItemResponse } from './cart-item.response';
import type { MoneyResponse } from './money.response';

export interface CartResponse {
  userId: string;
  items: CartItemResponse[];
  itemCount: number;
  uniqueItemCount: number;
  totalAmount: MoneyResponse;
  updatedAt: string;
}
