import { Controller, Get } from "@nestjs/common";
import { ApiResponse } from "./messaging/infrastructure/api-response";

@Controller()
export class AppController {
  @Get("health")
  health(): ApiResponse<{ status: string }> {
    return ApiResponse.ok({ status: "ok" });
  }
}
