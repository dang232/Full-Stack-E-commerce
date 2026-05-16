import { RouterProvider } from "react-router";
import { Toaster } from "sonner";

import { ErrorBoundary } from "./components/error-boundary";
import { VNShopProvider } from "./components/vnshop-context";
import { router } from "./routes";
import "../styles/fonts.css";

export default function App() {
  return (
    <ErrorBoundary>
      <VNShopProvider>
        <RouterProvider router={router} />
        <Toaster position="top-center" richColors expand={false} />
      </VNShopProvider>
    </ErrorBoundary>
  );
}
