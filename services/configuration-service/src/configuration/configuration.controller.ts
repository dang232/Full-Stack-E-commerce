import { Controller, Get, Param, Post } from '@nestjs/common';
import { ConfigurationService } from './configuration.service.js';
import { AppConfigDto } from './dto/app-config.dto.js';

@Controller('api')
export class ConfigurationController {
  constructor(private readonly configurationService: ConfigurationService) {}

  @Get('config')
  getConfig(): AppConfigDto {
    return this.configurationService.getConfig();
  }

  @Get('config/services')
  getAllServiceConfigs(): Record<string, Record<string, unknown>> {
    return this.configurationService.getAllServiceConfigs();
  }

  @Get('config/services/:serviceName')
  getServiceConfig(@Param('serviceName') serviceName: string): Record<string, unknown> {
    return this.configurationService.getServiceConfig(serviceName);
  }

  @Get('config/global')
  getGlobalConfig(): Record<string, unknown> {
    return this.configurationService.getGlobalConfig();
  }

  @Post('config/reload')
  reloadConfigs(): { status: string } {
    this.configurationService.reloadConfigs();
    return { status: 'reloaded' };
  }
}
