"use client";

import { cn } from "@/lib/utils";

export type KpiItem = {
  id: string;
  label: string;
  value: string;
  hint?: string;
  tone?: "success" | "danger" | "neutral" | "info";
};

export function KpiStrip({
  items,
  selectedId,
  onSelect,
}: {
  items: KpiItem[];
  selectedId?: string | null;
  onSelect?: (id: string) => void;
}) {
  return (
    <div
      role={onSelect ? "radiogroup" : undefined}
      aria-label={onSelect ? "Metric filter" : undefined}
      className="grid grid-cols-1 divide-y divide-card-border border border-card-border bg-card sm:grid-cols-2 sm:divide-x lg:grid-cols-4 lg:divide-y-0"
    >
      {items.map((item) => {
        const selected = selectedId === item.id;
        const className = cn(
          "w-full px-4 py-3 text-left",
          onSelect && "hover:bg-overlay",
          selected && "bg-overlay"
        );
        const body = (
          <>
            <p className="font-mono text-[11px] tracking-wide text-muted-foreground uppercase">{item.label}</p>
            <p className="mt-1 font-mono text-xl tabular-nums text-foreground">{item.value}</p>
            {item.hint ? (
              <p
                className={cn(
                  "mt-1 font-mono text-[11px]",
                  item.tone === "success" && "text-accent",
                  item.tone === "danger" && "text-destructive",
                  item.tone === "info" && "text-primary",
                  (!item.tone || item.tone === "neutral") && "text-muted-foreground"
                )}
              >
                {item.hint}
              </p>
            ) : null}
          </>
        );

        if (!onSelect) {
          return (
            <div key={item.id} className={className}>
              {body}
            </div>
          );
        }

        return (
          <button
            key={item.id}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => onSelect(selected ? "" : item.id)}
            className={className}
          >
            {body}
          </button>
        );
      })}
    </div>
  );
}
