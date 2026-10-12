"use client";

import { Button } from "@/components/ui/Button";
import { Pause, Play, RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";

type LiveControlsProps = {
  paused: boolean;
  onToggle: () => void;
  onRefresh: () => void;
  updatedAt: Date | null;
  status: "loading" | "ok" | "error";
};

export function LiveControls({
  paused,
  onToggle,
  onRefresh,
  updatedAt,
  status,
}: LiveControlsProps) {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    setReady(true);
  }, []);

  const time =
    ready && updatedAt
      ? updatedAt.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" })
      : "—";

  const label = status === "error" ? "Offline" : paused ? "Paused" : "Live";

  return (
    <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
      <span aria-live="polite">
        {label}
        <span className="mx-1.5 text-card-border">·</span>
        {time}
      </span>
      <Button variant="outline" className="h-7 min-h-7 px-2 text-xs" onClick={onToggle} aria-pressed={paused}>
        {paused ? <Play className="h-3 w-3" aria-hidden="true" /> : <Pause className="h-3 w-3" aria-hidden="true" />}
        {paused ? "Resume" : "Pause"}
      </Button>
      <Button variant="ghost" className="h-7 min-h-7 px-2 text-xs" onClick={onRefresh}>
        <RefreshCw className="h-3 w-3" aria-hidden="true" />
        Refresh
      </Button>
    </div>
  );
}
