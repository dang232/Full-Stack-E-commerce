export function PageSkeleton() {
  return (
    <div className="animate-pulse space-y-4 p-6" aria-busy="true" aria-label="Loading content">
      <div className="h-8 bg-muted rounded w-1/3" />
      <div className="h-4 bg-muted rounded w-2/3" />
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-6">
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} className="h-48 bg-muted rounded" />
        ))}
      </div>
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
