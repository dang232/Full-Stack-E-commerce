import {
  BadRequestException,
  Body,
  Controller,
  Delete,
  Get,
  Headers,
  Param,
  Post,
  Put,
  UseFilters,
} from '@nestjs/common';
import { AddToCartUseCase } from '../application/add-to-cart.use-case';
import { ClearCartUseCase } from '../application/clear-cart.use-case';
import { RemoveCartItemUseCase } from '../application/remove-cart-item.use-case';
import { UpdateCartItemUseCase } from '../application/update-cart-item.use-case';
import { ViewCartUseCase } from '../application/view-cart.use-case';
import type { CartResponse } from '../application/cart.response';
import { ApiResponse } from './api-response';
import { CartExceptionFilter } from './cart.exception-filter';
import type { AddCartItemRequest } from './add-cart-item.request';
import type { UpdateCartItemRequest } from './update-cart-item.request';

@Controller('cart')
@UseFilters(CartExceptionFilter)
export class CartController {
  constructor(
    private readonly addToCartUseCase: AddToCartUseCase,
    private readonly viewCartUseCase: ViewCartUseCase,
    private readonly updateCartItemUseCase: UpdateCartItemUseCase,
    private readonly removeCartItemUseCase: RemoveCartItemUseCase,
    private readonly clearCartUseCase: ClearCartUseCase,
  ) {}

  @Get()
  async viewCart(
    @Headers('x-user-id') userId: string | undefined,
  ): Promise<ApiResponse<CartResponse>> {
    return ApiResponse.ok(
      await this.viewCartUseCase.execute(this.requireUserId(userId)),
    );
  }

  @Post('items')
  async addItem(
    @Headers('x-user-id') userId: string | undefined,
    @Body() request: AddCartItemRequest,
  ): Promise<ApiResponse<CartResponse>> {
    if (!request.productId) {
      throw new BadRequestException('productId is required');
    }

    const cart = await this.addToCartUseCase.execute({
      userId: this.requireUserId(userId),
      productId: request.productId,
      quantity: request.quantity ?? 1,
    });

    return ApiResponse.ok('Cart item added', cart);
  }

  @Put('items/:productId')
  async updateItem(
    @Headers('x-user-id') userId: string | undefined,
    @Param('productId') productId: string,
    @Body() request: UpdateCartItemRequest,
  ): Promise<ApiResponse<CartResponse>> {
    if (request.quantity === undefined || request.quantity === null) {
      throw new BadRequestException('quantity is required');
    }

    const cart = await this.updateCartItemUseCase.execute({
      userId: this.requireUserId(userId),
      productId,
      quantity: request.quantity,
    });

    return ApiResponse.ok('Cart item updated', cart);
  }

  @Delete('items/:productId')
  async removeItem(
    @Headers('x-user-id') userId: string | undefined,
    @Param('productId') productId: string,
  ): Promise<ApiResponse<CartResponse>> {
    const cart = await this.removeCartItemUseCase.execute({
      userId: this.requireUserId(userId),
      productId,
    });

    return ApiResponse.ok('Cart item removed', cart);
  }

  @Delete()
  async clearCart(
    @Headers('x-user-id') userId: string | undefined,
  ): Promise<ApiResponse<null>> {
    await this.clearCartUseCase.execute(this.requireUserId(userId));
    return ApiResponse.ok('Cart cleared', null);
  }

  private requireUserId(userId: string | undefined): string {
    if (!userId) {
      throw new BadRequestException('x-user-id header is required');
    }

    return userId;
  }
}
