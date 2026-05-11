import { BadRequestException, Controller, Get, Headers, Inject, NotFoundException, Param, Post, Query } from '@nestjs/common';
import { SendNotificationUseCase } from '../application/send-notification.use-case';
import { NotificationType } from '../domain/notification-type.enum';
import { NOTIFICATION_REPOSITORY } from '../domain/notification.repository';
import type { NotificationRepository } from '../domain/notification.repository';
import { ApiResponse } from './api-response';

@Controller('notifications')
export class NotificationController {
  constructor(
    @Inject(NOTIFICATION_REPOSITORY)
    private readonly repository: NotificationRepository,
    private readonly sendNotificationUseCase: SendNotificationUseCase,
  ) {}

  @Get()
  async findUserNotifications(
    @Headers('x-user-id') userIdHeader: string | undefined,
    @Query('userId') userIdQuery: string | undefined,
  ) {
    const userId = userIdHeader ?? userIdQuery;

    if (!userId) {
      throw new BadRequestException('userId query param or x-user-id header is required');
    }

    return ApiResponse.ok(await this.repository.findByUserId(userId));
  }

  @Get(':id')
  async findById(@Param('id') id: string) {
    const notification = await this.repository.findById(id);

    if (!notification) {
      throw new NotFoundException('Notification not found');
    }

    return ApiResponse.ok(notification);
  }

  @Post('test')
  async createTestNotification(
    @Headers('x-user-id') userIdHeader: string | undefined,
    @Query('userId') userIdQuery: string | undefined,
  ) {
    const userId = userIdHeader ?? userIdQuery;

    if (!userId) {
      throw new BadRequestException('userId query param or x-user-id header is required');
    }

    return ApiResponse.ok(await this.sendNotificationUseCase.send({
      type: NotificationType.ORDER_CREATED,
      userId,
      title: 'Test notification',
      body: 'This is a test notification.',
      data: { source: 'manual-test' },
    }));
  }
}
