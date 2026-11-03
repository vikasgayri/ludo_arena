export function Skeleton({ className = '' }) {
  return <div className={`skeleton ${className}`} aria-hidden />;
}

/** variant: card | list | table | stats | page */
export default function LoadingSkeleton({ variant = 'list', rows = 4 }) {
  if (variant === 'stats') {
    return (
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" role="status" aria-label="Loading" data-testid="loading-skeleton">
        {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-24" />)}
      </div>
    );
  }
  if (variant === 'card') {
    return <div role="status" aria-label="Loading" data-testid="loading-skeleton"><Skeleton className="h-40 w-full" /></div>;
  }
  if (variant === 'page') {
    return (
      <div className="space-y-4" role="status" aria-label="Loading" data-testid="loading-skeleton">
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-28 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }
  return (
    <div className="space-y-2" role="status" aria-label="Loading" data-testid="loading-skeleton">
      {Array.from({ length: rows }).map((_, i) => <Skeleton key={i} className={variant === 'table' ? 'h-9 w-full' : 'h-16 w-full'} />)}
    </div>
  );
}
