"use client";

import { cn } from "@/lib/utils";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useShell } from "@/components/layout/ShellContext";
import { NAV_GROUPS } from "@/components/layout/nav";

export function Sidebar() {
  const pathname = usePathname();
  const { navOpen, setNavOpen } = useShell();

  return (
    <aside
      className={cn(
        "fixed top-0 left-0 z-50 flex h-screen w-56 flex-col border-r border-card-border bg-background px-2 py-3 transition-transform duration-150",
        navOpen ? "translate-x-0" : "-translate-x-full lg:translate-x-0"
      )}
    >
      <div className="mb-4 border-b border-card-border px-2 pb-3">
        <p className="font-mono text-xs text-foreground">NEXUS-GW</p>
        <p className="font-mono text-[11px] text-muted-foreground">cluster · ap-south-1</p>
      </div>

      <nav className="flex-1 space-y-4" aria-label="Primary">
        {NAV_GROUPS.map((group) => (
          <div key={group.label}>
            <p className="mb-1 px-2 font-mono text-[10px] tracking-wide text-muted-foreground uppercase">
              {group.label}
            </p>
            <div className="space-y-0.5">
              {group.items.map((item) => {
                const isActive = pathname === item.href;
                return (
                  <Link
                    key={item.name}
                    href={item.href}
                    aria-current={isActive ? "page" : undefined}
                    onClick={() => setNavOpen(false)}
                    className={cn(
                      "flex items-center gap-2 border-l-2 px-2 py-1.5 text-sm",
                      isActive
                        ? "border-foreground bg-muted text-foreground"
                        : "border-transparent text-muted-foreground hover:bg-overlay hover:text-foreground"
                    )}
                  >
                    <span>{item.name}</span>
                  </Link>
                );
              })}
            </div>
          </div>
        ))}
      </nav>

      <div className="mt-auto border-t border-card-border px-2 pt-3 font-mono text-[11px] text-muted-foreground">
        <p className="text-foreground">admin@local</p>
        <p>session 8h remaining</p>
      </div>
    </aside>
  );
}
