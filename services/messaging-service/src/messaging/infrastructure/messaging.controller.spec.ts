import { BadRequestException } from "@nestjs/common";
import { CreateThreadUseCase } from "../application/create-thread.use-case";
import { ListMessagesUseCase } from "../application/list-messages.use-case";
import { ListThreadsUseCase } from "../application/list-threads.use-case";
import { MarkThreadReadUseCase } from "../application/mark-thread-read.use-case";
import { SendMessageUseCase } from "../application/send-message.use-case";
import { Message } from "../domain/message";
import { Thread } from "../domain/thread";
import type { AuthenticatedRequest } from "./auth/authenticated-request";
import { MessagingController } from "./messaging.controller";

const requestFor = (sub: string): AuthenticatedRequest =>
  ({ user: { sub } }) as AuthenticatedRequest;

const buildThread = () =>
  new Thread({
    id: "t1",
    buyerId: "alice",
    sellerId: "zoe",
    productId: "P1",
    lastMessageAt: new Date("2026-01-01T00:00:00Z"),
    buyerLastReadAt: null,
    sellerLastReadAt: null,
    createdAt: new Date("2026-01-01T00:00:00Z"),
  });

describe("MessagingController", () => {
  let listThreads: jest.Mocked<Pick<ListThreadsUseCase, "execute">>;
  let createThread: jest.Mocked<Pick<CreateThreadUseCase, "execute">>;
  let listMessages: jest.Mocked<Pick<ListMessagesUseCase, "execute">>;
  let sendMessage: jest.Mocked<Pick<SendMessageUseCase, "execute">>;
  let markRead: jest.Mocked<Pick<MarkThreadReadUseCase, "execute">>;
  let controller: MessagingController;

  beforeEach(() => {
    listThreads = { execute: jest.fn() };
    createThread = { execute: jest.fn() };
    listMessages = { execute: jest.fn() };
    sendMessage = { execute: jest.fn() };
    markRead = { execute: jest.fn() };

    controller = new MessagingController(
      listThreads as ListThreadsUseCase,
      createThread as CreateThreadUseCase,
      listMessages as ListMessagesUseCase,
      sendMessage as SendMessageUseCase,
      markRead as MarkThreadReadUseCase,
    );
  });

  it("GET /messaging/threads serialises threads with the caller-relative otherPartyId", async () => {
    const thread = buildThread();
    listThreads.execute.mockResolvedValue([
      {
        thread,
        lastMessageBody: "hi",
        lastMessageSenderId: "zoe",
        unreadCount: 2,
      },
    ]);

    const result = await controller.getThreads(requestFor("alice"), undefined);

    expect(listThreads.execute).toHaveBeenCalledWith("alice", 50);
    expect(result.success).toBe(true);
    expect(result.data?.content[0]).toEqual(
      expect.objectContaining({
        id: "t1",
        otherPartyId: "zoe",
        productId: "P1",
        unreadCount: 2,
        lastMessageBody: "hi",
      }),
    );
  });

  it("POST /messaging/threads requires recipientId", async () => {
    await expect(
      controller.openThread(requestFor("alice"), {}),
    ).rejects.toThrow(BadRequestException);
  });

  it("POST /messaging/threads delegates to the use case and serialises the response", async () => {
    createThread.execute.mockResolvedValue(buildThread());

    const result = await controller.openThread(requestFor("alice"), {
      recipientId: "zoe",
      productId: "P1",
    });

    expect(createThread.execute).toHaveBeenCalledWith({
      callerId: "alice",
      recipientId: "zoe",
      productId: "P1",
    });
    expect(result.data?.id).toBe("t1");
    expect(result.data?.otherPartyId).toBe("zoe");
  });

  it("POST /messaging/threads/:id/messages forwards the Idempotency-Key header", async () => {
    sendMessage.execute.mockResolvedValue(
      new Message({
        id: "m1",
        threadId: "t1",
        senderId: "alice",
        body: "hi",
        sentAt: new Date("2026-01-01T00:00:00Z"),
      }),
    );

    const result = await controller.postMessage(
      requestFor("alice"),
      "t1",
      { body: "hi" },
      "idem-key-1",
    );

    expect(sendMessage.execute).toHaveBeenCalledWith({
      callerId: "alice",
      threadId: "t1",
      body: "hi",
      idempotencyKey: "idem-key-1",
    });
    expect(result.data?.id).toBe("m1");
  });

  it("POST /messaging/threads/:id/messages rejects empty bodies", async () => {
    await expect(
      controller.postMessage(
        requestFor("alice"),
        "t1",
        { body: "   " },
        undefined,
      ),
    ).rejects.toThrow(BadRequestException);
  });

  it("GET /messaging/threads/:id/messages threads the cursor through", async () => {
    listMessages.execute.mockResolvedValue({
      content: [],
      nextCursor: "2026-01-01T00:00:00.000Z",
      hasMore: true,
    });

    const result = await controller.getMessages(
      requestFor("alice"),
      "t1",
      "2026-01-02T00:00:00.000Z",
      "20",
    );

    expect(listMessages.execute).toHaveBeenCalledWith(
      "alice",
      "t1",
      "2026-01-02T00:00:00.000Z",
      20,
    );
    expect(result.data?.hasMore).toBe(true);
    expect(result.data?.nextCursor).toBe("2026-01-01T00:00:00.000Z");
  });

  it("POST /messaging/threads/:id/read returns the readAt timestamp", async () => {
    markRead.execute.mockResolvedValue({
      readAt: new Date("2026-01-01T00:00:00Z"),
    });
    const result = await controller.readThread(requestFor("alice"), "t1");
    expect(markRead.execute).toHaveBeenCalledWith("alice", "t1");
    expect(result.data?.readAt).toBe("2026-01-01T00:00:00.000Z");
  });
});
