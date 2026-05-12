import { ProductNotFoundException } from '../domain/product-not-found.exception';
import { Money } from '../domain/money';
import type { ProductClientPort } from '../application/product-client.port';
import type { ProductSnapshot } from '../application/product-snapshot';

interface ProductServiceResponse {
  id?: string;
  productId?: string;
  name?: string;
  productName?: string;
  image?: string;
  productImage?: string;
  price?: number | { amount: number; currency?: string };
  unitPrice?: number | { amount: number; currency?: string };
  currency?: string;
}

export class ProductHttpClientAdapter implements ProductClientPort {
  constructor(private readonly productServiceUrl?: string) {}

  async getSnapshot(productId: string): Promise<ProductSnapshot> {
    if (!this.productServiceUrl) {
      return {
        productId,
        productName: productId,
        productImage: '',
        unitPrice: Money.zero('VND'),
      };
    }

    const response = await fetch(
      `${this.productServiceUrl}/products/${productId}`,
    );

    if (response.status === 404) {
      throw new ProductNotFoundException(productId);
    }

    if (!response.ok) {
      throw new ProductNotFoundException(productId);
    }

    const payload = (await response.json()) as
      | ProductServiceResponse
      | { data: ProductServiceResponse };
    const product = 'data' in payload ? payload.data : payload;
    const price = product.unitPrice ?? product.price ?? 0;
    const amount = typeof price === 'number' ? price : price.amount;
    const currency =
      typeof price === 'number'
        ? (product.currency ?? 'VND')
        : (price.currency ?? product.currency ?? 'VND');

    return {
      productId: product.productId ?? product.id ?? productId,
      productName: product.productName ?? product.name ?? productId,
      productImage: product.productImage ?? product.image ?? '',
      unitPrice: Money.of(amount, currency),
    };
  }
}
