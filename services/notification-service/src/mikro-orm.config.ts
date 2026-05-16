import { defineConfig } from '@mikro-orm/postgresql';
import { NotificationMikroOrmEntity } from './notification/infrastructure/notification.mikro-orm-entity';

export default defineConfig({
  entities: [NotificationMikroOrmEntity],
  host: process.env.DB_HOST ?? 'localhost',
  port: Number(process.env.DB_PORT ?? 5432),
  user: process.env.DB_USERNAME ?? 'postgres',
  password: process.env.DB_PASSWORD ?? 'postgres',
  dbName: process.env.DB_DATABASE ?? 'notification_service',
  // Schema lives under notification_svc; tables carry their own schema option.
  // We leave global migrations off — the codebase uses raw SQL migration files
  // under src/db/migration applied externally (V1__notification_schema.sql etc).
  migrations: { disableForeignKeys: false },
  debug: false,
});
