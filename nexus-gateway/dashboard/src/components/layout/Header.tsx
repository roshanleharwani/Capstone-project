"use client";

import { Bell, Command, Menu, Search, X } from "lucide-react";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import { NAV_ITEMS } from "@/components/layout/nav";
import { useShell } from "@/components/layout/ShellContext";

const TITLES: Record<string, string> = {
  "/": "Overview",
  "/cache": "Cache",
  "/pii": "PII",
  "/quota": "Quota",
  "/test": "Console",
  "/settings": "Config",
};

const ALERTS = [
  { id: "1", title: "Prompt injection blocked", detail: "Admin · 2m ago" },
  { id: "2", title: "Tier 2 spillover", detail: "CS Dept · 9m ago" },
  { id: "3", title: "Cache hit ratio 94%", detail: "18m ago" },
];

export function Header() {
  const pathname = usePathname();
  const router = useRouter();
  const { navOpen, setNavOpen } = useShell();
  const [query, setQuery] = useState("");
  const [searchOpen, setSearchOpen] = useState(false);
  const [alertsOpen, setAlertsOpen] = useState(false);
  const searchRef = useRef<HTMLInputElement>(null);
  const title = TITLES[pathname] ?? "NexusGateway";

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return NAV_ITEMS;
    return NAV_ITEMS.filter((item) => item.name.toLowerCase().includes(q) || item.href.includes(q));
  }, [query]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setSearchOpen(true);
        requestAnimationFrame(() => searchRef.current?.focus());
      }
      if (event.key === "Escape") {
        setSearchOpen(false);
        setAlertsOpen(false);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  return (
    <header className="sticky top-0 z-40 flex h-12 items-center justify-between gap-3 border-b border-card-border bg-background px-4">
      <div className="flex min-w-0 items-center gap-3">
        <Button
          variant="ghost"
          className="h-8 w-8 min-h-8 px-0 lg:hidden"
          aria-label={navOpen ? "Close navigation" : "Open navigation"}
          aria-expanded={navOpen}
          onClick={() => setNavOpen(!navOpen)}
        >
          {navOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
        </Button>
        <p className="truncate text-sm text-muted-foreground lg:hidden">{title}</p>
      </div>

      <div className="flex items-center gap-1">
        <div className="relative hidden md:block">
          <Search className="pointer-events-none absolute top-1/2 left-2.5 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
          <input
            ref={searchRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onFocus={() => setSearchOpen(true)}
            onBlur={() => setTimeout(() => setSearchOpen(false), 120)}
            placeholder="Go to…"
            aria-label="Go to page"
            className="h-8 w-52 rounded-md border border-card-border bg-input py-1 pr-10 pl-8 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:border-primary lg:w-64"
          />
          <kbd className="pointer-events-none absolute top-1/2 right-1.5 hidden -translate-y-1/2 rounded-sm border border-card-border px-1 text-[10px] text-muted-foreground sm:inline-flex">
            <Command className="h-3 w-3" />K
          </kbd>
          {searchOpen ? (
            <div className="absolute top-9 right-0 z-50 w-full overflow-hidden rounded-md border border-card-border bg-card shadow-sm">
              {results.map((item) => (
                <button
                  key={item.href}
                  type="button"
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-muted-foreground hover:bg-overlay hover:text-foreground"
                  onMouseDown={(event) => {
                    event.preventDefault();
                    router.push(item.href);
                    setQuery("");
                    setSearchOpen(false);
                  }}
                >
                  {item.name}
                </button>
              ))}
            </div>
          ) : null}
        </div>

        <ThemeToggle />

        <div className="relative">
          <Button
            variant="ghost"
            className="relative h-8 w-8 min-h-8 px-0"
            aria-label="Notifications"
            aria-expanded={alertsOpen}
            onClick={() => setAlertsOpen((open) => !open)}
          >
            <Bell className="h-4 w-4" />
            <span className="absolute top-1.5 right-1.5 h-1.5 w-1.5 rounded-full bg-destructive" />
          </Button>
          {alertsOpen ? (
            <div className="absolute top-9 right-0 z-50 w-72 rounded-md border border-card-border bg-card p-1 shadow-sm">
              <p className="px-2 py-1.5 text-[11px] text-muted-foreground">Alerts</p>
              {ALERTS.map((alert) => (
                <div key={alert.id} className="rounded-md px-2 py-1.5 hover:bg-overlay">
                  <p className="text-sm text-foreground">{alert.title}</p>
                  <p className="text-xs text-muted-foreground">{alert.detail}</p>
                </div>
              ))}
            </div>
          ) : null}
        </div>
      </div>
    </header>
  );
}
