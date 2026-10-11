import { cn } from "@/lib/utils";

export function StatusCell({
  label,
  tone = "neutral",
}: {
  label: string;
  tone?: "success" | "danger" | "warning" | "info" | "neutral";
}) {
  return (
    <span className="inline-flex items-center gap-1.5 font-mono text-[11px] uppercase">
      <span
        className={cn(
          "inline-block h-1.5 w-1.5",
          tone === "success" && "bg-accent",
          tone === "danger" && "bg-destructive",
          tone === "warning" && "bg-warning",
          tone === "info" && "bg-primary",
          tone === "neutral" && "bg-muted-foreground"
        )}
        aria-hidden="true"
      />
      {label}
    </span>
  );
}
