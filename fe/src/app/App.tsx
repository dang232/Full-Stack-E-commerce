import { RouterProvider } from "react-router";
import { Toaster } from "sonner";

import { ErrorBoundary } from "./components/error-boundary";
import { VNShopProvider } from "./components/vnshop-context";
import { useMessagingSocket } from "./hooks/use-messaging-socket";
import { router } from "./routes";
import "../styles/fonts.css";

/**
 * Mounts background hooks that need access to the auth + query providers but
 * don't render anything. Lives inside `VNShopProvider` so it sees the same
 * QueryClient + AuthProvider as the routed pages.
 */
function BackgroundEffects() {
  useMessagingSocket();
  return null;
}

export default function App() {
  return (
    <ErrorBoundary>
      <VNShopProvider>
        <BackgroundEffects />
        <RouterProvider router={router} />
        <Toaster position="top-center" richColors expand={false} />
      </VNShopProvider>
    </ErrorBoundary>
  );
}
