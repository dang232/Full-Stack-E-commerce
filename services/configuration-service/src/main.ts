import { NestFactory } from '@nestjs/core';
import { Reflector } from '@nestjs/core';
import { AppModule } from './app.module.js';
import { GatewayAuthGuard } from './common/guards/gateway-auth.guard.js';
import { RolesGuard } from './common/guards/roles.guard.js';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);

  const reflector = app.get(Reflector);
  app.useGlobalGuards(new GatewayAuthGuard(reflector), new RolesGuard(reflector));

  await app.listen(Number(process.env.SERVER_PORT ?? 8097));
}
void bootstrap();
