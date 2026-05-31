import { Controller, Get } from '@nestjs/common';
import { ConfigurationService } from './configuration.service.js';
import { AppConfigDto } from './dto/app-config.dto.js';

@Controller('api')
export class ConfigurationController {
  constructor(private readonly configurationService: ConfigurationService) {}

  @Get('config')
  getConfig(): AppConfigDto {
    return this.configurationService.getConfig();
  }
}
