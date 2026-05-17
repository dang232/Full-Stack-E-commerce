import { Component, type ErrorInfo, type ReactNode } from "react";

import { ApiError } from "../lib/api";

interface Props {
  children: ReactNode;
  fallback?: (error: unknown, reset: () => void) => ReactNode;
}

interface State {
  error: unknown;
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: unknown): State {
    return { error };
  }

  componentDidCatch(error: unknown, info: ErrorInfo): void {
    console.error("[ErrorBoundary]", error, info.componentStack);
  }

  reset = () => this.setState({ error: null });

  render() {
    if (!this.state.error) return this.props.children;
    if (this.props.fallback) return this.props.fallback(this.state.error, this.reset);

    const apiError = this.state.error instanceof ApiError ? this.state.error : null;
    const message =
      apiError?.message ??
      (this.state.error instanceof Error
        ? this.state.error.message
        : "Đã xảy ra lỗi không xác định.");

    return (
      <div className="min-h-[60vh] flex items-center justify-center px-4">
        <div className="max-w-md w-full bg-white border border-gray-200 rounded-2xl shadow-sm p-6 text-center">
          <h2 className="text-lg font-bold text-gray-800 mb-2">Có lỗi xảy ra</h2>
          <p className="text-sm text-gray-600 mb-4">{message}</p>
          {apiError?.correlationId ? (
            <p className="text-xs text-gray-400 mb-4">
              Mã hỗ trợ: <span className="font-mono">{apiError.correlationId}</span>
            </p>
          ) : null}
          <button
            type="button"
            onClick={this.reset}
            className="px-4 py-2 rounded-xl text-white text-sm font-semibold"
            style={{ background: "#00BFB3" }}
          >
            Thử lại
          </button>
        </div>
      </div>
    );
  }
}
