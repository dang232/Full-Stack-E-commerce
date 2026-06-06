import { Entity, PrimaryKey, Property } from '@mikro-orm/core';

@Entity({ tableName: 'carts' })
export class CartMikroOrmEntity {
  @PrimaryKey({ fieldName: 'user_id', type: 'varchar', length: 255 })
  userId!: string;

  @Property({ type: 'jsonb' })
  items!: unknown;

  @Property({ fieldName: 'updated_at', type: 'timestamptz' })
  updatedAt: Date = new Date();

  @Property({ version: true })
  version: number = 1;
}
