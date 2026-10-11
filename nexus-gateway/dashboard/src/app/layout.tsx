import type { Metadata } from "next";
import Script from "next/script";
import { Fira_Sans, Fira_Code } from "next/font/google";
import "./globals.css";
import { AppShell } from "@/components/layout/AppShell";
import { ThemeProvider } from "@/components/theme/ThemeProvider";
import { THEME_BOOTSTRAP_SCRIPT } from "@/lib/theme";

const firaSans = Fira_Sans({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  display: "swap",
  variable: "--font-fira-sans",
});

const firaCode = Fira_Code({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  display: "swap",
  variable: "--font-fira-code",
});

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
    <html lang="en" className={`${firaSans.variable} ${firaCode.variable}`} suppressHydrationWarning>
      <body className={`${firaSans.className} min-h-screen antialiased`}>
        <Script id="nexus-theme" strategy="beforeInteractive">
          {THEME_BOOTSTRAP_SCRIPT}
        </Script>
        <ThemeProvider>
          <AppShell>{children}</AppShell>
        </ThemeProvider>
      </body>
    </html>
  );
}
