export interface RemoveCartItemCommand {
  userId: string;
  /** Composite item key: productId or productId:variantId */
  itemKey: string;
}
