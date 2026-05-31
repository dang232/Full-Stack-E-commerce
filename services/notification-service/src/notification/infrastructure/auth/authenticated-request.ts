import type { Request } from 'express';
import type { JwtUser } from './jwt.strategy';

/**
 * Express request after the passport-jwt strategy has hydrated `req.user` with
 * the decoded JWT payload. Controllers should use `req.user.sub` as the
 * authenticated subject identifier instead of trusting any client-supplied
 * header.
 */
export type AuthenticatedRequest = Request & { user: JwtUser };
