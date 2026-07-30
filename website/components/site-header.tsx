import Link from "next/link";
import { ArrowUpRight, Hexagon } from "lucide-react";
import { siteConfig } from "@/lib/site";

export function SiteHeader() {
  return (
    <header className="site-header">
      <nav className="nav container" aria-label="主导航">
        <Link className="brand" href="/" aria-label="Oraculus 首页">
          <span className="brand-mark"><Hexagon size={18} strokeWidth={2.4} /></span>
          <span>Oraculus</span>
        </Link>
        <div className="nav-links">
          {siteConfig.navigation.map((item) => <Link key={item.href} href={item.href}>{item.label}</Link>)}
        </div>
        <Link className="account-link" href="/account">账户中心 <ArrowUpRight size={15} /></Link>
      </nav>
    </header>
  );
}
