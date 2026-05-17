import { NestFactory } from "@nestjs/core";
import { MicroserviceOptions, Transport } from "@nestjs/microservices";
import { WsAdapter } from "@nestjs/platform-ws";
import { SwaggerModule, DocumentBuilder } from "@nestjs/swagger";
import { AppModule } from "./app.module";
import { startTracing } from "./tracing";

async function bootstrap() {
  startTracing();
  const app = await NestFactory.create(AppModule);
  app.useWebSocketAdapter(new WsAdapter(app));

  const brokers = (process.env.KAFKA_BOOTSTRAP_SERVERS ?? "localhost:9092")
    .split(",")
    .map((b: string) => b.trim())
    .filter((b: string) => b.length > 0);

  app.connectMicroservice<MicroserviceOptions>({
    transport: Transport.KAFKA,
    options: {
      client: {
        clientId: "messaging-service",
        brokers,
      },
      consumer: {
        groupId: process.env.KAFKA_CONSUMER_GROUP ?? "messaging-service",
        allowAutoTopicCreation: true,
      },
    },
  });

  const config = new DocumentBuilder()
    .setTitle("Messaging Service")
    .setVersion("1.0")
    .build();
  const document = SwaggerModule.createDocument(app, config);
  SwaggerModule.setup("api-docs", app, document);

  await app.startAllMicroservices();
  await app.listen(Number(process.env.PORT ?? 8094));
}
void bootstrap();
