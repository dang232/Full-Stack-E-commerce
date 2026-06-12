import { CanActivate, ExecutionContext, ForbiddenException, Injectable } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { ROLES_KEY } from '../decorators/roles.decorator.js';

/**
 * Checks the x-user-roles header injected by the api-gateway.
 *
 * TODO: The gateway (UserIdHeaderFilter) currently only forwards x-user-id.
 * A companion filter must be added to extract realm_access.roles from the JWT
 * and forward them as a comma-separated x-user-roles header. Until then, any
 * endpoint decorated with @Roles() will reject all requests with 403.
 */
@Injectable()
export class RolesGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const requiredRoles = this.reflector.getAllAndOverride<string[]>(ROLES_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (!requiredRoles || requiredRoles.length === 0) return true;

    const request = context.switchToHttp().getRequest();
    const rolesHeader = request.headers['x-user-roles'];
    if (!rolesHeader) throw new ForbiddenException('Missing roles');

    const userRoles = typeof rolesHeader === 'string' ? rolesHeader.split(',') : [];
    const hasRole = requiredRoles.some((role) => userRoles.includes(role));
    if (!hasRole) throw new ForbiddenException('Insufficient role');
    return true;
  }
}
