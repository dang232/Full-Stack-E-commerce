import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
} from '@nestjs/common';
import { Response } from 'express';
import { CartDomainException } from '../domain/cart-domain.exception';
import { ApiResponse } from './api-response';

@Catch()
export class CartExceptionFilter implements ExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost): void {
    const response = host.switchToHttp().getResponse<Response>();

    if (exception instanceof CartDomainException) {
      response
        .status(this.resolveStatus(exception))
        .json(ApiResponse.error(exception.message, exception.errorCode));
      return;
    }

    if (exception instanceof HttpException) {
      const status = exception.getStatus();
      response
        .status(status)
        .json(
          ApiResponse.error(
            exception.message,
            HttpStatus[status] ?? 'HTTP_ERROR',
          ),
        );
      return;
    }

    response
      .status(HttpStatus.INTERNAL_SERVER_ERROR)
      .json(
        ApiResponse.error('Internal server error', 'INTERNAL_SERVER_ERROR'),
      );
  }

  private resolveStatus(exception: CartDomainException): number {
    switch (exception.errorCode) {
      case 'CART_FULL': return 422;
      case 'CART_ITEM_LIMIT_EXCEEDED': return 422;
      case 'CART_ITEM_NOT_FOUND': return 404;
      case 'INVALID_CART_OPERATION': return 400;
      case 'CURRENCY_MISMATCH': return 500;
      case 'PRODUCT_NOT_FOUND': return 404;
      default: return 500;
    }
  }
}
