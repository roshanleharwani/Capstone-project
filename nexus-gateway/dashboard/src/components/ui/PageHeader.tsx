import type { ComponentType, ReactNode } from "react";

export function PageHeader({
  title,
  crumbs,
  actions,
}: {
  icon?: ComponentType<{ className?: string }>;
  title: string;
  description?: string;
  crumbs?: string[];
  actions?: ReactNode;
}) {
  const trail = crumbs ?? ["prod", "nexus-gw", title];

  return (
    <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <nav aria-label="Breadcrumb" className="font-mono text-[11px] text-muted-foreground">
          {trail.map((crumb, index) => (
            <span key={`${crumb}-${index}`}>
              {index > 0 ? <span className="mx-1.5 text-card-border">/</span> : null}
              <span className={index === trail.length - 1 ? "text-foreground" : undefined}>{crumb}</span>
            </span>
          ))}
        </nav>
        <h1 className="mt-1 text-base font-medium text-foreground">{title}</h1>
      </div>
      {actions}
    </div>
  );
}
