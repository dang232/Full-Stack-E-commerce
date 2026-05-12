import type { MoneyResponse } from './money.response';

export interface CartItemResponse {
  productId: string;
  productName: string;
  productImage: string;
  unitPrice: MoneyResponse;
  quantity: number;
  subtotal: MoneyResponse;
  addedAt: string;
}
