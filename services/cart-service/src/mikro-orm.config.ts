import { defineConfig } from '@mikro-orm/postgresql';
import { CartMikroOrmEntity } from './cart/infrastructure/cart.mikro-orm-entity.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) {
  throw new Error(
    'DATABASE_URL environment variable is required but not set. ' +
      'Example: postgresql://vnshop:vnshop@localhost:5441/vnshop_cart',
  );
}

export default defineConfig({
  entities: [CartMikroOrmEntity],
  clientUrl: databaseUrl,
  pool: { min: 2, max: 10 },
  migrations: {
    path: './src/db/migrations',
    disableForeignKeys: false,
  },
  debug: false,
});
