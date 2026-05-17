import {
  Controller,
  Get,
  NotFoundException,
  Param,
  Post,
  Query,
  Req,
  UseGuards,
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
import type { AuthenticatedRequest } from './auth/authenticated-request';
import { JwtAuthGuard } from './auth/jwt-auth.guard';

const DEFAULT_PAGE_SIZE = 30;

/**
 * Every endpoint resolves the caller's identity from the validated JWT
 * (`req.user.sub`) — never from a client-supplied `x-user-id` header or
 * `?userId=` query parameter, which were trusted previously and made the
 * service vulnerable to IDOR via direct (non-gateway) access.
 */
@Controller('notifications')
@UseGuards(JwtAuthGuard)
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
    @Req() req: AuthenticatedRequest,
    @Query('page') pageRaw: string | undefined,
    @Query('size') sizeRaw: string | undefined,
  ) {
    const userId = req.user.sub;
    const page = this.parseInt(pageRaw, 0);
    const size = this.parseInt(sizeRaw, DEFAULT_PAGE_SIZE);
    return ApiResponse.ok(
      await this.findUserNotificationsUseCase.executePaged(userId, page, size),
    );
  }

  @Get('unread-count')
  async unreadCount(@Req() req: AuthenticatedRequest) {
    const userId = req.user.sub;
    return ApiResponse.ok({
      count: await this.countUnreadNotificationsUseCase.execute(userId),
    });
  }

  @Post('mark-all-read')
  async markAllRead(@Req() req: AuthenticatedRequest) {
    const userId = req.user.sub;
    return ApiResponse.ok({
      updated: await this.markAllNotificationsReadUseCase.execute(userId),
    });
  }

  @Post(':id/read')
  async markRead(@Req() req: AuthenticatedRequest, @Param('id') id: string) {
    const userId = req.user.sub;
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
  async createTestNotification(@Req() req: AuthenticatedRequest) {
    const userId = req.user.sub;
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

  private parseInt(value: string | undefined, fallback: number): number {
    if (value === undefined || value === '') return fallback;
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }
}
