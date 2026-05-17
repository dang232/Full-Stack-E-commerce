import { Injectable } from "@nestjs/common";
import { AuthGuard } from "@nestjs/passport";

/**
 * Guards REST controllers. WebSocket auth lives in `MessagingWsGateway` because
 * passport-jwt is request-scoped and `ws` connections aren't Express requests.
 */
@Injectable()
export class JwtAuthGuard extends AuthGuard("jwt") {}
