import { cn } from "@/lib/utils";

type Tone = "neutral" | "success" | "danger" | "warning" | "info";

const tones: Record<Tone, string> = {
  neutral: "bg-muted text-muted-foreground border-card-border",
  success: "text-accent border-card-border",
  danger: "text-destructive border-card-border",
  warning: "text-warning border-card-border",
  info: "text-primary border-card-border",
};

export function Badge({
  tone = "neutral",
  className,
  ...props
}: React.HTMLAttributes<HTMLSpanElement> & { tone?: Tone }) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-sm border px-1.5 py-0.5 text-[11px] font-medium whitespace-nowrap",
        tones[tone],
        className
      )}
      {...props}
    />
  );
}
