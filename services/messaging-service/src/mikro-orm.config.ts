import { defineConfig } from "@mikro-orm/postgresql";
import { MessageMikroOrmEntity } from "./messaging/infrastructure/message.mikro-orm-entity";
import { ThreadMikroOrmEntity } from "./messaging/infrastructure/thread.mikro-orm-entity";

export default defineConfig({
  entities: [ThreadMikroOrmEntity, MessageMikroOrmEntity],
  host: process.env.DB_HOST ?? "localhost",
  port: Number(process.env.DB_PORT ?? 5432),
  user: process.env.DB_USERNAME ?? "postgres",
  password: process.env.DB_PASSWORD ?? "postgres",
  dbName: process.env.DB_DATABASE ?? "messaging_service",
  // Schema lives under messaging_svc; tables carry their own schema option.
  // Raw SQL migrations under src/db/migration are applied externally.
  migrations: { disableForeignKeys: false },
  debug: false,
});
