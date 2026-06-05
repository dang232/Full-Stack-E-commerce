export interface UpdateCartItemCommand {
  userId: string;
  /** Composite item key: productId or productId:variantId */
  itemKey: string;
  quantity: number;
}
