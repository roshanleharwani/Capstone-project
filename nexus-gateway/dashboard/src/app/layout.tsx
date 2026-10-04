import type { Metadata } from "next";

import "./globals.css";
import { Sidebar } from "@/components/layout/Sidebar";
import { Header } from "@/components/layout/Header";



export const metadata: Metadata = {
  title: "Nexus Gateway Dashboard",
  description: "Control plane and telemetry for Nexus Gateway router.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="dark">
      <body className={`antialiased flex bg-background`}>
        <Sidebar />
        <main className="flex-1 ml-64 flex flex-col min-h-screen">
          <Header />
          <div className="flex-1 p-8 overflow-y-auto">
            {children}
          </div>
        </main>
      </body>
    </html>
  );
}
