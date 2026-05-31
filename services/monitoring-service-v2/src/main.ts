import { NestFactory } from '@nestjs/core';
import { IoAdapter } from '@nestjs/platform-socket.io';
import { AppModule } from './app.module.js';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  app.useWebSocketAdapter(new IoAdapter(app));
  app.enableCors({
    origin: (process.env.CORS_ORIGINS ?? 'http://localhost:3000,http://localhost:5173').split(','),
    credentials: true,
  });

  const port = process.env.PORT ?? 8096;
  await app.listen(port);
  console.log(`Monitoring service running on port ${port}`);
}

void bootstrap();
