import { registerAs } from '@nestjs/config';

export const databaseConfig = registerAs('database', () => ({
  host: process.env.TIMESCALE_HOST ?? 'localhost',
  port: parseInt(process.env.TIMESCALE_PORT ?? '5440', 10),
  database: process.env.TIMESCALE_DB ?? 'monitoring',
  username: process.env.TIMESCALE_USER ?? 'monitoring',
  password: process.env.TIMESCALE_PASSWORD ?? 'monitoring',
}));
