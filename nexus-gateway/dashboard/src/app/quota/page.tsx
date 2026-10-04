"use client";
import { useState, useEffect } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { 
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  BarChart, Bar
} from "recharts";
import { TrendingUp, Coins, Activity, Users } from "lucide-react";
import { motion } from "framer-motion";

export default function MarketQuota() {
  const [priceTrendData, setPriceTrendData] = useState<any[]>([]);
  const [tenantQuotaData, setTenantQuotaData] = useState<any[]>([]);
  const [ledgerTransactions, setLedgerTransactions] = useState<any[]>([]);
  const [currentExchangeRate, setCurrentExchangeRate] = useState(2.8);
  const [systemUtilization, setSystemUtilization] = useState(0);
  const [surplusPoolSize, setSurplusPoolSize] = useState(0);

  useEffect(() => {
    const fetchData = () => {
      fetch('/api/v1/dashboard/quota')
        .then(res => res.json())
        .then(data => {
          setPriceTrendData(data.priceTrendData || []);
          setTenantQuotaData(data.tenantQuotaData || []);
          setLedgerTransactions(data.ledgerTransactions || []);
          setCurrentExchangeRate(data.currentExchangeRate || 2.8);
          setSystemUtilization(data.systemUtilization || 0);
          setSurplusPoolSize(data.surplusPoolSize || 0);
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
        <TrendingUp className="w-8 h-8 text-secondary" />
        <h1 className="text-3xl font-bold text-white">Market Incentives & Quota</h1>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <Card className="relative overflow-hidden group">
          <div className="absolute top-0 right-0 p-4 opacity-10 transform translate-x-4 -translate-y-4 group-hover:scale-110 group-hover:opacity-20 transition-all duration-500">
            <Coins className="w-24 h-24 text-accent" />
          </div>
          <CardHeader className="mb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Current Exchange Rate</CardTitle></CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-white mb-2">{currentExchangeRate.toFixed(1)} <span className="text-sm font-normal text-muted-foreground">cr/kT</span></div>
            <p className="text-xs font-semibold text-accent">-31% <span className="text-muted-foreground font-normal ml-1">from peak</span></p>
          </CardContent>
        </Card>

        <Card className="relative overflow-hidden group">
          <div className="absolute top-0 right-0 p-4 opacity-10 transform translate-x-4 -translate-y-4 group-hover:scale-110 group-hover:opacity-20 transition-all duration-500">
            <Activity className="w-24 h-24 text-secondary" />
          </div>
          <CardHeader className="mb-2"><CardTitle className="text-sm font-medium text-muted-foreground">System Utilization</CardTitle></CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-white mb-2">{systemUtilization}%</div>
            <p className="text-xs font-semibold text-destructive">High Load (Stackelberg Active)</p>
          </CardContent>
        </Card>

        <Card className="relative overflow-hidden group">
          <div className="absolute top-0 right-0 p-4 opacity-10 transform translate-x-4 -translate-y-4 group-hover:scale-110 group-hover:opacity-20 transition-all duration-500">
            <Users className="w-24 h-24 text-primary" />
          </div>
          <CardHeader className="mb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Surplus Pool Size</CardTitle></CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-white mb-2">{(surplusPoolSize / 1000).toFixed(1)}k <span className="text-sm font-normal text-muted-foreground">tokens</span></div>
            <p className="text-xs font-semibold text-primary">Available for VCG Auction</p>
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card>
          <CardHeader><CardTitle>Stackelberg Pricing Trend</CardTitle></CardHeader>
          <CardContent>
            <div className="h-[300px] w-full mt-4">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={priceTrendData} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
                  <XAxis dataKey="time" stroke="#475569" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis stroke="#475569" fontSize={12} tickLine={false} axisLine={false} />
                  <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '8px' }} />
                  <Line type="stepAfter" dataKey="rate" name="Credits / 1k Tokens" stroke="#8b5cf6" strokeWidth={3} dot={{r: 4, fill: '#8b5cf6'}} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle>Tenant Quota Utilization</CardTitle></CardHeader>
          <CardContent>
            <div className="h-[300px] w-full mt-4">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={tenantQuotaData} margin={{ top: 10, right: 10, left: -10, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
                  <XAxis dataKey="name" stroke="#475569" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis stroke="#475569" fontSize={12} tickLine={false} axisLine={false} tickFormatter={(val) => `${val/1000}k`} />
                  <Tooltip cursor={{fill: '#1e293b', opacity: 0.4}} contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '8px' }} />
                  <Bar dataKey="used" name="Used Tokens" fill="#3b82f6" radius={[4, 4, 0, 0]} />
                  <Bar dataKey="limit" name="Daily Limit" fill="#334155" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader><CardTitle>Immutable Ledger Feed (Recent Token Borrows)</CardTitle></CardHeader>
        <CardContent>
          <div className="overflow-x-auto mt-4">
            <table className="w-full text-sm text-left text-muted-foreground">
              <thead className="text-xs text-white uppercase bg-white/5 border-b border-white/10">
                <tr>
                  <th scope="col" className="px-6 py-3 rounded-tl-lg">Tx ID</th>
                  <th scope="col" className="px-6 py-3">Borrower</th>
                  <th scope="col" className="px-6 py-3">Lender</th>
                  <th scope="col" className="px-6 py-3">Tokens</th>
                  <th scope="col" className="px-6 py-3">Rate</th>
                  <th scope="col" className="px-6 py-3 text-right">Credits Paid</th>
                  <th scope="col" className="px-6 py-3 text-right rounded-tr-lg">Time</th>
                </tr>
              </thead>
              <tbody>
                {ledgerTransactions.map((tx, i) => (
                  <motion.tr 
                    initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: i * 0.1 }}
                    key={tx.txId} 
                    className="bg-transparent border-b border-white/5 hover:bg-white/5 transition-colors"
                  >
                    <td className="px-6 py-4 font-mono text-xs">{tx.txId}</td>
                    <td className="px-6 py-4 font-semibold text-white">{tx.borrower}</td>
                    <td className="px-6 py-4 text-primary">{tx.lender}</td>
                    <td className="px-6 py-4">{tx.tokens.toLocaleString()}</td>
                    <td className="px-6 py-4">{tx.rate.toFixed(1)}</td>
                    <td className="px-6 py-4 text-right font-bold text-accent">{tx.credits.toFixed(1)}</td>
                    <td className="px-6 py-4 text-right text-xs">{tx.time}</td>
                  </motion.tr>
                ))}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
