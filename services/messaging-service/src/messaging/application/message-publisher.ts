import { Message } from "../domain/message";

export const MESSAGE_PUBLISHER = Symbol("MESSAGE_PUBLISHER");

export interface PublishMessageInput {
  threadId: string;
  message: Message;
  buyerId: string;
  sellerId: string;
  /** Convenience field — the other participant from the sender's perspective. */
  recipientId: string;
}

/**
 * Side-effect output for `SendMessageUseCase`. The Kafka adapter publishes to
 * `messaging.message.sent`; tests use a no-op stub.
 */
export interface MessagePublisher {
  publish(input: PublishMessageInput): Promise<void>;
}
