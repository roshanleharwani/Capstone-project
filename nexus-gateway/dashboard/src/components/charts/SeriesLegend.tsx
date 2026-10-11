"use client";

import { cn } from "@/lib/utils";

export type SeriesOption = {
  id: string;
  label: string;
  color: string;
  dashed?: boolean;
};

export function SeriesLegend({
  series,
  hidden,
  onToggle,
}: {
  series: SeriesOption[];
  hidden: Record<string, boolean>;
  onToggle: (id: string) => void;
}) {
  return (
    <div className="mb-2 flex flex-wrap gap-1" role="group" aria-label="Toggle series">
      {series.map((item) => {
        const off = hidden[item.id];
        return (
          <button
            key={item.id}
            type="button"
            aria-pressed={!off}
            onClick={() => onToggle(item.id)}
            className={cn(
              "inline-flex h-6 items-center gap-1.5 border border-card-border px-2 font-mono text-[11px] uppercase",
              off ? "text-muted-foreground" : "bg-overlay text-foreground"
            )}
          >
            <span
              aria-hidden="true"
              className="inline-block h-px w-3"
              style={{
                background: off ? "var(--muted-foreground)" : item.color,
                borderTop: item.dashed ? `1px dashed ${off ? "var(--muted-foreground)" : item.color}` : undefined,
                height: item.dashed ? 0 : 2,
              }}
            />
            {item.label}
          </button>
        );
      })}
    </div>
  );
}
