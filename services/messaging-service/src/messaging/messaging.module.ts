import { MikroOrmModule } from "@mikro-orm/nestjs";
import { Module } from "@nestjs/common";
import { PassportModule } from "@nestjs/passport";
import { CreateThreadUseCase } from "./application/create-thread.use-case";
import { KafkaMessageConsumer } from "./application/kafka-message.consumer";
import {
  KafkaMessagePublisher,
  NoopMessagePublisher,
} from "./application/kafka-message.publisher";
import { ListMessagesUseCase } from "./application/list-messages.use-case";
import { ListThreadsUseCase } from "./application/list-threads.use-case";
import { MarkThreadReadUseCase } from "./application/mark-thread-read.use-case";
import { MESSAGE_PUBLISHER } from "./application/message-publisher";
import { SendMessageUseCase } from "./application/send-message.use-case";
import { MESSAGE_REPOSITORY } from "./domain/message.repository";
import { THREAD_REPOSITORY } from "./domain/thread.repository";
import { JwtStrategy } from "./infrastructure/auth/jwt.strategy";
import { WsJwtVerifier } from "./infrastructure/auth/ws-jwt.verifier";
import { IdempotencyStore } from "./infrastructure/idempotency-store";
import { MessageMikroOrmEntity } from "./infrastructure/message.mikro-orm-entity";
import { MessageMikroOrmRepository } from "./infrastructure/message.mikro-orm-repository";
import { MessagingController } from "./infrastructure/messaging.controller";
import { MessagingWsGateway } from "./infrastructure/messaging-ws.gateway";
import { ThreadMikroOrmEntity } from "./infrastructure/thread.mikro-orm-entity";
import { ThreadMikroOrmRepository } from "./infrastructure/thread.mikro-orm-repository";

const KAFKA_DISABLED = process.env.MESSAGING_DISABLE_KAFKA === "true";

@Module({
  imports: [
    MikroOrmModule.forFeature([ThreadMikroOrmEntity, MessageMikroOrmEntity]),
    PassportModule.register({ defaultStrategy: "jwt" }),
  ],
  controllers: [MessagingController, KafkaMessageConsumer],
  providers: [
    JwtStrategy,
    WsJwtVerifier,
    IdempotencyStore,
    MessagingWsGateway,
    {
      provide: MESSAGE_PUBLISHER,
      useClass: KAFKA_DISABLED ? NoopMessagePublisher : KafkaMessagePublisher,
    },
    {
      provide: THREAD_REPOSITORY,
      useClass: ThreadMikroOrmRepository,
    },
    {
      provide: MESSAGE_REPOSITORY,
      useClass: MessageMikroOrmRepository,
    },
    ListThreadsUseCase,
    CreateThreadUseCase,
    ListMessagesUseCase,
    SendMessageUseCase,
    MarkThreadReadUseCase,
  ],
})
export class MessagingModule {}
