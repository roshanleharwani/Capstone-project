import type { ReactNode } from "react";

export function PropertyRow({
  label,
  htmlFor,
  hint,
  children,
}: {
  label: string;
  htmlFor?: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <div className="grid grid-cols-1 gap-2 border-b border-card-border py-2.5 last:border-b-0 sm:grid-cols-[220px_1fr] sm:items-center">
      <div>
        <label htmlFor={htmlFor} className="font-mono text-[11px] tracking-wide text-muted-foreground uppercase">
          {label}
        </label>
        {hint ? <p className="mt-0.5 text-xs text-muted-foreground">{hint}</p> : null}
      </div>
      <div>{children}</div>
    </div>
  );
}
