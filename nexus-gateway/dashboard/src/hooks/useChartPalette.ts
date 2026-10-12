"use client";

import { useTheme } from "@/components/theme/ThemeProvider";

export function useChartPalette() {
  const { theme } = useTheme();
  const light = theme === "light";

  return {
    axis: light ? "#475569" : "#64748b",
    grid: light ? "#e2e8f0" : "#1e293b",
    cursor: light ? "#cbd5e1" : "#1e293b",
  };
}
