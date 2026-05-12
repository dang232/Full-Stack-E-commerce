import type { ProductSnapshot } from './product-snapshot';

export interface ProductClientPort {
  getSnapshot(productId: string): Promise<ProductSnapshot>;
}
