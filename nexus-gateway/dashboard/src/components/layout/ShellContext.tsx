"use client";

import { createContext, useContext } from "react";

type ShellContextValue = {
  navOpen: boolean;
  setNavOpen: (open: boolean) => void;
};

export const ShellContext = createContext<ShellContextValue | null>(null);

export function useShell() {
  const ctx = useContext(ShellContext);
  if (!ctx) throw new Error("useShell must be used inside AppShell");
  return ctx;
}
