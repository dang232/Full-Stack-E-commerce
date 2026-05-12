import { BadRequestException, Controller, Get, Headers, NotFoundException, Param, Post, Query } from '@nestjs/common';
import { FindNotificationByIdUseCase } from '../application/find-notification-by-id.use-case';
import { FindUserNotificationsUseCase } from '../application/find-user-notifications.use-case';
import { SendNotificationUseCase, TEST_NOTIFICATION_TYPE } from '../application/send-notification.use-case';
import { ApiResponse } from './api-response';

@Controller('notifications')
export class NotificationController {
  constructor(
    private readonly findUserNotificationsUseCase: FindUserNotificationsUseCase,
    private readonly findNotificationByIdUseCase: FindNotificationByIdUseCase,
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

    return ApiResponse.ok(await this.findUserNotificationsUseCase.execute(userId));
  }

  @Get(':id')
  async findById(@Param('id') id: string) {
    const notification = await this.findNotificationByIdUseCase.execute(id);

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
      type: TEST_NOTIFICATION_TYPE,
      userId,
      title: 'Test notification',
      body: 'This is a test notification.',
      data: { source: 'manual-test' },
    }));
  }
}
