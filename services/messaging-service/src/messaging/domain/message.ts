import { randomUUID } from "node:crypto";

export interface MessageProperties {
  id: string;
  threadId: string;
  senderId: string;
  body: string;
  sentAt: Date;
}

export class Message {
  public readonly id: string;
  public readonly threadId: string;
  public readonly senderId: string;
  public readonly body: string;
  public readonly sentAt: Date;

  constructor(properties: MessageProperties) {
    this.id = properties.id;
    this.threadId = properties.threadId;
    this.senderId = properties.senderId;
    this.body = properties.body;
    this.sentAt = properties.sentAt;
  }

  static create(
    properties: Omit<MessageProperties, "id" | "sentAt"> & { now?: Date },
  ): Message {
    return new Message({
      id: randomUUID(),
      threadId: properties.threadId,
      senderId: properties.senderId,
      body: properties.body,
      sentAt: properties.now ?? new Date(),
    });
  }
}
