import { Entity, Column, PrimaryGeneratedColumn, Index } from 'typeorm';

@Entity('alerts')
@Index(['serviceId'])
@Index(['createdAt'])
export class Alert {
  @PrimaryGeneratedColumn()
  id!: number;

  @Column({ name: 'service_id' })
  serviceId!: string;

  @Column()
  type!: string;

  @Column({ type: 'text', nullable: true })
  message!: string | null;

  @Column({ name: 'created_at', type: 'timestamptz', default: () => 'NOW()' })
  createdAt!: Date;

  @Column({ name: 'resolved_at', type: 'timestamptz', nullable: true })
  resolvedAt!: Date | null;
}
