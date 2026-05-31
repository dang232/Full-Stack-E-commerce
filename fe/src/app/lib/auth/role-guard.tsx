import { type ReactNode } from "react";
import { Navigate, useLocation } from "react-router";

import { useAuth, type Role } from "../../hooks/use-auth";

interface RequireAuthProps {
  children: ReactNode;
  /** Redirect target while waiting for sign-in. Defaults to /login. */
  loginPath?: string;
}

export function RequireAuth({ children, loginPath = "/login" }: RequireAuthProps) {
  const { ready, authenticated } = useAuth();
  const location = useLocation();
  if (!ready) return null;
  if (!authenticated) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`${loginPath}?next=${next}`} replace />;
  }
  return <>{children}</>;
}

interface RequireRoleProps {
  role: Role;
  children: ReactNode;
  fallbackPath?: string;
}

export function RequireRole({ role, children, fallbackPath = "/" }: RequireRoleProps) {
  const { ready, authenticated, roles } = useAuth();
  if (!ready) return null;
  if (!authenticated) return <Navigate to="/login" replace />;
  if (!roles.includes(role)) return <Navigate to={fallbackPath} replace />;
  return <>{children}</>;
}
