import { Entity, Column, PrimaryGeneratedColumn, Index } from 'typeorm';

@Entity('health_metrics')
@Index(['service_id', 'time'])
export class HealthMetric {
  @PrimaryGeneratedColumn('increment')
  id!: number;

  @Column({ type: 'timestamptz' })
  time!: Date;

  @Column({ name: 'service_id' })
  serviceId!: string;

  @Column()
  status!: string;

  @Column({ name: 'response_ms', nullable: true })
  responseMs!: number | null;

  @Column({ type: 'jsonb', nullable: true })
  details!: Record<string, unknown> | null;
}
