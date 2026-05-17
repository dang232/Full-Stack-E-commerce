import { Injectable } from '@nestjs/common';
import { AuthGuard } from '@nestjs/passport';

/**
 * Wraps the passport-jwt strategy in a Nest guard so controllers can be
 * protected with `@UseGuards(JwtAuthGuard)`. Validation logic lives in
 * `JwtStrategy`; this is the binding to Nest's request lifecycle.
 */
@Injectable()
export class JwtAuthGuard extends AuthGuard('jwt') {}
