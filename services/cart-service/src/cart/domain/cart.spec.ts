import { Cart } from './cart';
import {
  CartItemLimitExceededException,
  CartItemNotFoundException,
} from './cart.exceptions';
import { CartItem } from './cart-item';
import { Money } from './money';

describe('Cart', () => {
  it('adds and merges items', () => {
    const cart = Cart.create('user-1');

    cart.addItem(
      CartItem.create('product-1', 'Keyboard', '', Money.of(1000), 2),
    );
    cart.addItem(
      CartItem.create('product-1', 'Keyboard', '', Money.of(1000), 3),
    );

    expect(cart.itemCount).toBe(5);
    expect(cart.uniqueItemCount).toBe(1);
    expect(cart.totalAmount.amount).toBe(5000);
  });

  it('rejects per-item quantity above max', () => {
    const cart = Cart.create('user-1');

    expect(() =>
      cart.addItem(
        CartItem.create('product-1', 'Keyboard', '', Money.of(1000), 11),
      ),
    ).toThrow(CartItemLimitExceededException);
  });

  it('removes item when quantity updates to zero', () => {
    const cart = Cart.create('user-1');
    cart.addItem(
      CartItem.create('product-1', 'Keyboard', '', Money.of(1000), 2),
    );

    cart.updateItemQuantity('product-1', 0);

    expect(cart.isEmpty).toBe(true);
  });

  it('throws when removing missing item', () => {
    const cart = Cart.create('user-1');

    expect(() => cart.removeItem('missing')).toThrow(CartItemNotFoundException);
  });
});
