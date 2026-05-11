import { Money } from '../domain/money';

export const PRODUCT_CLIENT = Symbol('PRODUCT_CLIENT');

export interface ProductSnapshot {
  productId: string;
  productName: string;
  productImage: string;
  unitPrice: Money;
}

export interface ProductClientPort {
  getSnapshot(productId: string): Promise<ProductSnapshot>;
}
