export default function Loading() {
  return (
    <div className="space-y-4" aria-busy="true" aria-live="polite">
      <div className="h-8 w-64 animate-pulse bg-overlay" />
      <div className="h-20 animate-pulse border border-card-border bg-overlay" />
      <div className="h-80 animate-pulse border border-card-border bg-overlay" />
    </div>
  );
}
