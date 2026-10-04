"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { 
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  BarChart, Bar, Legend
} from "recharts";
import { DollarSign, Zap, Shield, Wallet } from "lucide-react";
import { motion } from "framer-motion";
import { useState, useEffect } from "react";

const initialRouterData = [
  { time: "08:00", tier1: 0, tier2: 0 },
  { time: "09:00", tier1: 0, tier2: 0 },
  { time: "10:00", tier1: 0, tier2: 0 },
  { time: "11:00", tier1: 0, tier2: 0 },
  { time: "12:00", tier1: 0, tier2: 0 },
  { time: "13:00", tier1: 0, tier2: 0 },
  { time: "14:00", tier1: 0, tier2: 0 },
];

const initialCompressionData = [
  { name: "CS Dept", original: 100, compressed: 0 },
  { name: "Bio Dept", original: 100, compressed: 0 },
  { name: "Admin", original: 100, compressed: 0 },
  { name: "Eng Dept", original: 100, compressed: 0 },
];

const initialMetrics = [
  { title: "Total Cost Savings", value: "$0.00", change: "+0%", positive: true },
  { title: "Cache Hit Ratio", value: "0%", change: "+0%", positive: true },
  { title: "PII Intercepts", value: "0", change: "-0%", positive: false },
  { title: "Market Quota Traded", value: "0M", change: "+0%", positive: true },
];

const ICONS = [
  { icon: DollarSign, color: "text-accent" },
  { icon: Zap, color: "text-primary" },
  { icon: Shield, color: "text-destructive" },
  { icon: Wallet, color: "text-secondary" },
];

export default function Dashboard() {
  const [data, setData] = useState({
    routerData: initialRouterData,
    compressionData: initialCompressionData,
    metrics: initialMetrics,
    activityFeed: []
  });

  useEffect(() => {
    const fetchData = async () => {
      try {
        const res = await fetch('/api/v1/dashboard/metrics');
        if (res.ok) {
          const json = await res.json();
          setData(json);
        }
      } catch (err) {
        console.error("Failed to fetch dashboard metrics", err);
      }
    };
    
    fetchData(); // initial fetch
    const interval = setInterval(fetchData, 5000); // poll every 5s
    return () => clearInterval(interval);
  }, []);

  // Merge static icons with dynamic metrics
  const mergedMetrics = data.metrics.map((m, i) => ({
    ...m,
    ...ICONS[i]
  }));

  return (
    <div className="space-y-8 pb-10">
      
      {/* Top Metrics Row */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {mergedMetrics.map((metric, i) => (
          <Card key={i} className="relative overflow-hidden group">
            <div className="absolute top-0 right-0 p-4 opacity-10 transform translate-x-4 -translate-y-4 group-hover:scale-110 group-hover:opacity-20 transition-all duration-500">
              <metric.icon className={`w-24 h-24 ${metric.color}`} />
            </div>
            
            <CardHeader className="mb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground flex items-center justify-between">
                {metric.title}
                <metric.icon className={`w-4 h-4 ${metric.color}`} />
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold text-white mb-2 tracking-tight">
                {metric.value}
              </div>
              <p className={`text-xs font-semibold ${metric.positive ? "text-accent" : "text-destructive"}`}>
                {metric.change} <span className="text-muted-foreground font-normal ml-1">from last month</span>
              </p>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Main Routing Chart */}
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Routing Distribution (Tier 1 vs Tier 2)</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="h-[350px] w-full mt-4">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={data.routerData} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="colorTier1" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.4}/>
                      <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/>
                    </linearGradient>
                    <linearGradient id="colorTier2" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#8b5cf6" stopOpacity={0.4}/>
                      <stop offset="95%" stopColor="#8b5cf6" stopOpacity={0}/>
                    </linearGradient>
                  </defs>
                  <XAxis dataKey="time" stroke="#475569" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis stroke="#475569" fontSize={12} tickLine={false} axisLine={false} tickFormatter={(value) => `${value / 1000}k`} />
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
                  <Tooltip 
                    contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '8px' }}
                    itemStyle={{ color: '#e2e8f0' }}
                  />
                  <Area type="monotone" dataKey="tier1" name="Tier 1 (Bedrock)" stroke="#3b82f6" strokeWidth={3} fillOpacity={1} fill="url(#colorTier1)" />
                  <Area type="monotone" dataKey="tier2" name="Tier 2 (OpenAI)" stroke="#8b5cf6" strokeWidth={3} fillOpacity={1} fill="url(#colorTier2)" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        {/* Compression Ratio Chart */}
        <Card>
          <CardHeader>
            <CardTitle>Compression Ratios by Tenant</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="h-[350px] w-full mt-4">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={data.compressionData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
                  <XAxis dataKey="name" stroke="#475569" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis stroke="#475569" fontSize={12} tickLine={false} axisLine={false} />
                  <Tooltip 
                    cursor={{fill: '#1e293b', opacity: 0.4}}
                    contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '8px' }}
                  />
                  <Legend wrapperStyle={{ fontSize: '12px', paddingTop: '10px' }} />
                  <Bar dataKey="original" name="Original Context" fill="#334155" radius={[4, 4, 0, 0]} />
                  <Bar dataKey="compressed" name="Compressed Payload" fill="#10b981" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Activity Feed */}
      <Card>
        <CardHeader>
          <CardTitle>Real-Time Activity Feed</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-4 mt-4">
            {data.activityFeed.length > 0 ? data.activityFeed.map((log: any, i: number) => (
              <motion.div 
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: i * 0.1 + 0.4 }}
                key={i} 
                className="flex items-center justify-between p-4 rounded-lg bg-black/20 border border-white/5 hover:bg-white/5 transition-colors"
              >
                <div className="flex items-center gap-4">
                  <div className={`w-2 h-2 rounded-full ${
                    log.type === 'warning' ? 'bg-yellow-500 shadow-[0_0_10px_rgba(234,179,8,0.5)]' :
                    log.type === 'danger' ? 'bg-destructive shadow-glow-destructive' :
                    log.type === 'success' ? 'bg-accent shadow-glow-accent' :
                    'bg-primary shadow-glow-primary'
                  }`} />
                  <span className="text-sm text-foreground/90">{log.event}</span>
                </div>
                <span className="text-xs text-muted-foreground whitespace-nowrap">{log.time}</span>
              </motion.div>
            )) : <div className="text-sm text-muted-foreground">Waiting for events...</div>}
          </div>
        </CardContent>
      </Card>
      
    </div>
  );
}
