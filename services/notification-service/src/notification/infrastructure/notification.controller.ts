import {
  BadRequestException,
  Controller,
  Get,
  Headers,
  NotFoundException,
  Param,
  Post,
  Query,
} from '@nestjs/common';
import { CountUnreadNotificationsUseCase } from '../application/count-unread-notifications.use-case';
import { FindNotificationByIdUseCase } from '../application/find-notification-by-id.use-case';
import { FindUserNotificationsUseCase } from '../application/find-user-notifications.use-case';
import { MarkAllNotificationsReadUseCase } from '../application/mark-all-notifications-read.use-case';
import { MarkNotificationReadUseCase } from '../application/mark-notification-read.use-case';
import {
  SendNotificationUseCase,
  TEST_NOTIFICATION_TYPE,
} from '../application/send-notification.use-case';
import { ApiResponse } from './api-response';

const DEFAULT_PAGE_SIZE = 30;

@Controller('notifications')
export class NotificationController {
  constructor(
    private readonly findUserNotificationsUseCase: FindUserNotificationsUseCase,
    private readonly findNotificationByIdUseCase: FindNotificationByIdUseCase,
    private readonly sendNotificationUseCase: SendNotificationUseCase,
    private readonly markNotificationReadUseCase: MarkNotificationReadUseCase,
    private readonly markAllNotificationsReadUseCase: MarkAllNotificationsReadUseCase,
    private readonly countUnreadNotificationsUseCase: CountUnreadNotificationsUseCase,
  ) {}

  @Get()
  async findUserNotifications(
    @Headers('x-user-id') userIdHeader: string | undefined,
    @Query('userId') userIdQuery: string | undefined,
    @Query('page') pageRaw: string | undefined,
    @Query('size') sizeRaw: string | undefined,
  ) {
    const userId = this.requireUserId(userIdHeader, userIdQuery);
    const page = this.parseInt(pageRaw, 0);
    const size = this.parseInt(sizeRaw, DEFAULT_PAGE_SIZE);
    return ApiResponse.ok(
      await this.findUserNotificationsUseCase.executePaged(userId, page, size),
    );
  }

  @Get('unread-count')
  async unreadCount(
    @Headers('x-user-id') userIdHeader: string | undefined,
    @Query('userId') userIdQuery: string | undefined,
  ) {
    const userId = this.requireUserId(userIdHeader, userIdQuery);
    return ApiResponse.ok({
      count: await this.countUnreadNotificationsUseCase.execute(userId),
    });
  }

  @Post('mark-all-read')
  async markAllRead(
    @Headers('x-user-id') userIdHeader: string | undefined,
    @Query('userId') userIdQuery: string | undefined,
  ) {
    const userId = this.requireUserId(userIdHeader, userIdQuery);
    return ApiResponse.ok({
      updated: await this.markAllNotificationsReadUseCase.execute(userId),
    });
  }

  @Post(':id/read')
  async markRead(
    @Headers('x-user-id') userIdHeader: string | undefined,
    @Query('userId') userIdQuery: string | undefined,
    @Param('id') id: string,
  ) {
    const userId = this.requireUserId(userIdHeader, userIdQuery);
    const notification = await this.markNotificationReadUseCase.execute(
      id,
      userId,
    );
    if (!notification) {
      throw new NotFoundException('Notification not found');
    }
    return ApiResponse.ok(notification);
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
    const userId = this.requireUserId(userIdHeader, userIdQuery);
    return ApiResponse.ok(
      await this.sendNotificationUseCase.send({
        type: TEST_NOTIFICATION_TYPE,
        userId,
        title: 'Test notification',
        body: 'This is a test notification.',
        data: { source: 'manual-test' },
      }),
    );
  }

  private requireUserId(
    header: string | undefined,
    query: string | undefined,
  ): string {
    const userId = header ?? query;
    if (!userId) {
      throw new BadRequestException(
        'userId query param or x-user-id header is required',
      );
    }
    return userId;
  }

  private parseInt(value: string | undefined, fallback: number): number {
    if (value === undefined || value === '') return fallback;
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }
}
