import { MikroOrmModule } from '@mikro-orm/nestjs';
import { Module } from '@nestjs/common';
import { AppController } from './app.controller';
import mikroOrmConfig from './mikro-orm.config';
import { NotificationModule } from './notification/notification.module';

@Module({
  imports: [MikroOrmModule.forRoot(mikroOrmConfig), NotificationModule],
  controllers: [AppController],
})
export class AppModule {}
