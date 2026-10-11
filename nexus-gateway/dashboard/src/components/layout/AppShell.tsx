"use client";

import { useEffect, useMemo, useState } from "react";
import { Sidebar } from "@/components/layout/Sidebar";
import { Header } from "@/components/layout/Header";
import { ShellContext } from "@/components/layout/ShellContext";

export function AppShell({ children }: { children: React.ReactNode }) {
  const [navOpen, setNavOpen] = useState(false);
  const value = useMemo(() => ({ navOpen, setNavOpen }), [navOpen]);

  useEffect(() => {
    document.body.style.overflow = navOpen ? "hidden" : "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [navOpen]);

  return (
    <ShellContext.Provider value={value}>
      <a href="#main-content" className="skip-link">
        Skip to content
      </a>
      <Sidebar />
      {navOpen ? (
        <button
          type="button"
          aria-label="Close navigation"
          className="fixed inset-0 z-40 bg-black/50 lg:hidden"
          onClick={() => setNavOpen(false)}
        />
      ) : null}
      <div className="flex min-h-screen flex-1 flex-col lg:ml-56">
        <Header />
        <main id="main-content" className="flex-1 overflow-y-auto p-4 lg:p-6">
          {children}
        </main>
      </div>
    </ShellContext.Provider>
  );
}
