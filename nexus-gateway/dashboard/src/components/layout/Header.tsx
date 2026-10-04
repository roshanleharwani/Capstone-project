"use client";

import { Bell, Activity } from "lucide-react";
import { motion } from "framer-motion";
import { usePathname } from "next/navigation";

export function Header() {
  const pathname = usePathname();
  
  let title = "Overview";
  if (pathname === "/test") title = "Live Testing Pipeline";
  if (pathname === "/settings") title = "Gateway Configuration";
  
  return (
    <header className="h-20 glass border-b border-card-border sticky top-0 z-40 flex items-center justify-between px-8">
      <div className="flex items-center gap-4">
        <h2 className="text-2xl font-semibold text-white tracking-tight">{title}</h2>
        <div className="flex items-center gap-2 px-3 py-1 rounded-full bg-accent/10 border border-accent/20">
          <motion.div 
            animate={{ scale: [1, 1.2, 1] }}
            transition={{ repeat: Infinity, duration: 2 }}
            className="w-2 h-2 rounded-full bg-accent shadow-glow-accent"
          />
          <span className="text-xs font-medium text-accent">System Optimal</span>
        </div>
      </div>

      <div className="flex items-center gap-6">
        <div className="relative">
          <select 
            disabled
            className="appearance-none bg-black/20 border border-white/10 text-white/50 text-sm rounded-lg pl-4 pr-10 py-2 outline-none cursor-not-allowed"
          >
            <option value="all">Global View (All Tenants)</option>
          </select>
          <div className="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none text-muted-foreground/50">
            <Activity className="w-4 h-4" />
          </div>
        </div>

        <button className="relative p-2 text-muted-foreground hover:text-white transition-colors">
          <Bell className="w-5 h-5" />
          <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-destructive shadow-glow-destructive" />
        </button>
      </div>
    </header>
  );
}
