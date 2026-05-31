import { MikroOrmModule } from "@mikro-orm/nestjs";
import { Module } from "@nestjs/common";
import { AppController } from "./app.controller";
import mikroOrmConfig from "./mikro-orm.config";
import { MessagingModule } from "./messaging/messaging.module";

@Module({
  imports: [MikroOrmModule.forRoot(mikroOrmConfig), MessagingModule],
  controllers: [AppController],
})
export class AppModule {}
