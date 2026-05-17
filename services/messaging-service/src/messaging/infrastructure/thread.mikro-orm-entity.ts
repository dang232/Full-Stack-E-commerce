import { Entity, PrimaryKey, Property } from "@mikro-orm/core";

@Entity({ tableName: "threads", schema: "messaging_svc" })
export class ThreadMikroOrmEntity {
  @PrimaryKey({ type: "uuid" })
  id!: string;

  @Property({ fieldName: "buyer_id", length: 64 })
  buyerId!: string;

  @Property({ fieldName: "seller_id", length: 64 })
  sellerId!: string;

  @Property({ fieldName: "product_id", length: 64, nullable: true })
  productId: string | null = null;

  @Property({ fieldName: "last_message_at", type: "timestamptz" })
  lastMessageAt: Date = new Date();

  @Property({
    fieldName: "buyer_last_read_at",
    type: "timestamptz",
    nullable: true,
  })
  buyerLastReadAt: Date | null = null;

  @Property({
    fieldName: "seller_last_read_at",
    type: "timestamptz",
    nullable: true,
  })
  sellerLastReadAt: Date | null = null;

  @Property({ fieldName: "created_at", type: "timestamptz" })
  createdAt: Date = new Date();
}
