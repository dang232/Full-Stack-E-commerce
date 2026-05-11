import { CartRepository } from '../domain/cart.repository';

export class ClearCartUseCase {
  constructor(private readonly cartRepository: CartRepository) {}

  async execute(userId: string): Promise<void> {
    await this.cartRepository.delete(userId);
  }
}
