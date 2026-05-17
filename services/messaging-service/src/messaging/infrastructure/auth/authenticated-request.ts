import type { Request } from "express";
import type { JwtUser } from "./jwt.strategy";

/**
 * Express request after the passport-jwt strategy has hydrated `req.user` with
 * the decoded JWT payload. Controllers must use `req.user.sub` as the
 * authenticated subject identifier instead of trusting any client-supplied
 * header — same convention as notification-service.
 */
export type AuthenticatedRequest = Request & { user: JwtUser };
