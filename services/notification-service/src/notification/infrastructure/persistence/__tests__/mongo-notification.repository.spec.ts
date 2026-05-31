import { MongooseModule } from '@nestjs/mongoose';
import { Test } from '@nestjs/testing';
import { MongoMemoryServer } from 'mongodb-memory-server';
import { Connection, connect } from 'mongoose';
import { MongoNotificationRepository } from '../mongo-notification.repository';
import { NotificationSchemaClass, NotificationSchema } from '../mongo-notification.schema';
import { Notification } from '../../../domain/model/notification';
import { NotificationType } from '../../../domain/model/notification-type.enum';
import { NotificationThread } from '../../../domain/model/notification-thread';

describe('MongoNotificationRepository', () => {
  let mongod: MongoMemoryServer;
  let mongoConnection: Connection;
  let repo: MongoNotificationRepository;

  beforeAll(async () => {
    mongod = await MongoMemoryServer.create();
    mongoConnection = (await connect(mongod.getUri())).connection;

    const module = await Test.createTestingModule({
      imports: [
        MongooseModule.forRoot(mongod.getUri()),
        MongooseModule.forFeature([
          { name: NotificationSchemaClass.name, schema: NotificationSchema },
        ]),
      ],
      providers: [MongoNotificationRepository],
    }).compile();

    repo = module.get(MongoNotificationRepository);
  });

  afterAll(async () => {
    await mongoConnection.close();
    await mongod.stop();
  });

  it('saves and retrieves a notification by id', async () => {
    const n = Notification.create({
      userId: 'user-1',
      type: NotificationType.ORDER_CREATED,
      title: 'Test',
      body: 'Test body',
      deepLink: '/orders/123',
    });
    await repo.save(n);

    const found = await repo.findById(n.id);
    expect(found).not.toBeNull();
    expect(found!.id).toBe(n.id);
    expect(found!.userId).toBe('user-1');
    expect(found!.title).toBe('Test');
    expect(found!.deepLink).toBe('/orders/123');
  });

  it('finds by idempotency key', async () => {
    const n = Notification.create({
      userId: 'user-2',
      type: NotificationType.PAYMENT_COMPLETED,
      title: 'Payment',
      body: 'Done',
      idempotencyKey: 'payment.completed:ORD-001:PAYMENT_COMPLETED',
    });
    await repo.save(n);

    const found = await repo.findByIdempotencyKey('payment.completed:ORD-001:PAYMENT_COMPLETED');
    expect(found).not.toBeNull();
    expect(found!.id).toBe(n.id);
  });

  it('finds by user with pagination', async () => {
    // Create 3 notifications for user-3
    for (let i = 0; i < 3; i++) {
      await repo.save(
        Notification.create({
          userId: 'user-3',
          type: NotificationType.ORDER_SHIPPED,
          title: `Notif ${i}`,
          body: `Body ${i}`,
        }),
      );
    }

    const result = await repo.findByUser({ userId: 'user-3', page: 0, limit: 2 });
    expect(result.items).toHaveLength(2);
    expect(result.total).toBe(3);
  });

  it('counts unread notifications', async () => {
    const count = await repo.countUnread('user-3');
    expect(count).toBe(3); // All 3 from previous test are unread
  });

  it('marks all read for user and returns count', async () => {
    const updated = await repo.markAllReadForUser('user-3');
    expect(updated).toBe(3);

    const count = await repo.countUnread('user-3');
    expect(count).toBe(0);
  });

  it('finds threads by user', async () => {
    const thread = NotificationThread.create('order:ORD-100', 'Đơn hàng #ORD-100');
    await repo.save(
      Notification.create({
        userId: 'user-4',
        type: NotificationType.ORDER_CREATED,
        title: 'Created',
        body: 'Order created',
        thread,
      }),
    );
    await repo.save(
      Notification.create({
        userId: 'user-4',
        type: NotificationType.ORDER_SHIPPED,
        title: 'Shipped',
        body: 'Order shipped',
        thread,
      }),
    );

    const result = await repo.findThreadsByUser('user-4', 0, 10);
    expect(result.threads).toHaveLength(1);
    expect(result.threads[0].threadId).toBe('order:ORD-100');
    expect(result.threads[0].totalCount).toBe(2);
    expect(result.threads[0].unreadCount).toBe(2);
  });

  it('finds notifications by thread', async () => {
    const items = await repo.findByThread('order:ORD-100', 'user-4');
    expect(items).toHaveLength(2);
    expect(items[0].type).toBe(NotificationType.ORDER_CREATED); // sorted by createdAt ASC
  });

  it('findByIdAndUserId returns null for wrong user', async () => {
    const n = Notification.create({
      userId: 'user-5',
      type: NotificationType.PAYOUT_COMPLETED,
      title: 'Payout',
      body: 'Done',
    });
    await repo.save(n);

    const found = await repo.findByIdAndUserId(n.id, 'wrong-user');
    expect(found).toBeNull();
  });
});
