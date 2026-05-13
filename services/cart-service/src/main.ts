import { NestFactory } from '@nestjs/core';
import { ConfigService } from '@nestjs/config';
import { AppModule } from './app.module';
import { startTracing } from './tracing';

async function bootstrap() {
  await startTracing();
  const app = await NestFactory.create(AppModule);
  const configService = app.get(ConfigService);
  await app.listen(Number(configService.get<string>('SERVER_PORT') ?? 8084));
}
void bootstrap();
