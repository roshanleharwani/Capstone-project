import { Card, CardContent } from "@/components/ui/Card";
import { cn } from "@/lib/utils";
import type { LucideIcon } from "lucide-react";

type MetricCardProps = {
  title: string;
  value: string;
  hint?: string;
  tone?: "success" | "danger" | "neutral" | "info";
  icon: LucideIcon;
  delay?: number;
};

const iconTone = {
  success: "text-accent",
  danger: "text-destructive",
  info: "text-primary",
  neutral: "text-muted-foreground",
};

export function MetricCard({
  title,
  value,
  hint,
  tone = "neutral",
  icon: Icon,
}: MetricCardProps) {
  return (
    <Card>
      <CardContent>
        <div className="mb-3 flex items-center justify-between gap-2">
          <p className="text-xs text-muted-foreground">{title}</p>
          <Icon className={cn("h-3.5 w-3.5", iconTone[tone])} aria-hidden="true" />
        </div>
        <div className="font-mono text-2xl font-medium tabular-nums text-foreground">{value}</div>
        {hint ? (
          <p
            className={cn(
              "mt-1.5 text-xs",
              tone === "success" && "text-accent",
              tone === "danger" && "text-destructive",
              tone === "info" && "text-primary",
              tone === "neutral" && "text-muted-foreground"
            )}
          >
            {hint}
          </p>
        ) : null}
      </CardContent>
    </Card>
  );
}
