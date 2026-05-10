import { Column, CreateDateColumn, Entity, PrimaryColumn } from 'typeorm';
import { NotificationStatus } from '../domain/notification-status.enum';
import { NotificationType } from '../domain/notification-type.enum';

@Entity('notifications', { schema: 'notification_svc' })
export class NotificationTypeOrmEntity {
  @PrimaryColumn('uuid')
  id!: string;

  @Column({ name: 'user_id', type: 'varchar', length: 64 })
  userId!: string;

  @Column({ type: 'enum', enum: NotificationType })
  type!: NotificationType;

  @Column({ type: 'varchar', length: 255 })
  title!: string;

  @Column({ type: 'text' })
  body!: string;

  @Column({ type: 'jsonb', default: () => "'{}'::jsonb" })
  data!: Record<string, unknown>;

  @Column({ type: 'text', array: true })
  channels!: string[];

  @Column({ type: 'enum', enum: NotificationStatus })
  status!: NotificationStatus;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt!: Date;
}
