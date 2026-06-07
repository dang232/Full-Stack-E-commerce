import { Cart } from '../domain/cart';
import { CartItem } from '../domain/cart-item';
import { Money } from '../domain/money';
import { CartRepository } from '../domain/cart.repository';
import { MergeCartUseCase } from './merge-cart.use-case';

function makeItem(productId: string, quantity: number): CartItem {
  return CartItem.create(productId, 'Product', '', Money.of(1000), quantity);
}

function makeCart(ownerId: string, items: CartItem[] = []): Cart {
  const cart = Cart.create(ownerId);
  for (const item of items) {
    cart.addItem(item);
  }
  return cart;
}

function makeRepo(
  userCart: Cart | null,
  guestCart: Cart | null,
): CartRepository & { savedCart: Cart | null; deletedKey: string | null } {
  const repo = {
    savedCart: null as Cart | null,
    deletedKey: null as string | null,

    async findByUserId(userId: string): Promise<Cart | null> {
      return repo.findByOwnerId(userId);
    },

    async findByOwnerId(ownerId: string): Promise<Cart | null> {
      if (ownerId.startsWith('guest:')) return guestCart;
      return userCart;
    },

    async save(cart: Cart, _ttl: number): Promise<void> {
      repo.savedCart = cart;
    },

    async delete(ownerId: string): Promise<void> {
      repo.deletedKey = ownerId;
    },
  };
  return repo;
}

describe('MergeCartUseCase', () => {
  it('returns user cart unchanged when guest cart is null', async () => {
    const userCart = makeCart('user-1', [makeItem('p1', 2)]);
    const repo = makeRepo(userCart, null);
    const useCase = new MergeCartUseCase(repo);

    const result = await useCase.execute('user-1', 'sess-abc');

    expect(result.items).toHaveLength(1);
    expect(result.items[0].quantity).toBe(2);
    expect(repo.deletedKey).toBeNull();
  });

  it('returns user cart unchanged when guest cart is empty', async () => {
    const userCart = makeCart('user-1', [makeItem('p1', 2)]);
    const guestCart = makeCart('guest:sess-abc');
    const repo = makeRepo(userCart, guestCart);
    const useCase = new MergeCartUseCase(repo);

    const result = await useCase.execute('user-1', 'sess-abc');

    expect(result.items).toHaveLength(1);
    expect(repo.deletedKey).toBeNull();
  });

  it('creates new user cart from guest items when user cart is null', async () => {
    const guestCart = makeCart('guest:sess-abc', [makeItem('p1', 3)]);
    const repo = makeRepo(null, guestCart);
    const useCase = new MergeCartUseCase(repo);

    const result = await useCase.execute('user-1', 'sess-abc');

    expect(result.userId).toBe('user-1');
    expect(result.items).toHaveLength(1);
    expect(result.items[0].quantity).toBe(3);
    expect(repo.savedCart?.userId).toBe('user-1');
    expect(repo.deletedKey).toBe('guest:sess-abc');
  });

  it('sums quantities for matching itemKey during merge', async () => {
    const userCart = makeCart('user-1', [makeItem('p1', 2)]);
    const guestCart = makeCart('guest:sess-abc', [makeItem('p1', 3)]);
    const repo = makeRepo(userCart, guestCart);
    const useCase = new MergeCartUseCase(repo);

    const result = await useCase.execute('user-1', 'sess-abc');

    expect(result.items).toHaveLength(1);
    expect(result.items[0].quantity).toBe(5);
    expect(repo.savedCart?.userId).toBe('user-1');
    expect(repo.deletedKey).toBe('guest:sess-abc');
  });

  it('adds new items from guest cart that are not in user cart', async () => {
    const userCart = makeCart('user-1', [makeItem('p1', 1)]);
    const guestCart = makeCart('guest:sess-abc', [makeItem('p2', 2)]);
    const repo = makeRepo(userCart, guestCart);
    const useCase = new MergeCartUseCase(repo);

    const result = await useCase.execute('user-1', 'sess-abc');

    expect(result.items).toHaveLength(2);
    const p2 = result.items.find((i) => i.productId === 'p2');
    expect(p2?.quantity).toBe(2);
    expect(repo.deletedKey).toBe('guest:sess-abc');
  });

  it('deletes guest cart after merge', async () => {
    const guestCart = makeCart('guest:sess-xyz', [makeItem('p1', 1)]);
    const repo = makeRepo(null, guestCart);
    const useCase = new MergeCartUseCase(repo);

    await useCase.execute('user-1', 'sess-xyz');

    expect(repo.deletedKey).toBe('guest:sess-xyz');
  });
});
