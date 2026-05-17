import { Entity, ManyToOne, PrimaryKey, Property } from "@mikro-orm/core";
import { ThreadMikroOrmEntity } from "./thread.mikro-orm-entity";

@Entity({ tableName: "messages", schema: "messaging_svc" })
export class MessageMikroOrmEntity {
  @PrimaryKey({ type: "uuid" })
  id!: string;

  @ManyToOne(() => ThreadMikroOrmEntity, {
    fieldName: "thread_id",
    eager: false,
    deleteRule: "cascade",
  })
  thread!: ThreadMikroOrmEntity;

  @Property({ fieldName: "sender_id", length: 64 })
  senderId!: string;

  @Property({ type: "text" })
  body!: string;

  @Property({ fieldName: "sent_at", type: "timestamptz" })
  sentAt: Date = new Date();
}
