import type { Metadata } from "next";
import "./globals.css";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";

export const metadata: Metadata = {
  title: { default: "Oraculus", template: "%s · Oraculus" },
  description: "Oraculus 官方网站与账户中心。",
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? "https://oraculus.hakuri.tech"),
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="zh-CN"><body><SiteHeader /><main>{children}</main><SiteFooter /></body></html>;
}
