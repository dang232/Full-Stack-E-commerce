import { Cart } from './cart';

export const CART_REPOSITORY = Symbol('CART_REPOSITORY');

export interface CartRepository {
  findByUserId(userId: string): Promise<Cart | null>;
  save(cart: Cart, ttlSeconds: number): Promise<void>;
  delete(userId: string): Promise<void>;
}
