import { NestFactory } from '@nestjs/core';
import { MicroserviceOptions, Transport } from '@nestjs/microservices';
import { SwaggerModule, DocumentBuilder } from '@nestjs/swagger';
import { AppModule } from './app.module';
import { startTracing } from './tracing';

async function bootstrap() {
  startTracing();
  const app = await NestFactory.create(AppModule);

  // —— Kafka transport ——————————————————————
  const brokers = (process.env.KAFKA_BOOTSTRAP_SERVERS ?? 'localhost:9092')
    .split(',')
    .map((b: string) => b.trim())
    .filter((b: string) => b.length > 0);

  app.connectMicroservice<MicroserviceOptions>({
    transport: Transport.KAFKA,
    options: {
      client: {
        clientId: 'notification-service',
        brokers,
      },
      consumer: {
        groupId: process.env.KAFKA_CONSUMER_GROUP ?? 'notification-service',
        allowAutoTopicCreation: true,
      },
    },
  });

  const config = new DocumentBuilder()
    .setTitle('Notification Service')
    .setVersion('1.0')
    .build();
  const document = SwaggerModule.createDocument(app, config);
  SwaggerModule.setup('api-docs', app, document);

  await app.startAllMicroservices();
  await app.listen(8087);
}
void bootstrap();
