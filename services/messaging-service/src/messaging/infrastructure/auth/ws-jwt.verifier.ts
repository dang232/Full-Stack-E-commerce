import { Injectable, Logger } from "@nestjs/common";
import * as jwt from "jsonwebtoken";
import { JwksClient } from "jwks-rsa";

/**
 * Validates a JWT for the WebSocket handshake. Lives outside passport-jwt
 * because `ws` upgrades aren't Express requests — we need to verify the token
 * passed via the `?token=` query parameter (the SPA puts the Bearer token there
 * because browsers can't set custom headers on WebSocket constructors) before
 * accepting the upgrade.
 */
@Injectable()
export class WsJwtVerifier {
  private static readonly logger = new Logger(WsJwtVerifier.name);
  private readonly jwks: JwksClient;
  private readonly issuer: string;

  constructor() {
    this.issuer =
      process.env.KEYCLOAK_ISSUER_URI ?? "http://localhost:9090/realms/vnshop";
    const jwksUri =
      process.env.KEYCLOAK_JWK_SET_URI ??
      "http://keycloak:8080/realms/vnshop/protocol/openid-connect/certs";
    this.jwks = new JwksClient({
      jwksUri,
      cache: true,
      rateLimit: true,
      jwksRequestsPerMinute: 10,
    });
  }

  async verify(token: string): Promise<{ sub: string } & jwt.JwtPayload> {
    return new Promise((resolve, reject) => {
      jwt.verify(
        token,
        (header, callback) => {
          if (!header.kid) {
            callback(new Error("Missing kid"));
            return;
          }
          this.jwks
            .getSigningKey(header.kid)
            .then((key) => callback(null, key.getPublicKey()))
            .catch((err: unknown) =>
              callback(err instanceof Error ? err : new Error("JWKS failure")),
            );
        },
        { algorithms: ["RS256"], issuer: this.issuer },
        (err, decoded) => {
          if (err || !decoded || typeof decoded === "string") {
            WsJwtVerifier.logger.warn(
              `WebSocket JWT rejected: ${err?.message ?? "malformed payload"}`,
            );
            reject(err ?? new Error("Malformed token"));
            return;
          }
          if (typeof decoded.sub !== "string") {
            reject(new Error("Token missing sub claim"));
            return;
          }
          resolve(decoded as { sub: string } & jwt.JwtPayload);
        },
      );
    });
  }
}
