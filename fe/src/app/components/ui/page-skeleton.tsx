export function PageSkeleton() {
  return (
    <div
      className="grid grid-cols-4 gap-4 p-6"
      aria-busy="true"
      aria-label="Loading content"
    >
      {(["a", "b", "c", "d"] as const).map((id) => (
        <div
          key={id}
          className="bg-card border border-border rounded-[var(--radius-lg)] p-3"
        >
          <div className="aspect-square rounded-[var(--radius-md)] bg-surface-elevated animate-pulse mb-3" />
          <div className="h-3 rounded-md bg-surface-elevated animate-pulse mb-2 w-3/4" />
          <div className="h-3 rounded-md bg-surface-elevated animate-pulse mb-2 w-1/2" />
          <div className="h-3 rounded-md bg-surface-elevated animate-pulse w-[30%]" />
        </div>
      ))}
    </div>
  );
}

export function ProductDetailSkeleton() {
  return (
    <div
      className="animate-pulse p-6 grid grid-cols-1 md:grid-cols-2 gap-8"
      aria-busy="true"
      aria-label="Loading product"
    >
      <div className="h-96 bg-muted rounded" />
      <div className="space-y-4">
        <div className="h-8 bg-muted rounded w-3/4" />
        <div className="h-6 bg-muted rounded w-1/4" />
        <div className="h-4 bg-muted rounded w-full" />
        <div className="h-4 bg-muted rounded w-5/6" />
        <div className="h-12 bg-muted rounded w-1/3 mt-8" />
      </div>
    </div>
  );
}
