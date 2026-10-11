"use client";

import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import { useId, useState } from "react";

export function Panel({
  title,
  meta,
  actions,
  children,
  className,
  collapsible = true,
  defaultOpen = true,
}: {
  title: string;
  meta?: string;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  collapsible?: boolean;
  defaultOpen?: boolean;
}) {
  const [open, setOpen] = useState(defaultOpen);
  const panelId = useId();

  return (
    <section className={cn("flex min-h-0 flex-col overflow-hidden border border-card-border bg-card", className)}>
      <header className="flex h-8 shrink-0 items-center justify-between gap-3 border-b border-card-border bg-muted/60 px-2">
        <div className="flex min-w-0 items-center gap-2">
          {collapsible ? (
            <button
              type="button"
              aria-expanded={open}
              aria-controls={panelId}
              onClick={() => setOpen((value) => !value)}
              className="flex min-w-0 items-baseline gap-2 px-1 py-0.5 hover:bg-overlay"
            >
              <span aria-hidden="true" className="font-mono text-[10px] text-muted-foreground">
                {open ? "−" : "+"}
              </span>
              <h2 className="truncate font-mono text-[11px] font-medium tracking-wide text-foreground uppercase">
                {title}
              </h2>
            </button>
          ) : (
            <h2 className="truncate px-1 font-mono text-[11px] font-medium tracking-wide text-foreground uppercase">
              {title}
            </h2>
          )}
          {meta ? <span className="truncate font-mono text-[11px] text-muted-foreground">{meta}</span> : null}
        </div>
        {actions ? <div className="flex shrink-0 items-center gap-2">{actions}</div> : null}
      </header>
      {open ? (
        <div id={panelId} className="min-h-0 flex-1 p-3">
          {children}
        </div>
      ) : (
        <p id={panelId} className="px-3 py-1.5 font-mono text-[11px] text-muted-foreground">
          collapsed
        </p>
      )}
    </section>
  );
}
