"use client";

import { cn } from "@/lib/utils";
import { 
  LayoutDashboard, 
  Database, 
  ShieldAlert, 
  TrendingUp, 
  Settings,
  FlaskConical
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { motion } from "framer-motion";

const NAV_ITEMS = [
  { name: "Overview", href: "/", icon: LayoutDashboard },
  { name: "Context Cache", href: "/cache", icon: Database },
  { name: "PII Shields", href: "/pii", icon: ShieldAlert },
  { name: "Market Quota", href: "/quota", icon: TrendingUp },
  { name: "Live Test", href: "/test", icon: FlaskConical },
  { name: "Settings", href: "/settings", icon: Settings },
];

export function Sidebar() {
  const pathname = usePathname();

  return (
    <div className="w-64 h-screen glass border-r border-card-border fixed left-0 top-0 flex flex-col pt-8 pb-4 px-4 z-50">
      <div className="flex items-center gap-3 px-4 mb-10">
        <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-primary to-secondary flex items-center justify-center shadow-glow-secondary">
          <span className="font-bold text-white tracking-wider">N</span>
        </div>
        <h1 className="text-xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-white to-gray-400">
          NexusGateway
        </h1>
      </div>

      <nav className="flex-1 space-y-2">
        {NAV_ITEMS.map((item) => {
          const isActive = pathname === item.href;
          return (
            <Link key={item.name} href={item.href}>
              <div
                className={cn(
                  "relative flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium transition-all duration-300 group overflow-hidden",
                  isActive 
                    ? "text-white bg-white/5" 
                    : "text-muted-foreground hover:text-white hover:bg-white/5"
                )}
              >
                {isActive && (
                  <motion.div
                    layoutId="active-nav"
                    className="absolute inset-0 bg-primary/10 border-l-2 border-primary"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                  />
                )}
                <item.icon className={cn("w-5 h-5 z-10 transition-transform group-hover:scale-110", isActive && "text-primary")} />
                <span className="z-10">{item.name}</span>
              </div>
            </Link>
          );
        })}
      </nav>

      <div className="mt-auto pt-4 border-t border-white/10 px-4">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-full bg-muted flex items-center justify-center">
            <span className="text-xs font-semibold text-white">AD</span>
          </div>
          <div className="flex flex-col">
            <span className="text-sm font-medium text-white">Admin User</span>
            <span className="text-xs text-muted-foreground">Gateway Leader</span>
          </div>
        </div>
      </div>
    </div>
  );
}
