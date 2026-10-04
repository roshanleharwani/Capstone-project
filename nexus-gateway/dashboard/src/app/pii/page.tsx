"use client";
import { useState, useEffect } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { 
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  LineChart, Line
} from "recharts";
import { ShieldAlert, ShieldCheck, Lock, EyeOff } from "lucide-react";
import { motion } from "framer-motion";

export default function PIISecurity() {
  const [interceptionData, setInterceptionData] = useState<any[]>([]);
  const [riskTrendData, setRiskTrendData] = useState<any[]>([]);
  const [recentThreats, setRecentThreats] = useState<any[]>([]);
  const [totalInterceptions, setTotalInterceptions] = useState(0);
  const [promptInjectionsBlocked, setPromptInjectionsBlocked] = useState(0);
  const [entitiesPseudonymized, setEntitiesPseudonymized] = useState(0);

  useEffect(() => {
    const fetchData = () => {
      fetch('/api/v1/dashboard/pii')
        .then(res => res.json())
        .then(data => {
          setInterceptionData(data.interceptionData || []);
          setRiskTrendData(data.riskTrendData || []);
          setRecentThreats(data.recentThreats || []);
          setTotalInterceptions(data.totalInterceptions || 0);
          setPromptInjectionsBlocked(data.promptInjectionsBlocked || 0);
          setEntitiesPseudonymized(data.entitiesPseudonymized || 0);
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
        <ShieldAlert className="w-8 h-8 text-destructive" />
        <h1 className="text-3xl font-bold text-white">Security & PII Shield</h1>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card className="relative overflow-hidden group">
          <div className="absolute top-0 right-0 p-4 opacity-10 transform translate-x-4 -translate-y-4 group-hover:scale-110 group-hover:opacity-20 transition-all duration-500">
            <ShieldCheck className="w-24 h-24 text-accent" />
          </div>
          <CardHeader className="mb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Total Interceptions</CardTitle></CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-white mb-2">{totalInterceptions.toLocaleString()}</div>
            <p className="text-xs font-semibold text-accent">Safe Execution</p>
          </CardContent>
        </Card>

        <Card className="relative overflow-hidden group">
          <div className="absolute top-0 right-0 p-4 opacity-10 transform translate-x-4 -translate-y-4 group-hover:scale-110 group-hover:opacity-20 transition-all duration-500">
            <Lock className="w-24 h-24 text-destructive" />
          </div>
          <CardHeader className="mb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Prompt Injections Blocked</CardTitle></CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-white mb-2">{promptInjectionsBlocked.toLocaleString()}</div>
            <p className="text-xs font-semibold text-destructive">High Risk Events</p>
          </CardContent>
        </Card>

        <Card className="relative overflow-hidden group">
          <div className="absolute top-0 right-0 p-4 opacity-10 transform translate-x-4 -translate-y-4 group-hover:scale-110 group-hover:opacity-20 transition-all duration-500">
            <EyeOff className="w-24 h-24 text-primary" />
          </div>
          <CardHeader className="mb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Entities Pseudonymized</CardTitle></CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-white mb-2">{entitiesPseudonymized.toLocaleString()}</div>
            <p className="text-xs font-semibold text-primary">Session-Stable Masking</p>
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card>
          <CardHeader><CardTitle>Interceptions by Scanner Engine</CardTitle></CardHeader>
          <CardContent>
            <div className="h-[300px] w-full mt-4">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={interceptionData} layout="vertical" margin={{ top: 0, right: 30, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" horizontal={false} />
                  <XAxis type="number" stroke="#475569" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis type="category" dataKey="name" stroke="#475569" fontSize={12} tickLine={false} axisLine={false} />
                  <Tooltip cursor={{fill: '#1e293b', opacity: 0.4}} contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '8px' }} />
                  <Bar dataKey="blocks" fill="#ef4444" radius={[0, 4, 4, 0]} barSize={40} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle>Risk Trend (Last 5 Days)</CardTitle></CardHeader>
          <CardContent>
            <div className="h-[300px] w-full mt-4">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={riskTrendData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
                  <XAxis dataKey="time" stroke="#475569" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis stroke="#475569" fontSize={12} tickLine={false} axisLine={false} />
                  <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '8px' }} />
                  <Line type="monotone" dataKey="high" name="High Risk" stroke="#ef4444" strokeWidth={3} dot={{r: 4, fill: '#ef4444'}} />
                  <Line type="monotone" dataKey="med" name="Medium Risk" stroke="#eab308" strokeWidth={3} dot={{r: 4, fill: '#eab308'}} />
                  <Line type="monotone" dataKey="low" name="Low Risk" stroke="#10b981" strokeWidth={3} dot={{r: 4, fill: '#10b981'}} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader><CardTitle>Recent Threat Log</CardTitle></CardHeader>
        <CardContent>
          <div className="space-y-4 mt-4">
            {recentThreats.map((threat, i) => (
              <motion.div 
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.1 }}
                key={threat.id} 
                className="flex flex-col sm:flex-row sm:items-center justify-between p-4 rounded-lg bg-white/5 border border-white/5 hover:bg-white/10 transition-colors"
              >
                <div className="flex items-center gap-4 mb-2 sm:mb-0">
                  <div className={`px-3 py-1 rounded-full text-xs font-bold ${
                    threat.severity === 'Critical' ? 'bg-red-500/20 text-red-400 border border-red-500/30' :
                    threat.severity === 'High' ? 'bg-orange-500/20 text-orange-400 border border-orange-500/30' :
                    threat.severity === 'Medium' ? 'bg-yellow-500/20 text-yellow-400 border border-yellow-500/30' :
                    'bg-green-500/20 text-green-400 border border-green-500/30'
                  }`}>
                    {threat.severity}
                  </div>
                  <div>
                    <div className="text-sm font-semibold text-white">{threat.type}</div>
                    <div className="text-xs text-muted-foreground">{threat.id}</div>
                  </div>
                </div>
                <div className="flex items-center gap-6">
                  <div className="text-sm">
                    <span className="text-muted-foreground mr-2">Action:</span>
                    <span className={threat.action === 'Blocked' ? 'text-destructive font-semibold' : 'text-primary font-semibold'}>{threat.action}</span>
                  </div>
                  <div className="text-xs text-muted-foreground w-20 text-right">{threat.time}</div>
                </div>
              </motion.div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
