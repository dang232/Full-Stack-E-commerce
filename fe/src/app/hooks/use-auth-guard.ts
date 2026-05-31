import { useNavigate } from "react-router";
import { toast } from "sonner";

import { useAuth } from "./use-auth";

/**
 * Returns a guard function that checks authentication before executing an action.
 * If the user is not authenticated, shows a toast with a login link.
 */
export function useAuthGuard() {
  const { authenticated } = useAuth();
  const navigate = useNavigate();

  return (action: () => void) => {
    if (!authenticated) {
      toast.error("Please log in to continue", {
        action: { label: "Log in", onClick: () => navigate("/login") },
      });
      return;
    }
    action();
  };
}
