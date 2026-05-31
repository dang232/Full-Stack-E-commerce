import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Headers,
  Param,
  Post,
  Query,
  Req,
  UseGuards,
} from "@nestjs/common";
import { CreateThreadUseCase } from "../application/create-thread.use-case";
import { ListMessagesUseCase } from "../application/list-messages.use-case";
import { ListThreadsUseCase } from "../application/list-threads.use-case";
import { MarkThreadReadUseCase } from "../application/mark-thread-read.use-case";
import { SendMessageUseCase } from "../application/send-message.use-case";
import { ApiResponse } from "./api-response";
import type { AuthenticatedRequest } from "./auth/authenticated-request";
import { JwtAuthGuard } from "./auth/jwt-auth.guard";

interface CreateThreadBody {
  recipientId?: unknown;
  productId?: unknown;
}

interface SendMessageBody {
  body?: unknown;
}

const DEFAULT_PAGE_SIZE = 30;

/**
 * REST surface for buyer-seller messaging. Every endpoint runs through
 * `JwtAuthGuard` and resolves the caller from `req.user.sub` — never from
 * a client header — to match the IDOR-safe pattern used by notification-service.
 */
@Controller("messaging")
@UseGuards(JwtAuthGuard)
export class MessagingController {
  constructor(
    private readonly listThreads: ListThreadsUseCase,
    private readonly createThread: CreateThreadUseCase,
    private readonly listMessages: ListMessagesUseCase,
    private readonly sendMessage: SendMessageUseCase,
    private readonly markThreadRead: MarkThreadReadUseCase,
  ) {}

  @Get("threads")
  async getThreads(
    @Req() req: AuthenticatedRequest,
    @Query("limit") limitRaw: string | undefined,
  ) {
    const userId = req.user.sub;
    const limit = this.parseInt(limitRaw, 50);
    const items = await this.listThreads.execute(userId, limit);
    return ApiResponse.ok({
      content: items.map((item) => ({
        id: item.thread.id,
        buyerId: item.thread.buyerId,
        sellerId: item.thread.sellerId,
        otherPartyId: item.thread.otherParty(userId),
        productId: item.thread.productId,
        lastMessageAt: item.thread.lastMessageAt.toISOString(),
        lastMessageBody: item.lastMessageBody,
        lastMessageSenderId: item.lastMessageSenderId,
        unreadCount: item.unreadCount,
      })),
    });
  }

  @Post("threads")
  async openThread(
    @Req() req: AuthenticatedRequest,
    @Body() body: CreateThreadBody,
  ) {
    const userId = req.user.sub;
    if (
      typeof body?.recipientId !== "string" ||
      body.recipientId.length === 0
    ) {
      throw new BadRequestException("recipientId is required");
    }
    const productId =
      typeof body.productId === "string" && body.productId.length > 0
        ? body.productId
        : null;

    const thread = await this.createThread.execute({
      callerId: userId,
      recipientId: body.recipientId,
      productId,
    });

    return ApiResponse.ok({
      id: thread.id,
      buyerId: thread.buyerId,
      sellerId: thread.sellerId,
      otherPartyId: thread.otherParty(userId),
      productId: thread.productId,
      lastMessageAt: thread.lastMessageAt.toISOString(),
    });
  }

  @Get("threads/:id/messages")
  async getMessages(
    @Req() req: AuthenticatedRequest,
    @Param("id") id: string,
    @Query("cursor") cursor: string | undefined,
    @Query("limit") limitRaw: string | undefined,
  ) {
    const userId = req.user.sub;
    const limit = this.parseInt(limitRaw, DEFAULT_PAGE_SIZE);
    const page = await this.listMessages.execute(
      userId,
      id,
      cursor && cursor.length > 0 ? cursor : null,
      limit,
    );
    return ApiResponse.ok({
      content: page.content.map((message) => ({
        id: message.id,
        threadId: message.threadId,
        senderId: message.senderId,
        body: message.body,
        sentAt: message.sentAt.toISOString(),
      })),
      nextCursor: page.nextCursor,
      hasMore: page.hasMore,
    });
  }

  @Post("threads/:id/messages")
  async postMessage(
    @Req() req: AuthenticatedRequest,
    @Param("id") id: string,
    @Body() body: SendMessageBody,
    @Headers("idempotency-key") idempotencyKey: string | undefined,
  ) {
    const userId = req.user.sub;
    if (typeof body?.body !== "string" || body.body.trim().length === 0) {
      throw new BadRequestException("body is required");
    }
    const message = await this.sendMessage.execute({
      callerId: userId,
      threadId: id,
      body: body.body,
      idempotencyKey: idempotencyKey ?? null,
    });
    return ApiResponse.ok({
      id: message.id,
      threadId: message.threadId,
      senderId: message.senderId,
      body: message.body,
      sentAt: message.sentAt.toISOString(),
    });
  }

  @Post("threads/:id/read")
  async readThread(@Req() req: AuthenticatedRequest, @Param("id") id: string) {
    const userId = req.user.sub;
    const result = await this.markThreadRead.execute(userId, id);
    return ApiResponse.ok({ readAt: result.readAt.toISOString() });
  }

  private parseInt(value: string | undefined, fallback: number): number {
    if (value === undefined || value === "") return fallback;
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }
}
