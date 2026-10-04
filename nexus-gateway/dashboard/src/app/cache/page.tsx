"use client";
import { useState, useEffect } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { 
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, Legend
} from "recharts";
import { Database, Zap, HardDrive, Cpu } from "lucide-react";
import { motion } from "framer-motion";

const COLORS = ['#3b82f6', '#8b5cf6', '#ef4444'];

export default function CacheAnalytics() {
  const [cacheHistory, setCacheHistory] = useState<any[]>([]);
  const [evictionData, setEvictionData] = useState<any[]>([]);
  const [totalHits, setTotalHits] = useState(0);
  const [latencySaved, setLatencySaved] = useState("0");
  const [vectorStoreSize, setVectorStoreSize] = useState("0");

  useEffect(() => {
    const fetchData = () => {
      fetch('/api/v1/dashboard/cache')
        .then(res => res.json())
        .then(data => {
          setCacheHistory(data.cacheHistory || []);
          setEvictionData(data.evictionData || []);
          setTotalHits(data.totalHits || 0);
          setLatencySaved(data.latencySaved || "0");
          setVectorStoreSize(data.vectorStoreSize || "0");
        })
        .catch(console.error);
    };

    fetchData();
    const interval = setInterval(fetchData, 5000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="space-y-8 pb-10">
      <div className="flex items-center gap-3 mb-6">
        <Database className="w-8 h-8 text-primary" />
        <h1 className="text-3xl font-bold text-white">Semantic Cache Analytics</h1>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <Card className="relative overflow-hidden group">
          <div className="absolute top-0 right-0 p-4 opacity-10 transform translate-x-4 -translate-y-4 group-hover:scale-110 group-hover:opacity-20 transition-all duration-500">
            <Zap className="w-24 h-24 text-accent" />
          </div>
          <CardHeader className="mb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Total Cache Hits</CardTitle></CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-white mb-2">{totalHits.toLocaleString()}</div>
            <p className="text-xs font-semibold text-accent">+14% <span className="text-muted-foreground font-normal ml-1">from yesterday</span></p>
          </CardContent>
        </Card>

        <Card className="relative overflow-hidden group">
          <div className="absolute top-0 right-0 p-4 opacity-10 transform translate-x-4 -translate-y-4 group-hover:scale-110 group-hover:opacity-20 transition-all duration-500">
            <Cpu className="w-24 h-24 text-primary" />
          </div>
          <CardHeader className="mb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Latency Saved</CardTitle></CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-white mb-2">{latencySaved}s</div>
            <p className="text-xs font-semibold text-accent">Average response time dropped</p>
          </CardContent>
        </Card>

        <Card className="relative overflow-hidden group">
          <div className="absolute top-0 right-0 p-4 opacity-10 transform translate-x-4 -translate-y-4 group-hover:scale-110 group-hover:opacity-20 transition-all duration-500">
            <HardDrive className="w-24 h-24 text-secondary" />
          </div>
          <CardHeader className="mb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Vector Store Size</CardTitle></CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-white mb-2">{vectorStoreSize} GB</div>
            <p className="text-xs font-semibold text-muted-foreground">pgvector (PostgreSQL)</p>
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card className="lg:col-span-2">
          <CardHeader><CardTitle>Cache Performance Over Time</CardTitle></CardHeader>
          <CardContent>
            <div className="h-[350px] w-full mt-4">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={cacheHistory} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="colorHits" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#10b981" stopOpacity={0.4}/>
                      <stop offset="95%" stopColor="#10b981" stopOpacity={0}/>
                    </linearGradient>
                    <linearGradient id="colorMisses" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#ef4444" stopOpacity={0.4}/>
                      <stop offset="95%" stopColor="#ef4444" stopOpacity={0}/>
                    </linearGradient>
                  </defs>
                  <XAxis dataKey="time" stroke="#475569" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis stroke="#475569" fontSize={12} tickLine={false} axisLine={false} />
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
                  <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '8px' }} />
                  <Area type="monotone" dataKey="hits" name="Cache Hits" stroke="#10b981" strokeWidth={3} fillOpacity={1} fill="url(#colorHits)" />
                  <Area type="monotone" dataKey="misses" name="Cache Misses" stroke="#ef4444" strokeWidth={3} fillOpacity={1} fill="url(#colorMisses)" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle>Eviction Reasons</CardTitle></CardHeader>
          <CardContent className="flex items-center justify-center">
            <div className="h-[350px] w-full mt-4">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={evictionData} cx="50%" cy="45%" innerRadius={80} outerRadius={110} paddingAngle={5} dataKey="value" stroke="none">
                    {evictionData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '8px' }} />
                  <Legend verticalAlign="bottom" height={36} iconType="circle" />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
