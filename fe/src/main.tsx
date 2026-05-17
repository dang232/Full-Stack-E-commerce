import { QueryClientProvider } from "@tanstack/react-query";
import { createRoot } from "react-dom/client";

import App from "./app/App";
import { AuthProvider } from "./app/hooks/use-auth";
import "./app/lib/i18n";
import { queryClient } from "./app/lib/query-client";
import "./styles/index.css";

createRoot(document.getElementById("root")!).render(
  <QueryClientProvider client={queryClient}>
    <AuthProvider>
      <App />
    </AuthProvider>
  </QueryClientProvider>,
);
