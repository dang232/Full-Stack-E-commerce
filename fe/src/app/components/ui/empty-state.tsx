import type { ReactNode } from "react";

interface EmptyStateProps {
  icon: ReactNode;
  title: string;
  description: string;
  action?: { label: string; onClick: () => void; variant?: "primary" | "ghost" };
}

export function EmptyState({ icon, title, description, action }: EmptyStateProps) {
  return (
    <div
      className="flex flex-col items-center justify-center py-16 px-6 bg-card border border-border rounded-[var(--radius-xl)] text-center"
      role="status"
      aria-label={title}
    >
      <div
        className="w-16 h-16 rounded-full bg-surface-elevated flex items-center justify-center mb-4 shrink-0"
        aria-hidden="true"
      >
        <span className="text-muted-foreground [&>svg]:w-7 [&>svg]:h-7">{icon}</span>
      </div>

      <h3 className="text-lg font-semibold text-foreground mb-2">{title}</h3>

      <p className="text-sm text-text-secondary max-w-[320px] mb-6">{description}</p>

      {action ? (
        action.variant === "ghost" ? (
          <button
            type="button"
            onClick={action.onClick}
            className="px-5 py-2.5 rounded-[var(--radius-lg)] text-sm font-medium border border-border bg-transparent text-foreground hover:bg-background transition-colors"
          >
            {action.label}
          </button>
        ) : (
          <button
            type="button"
            onClick={action.onClick}
            className="px-5 py-2.5 rounded-[var(--radius-lg)] text-sm font-medium bg-primary text-white hover:opacity-90 transition-opacity"
          >
            {action.label}
          </button>
        )
      ) : null}
    </div>
  );
}
