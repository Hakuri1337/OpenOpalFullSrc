import Link from "next/link";
import { siteConfig } from "@/lib/site";

export function SiteFooter() {
  return <footer className="site-footer"><div className="container footer-inner">
    <div><strong>{siteConfig.name}</strong><p>账户、更新与下载的唯一官方入口。</p></div>
    <div className="footer-links"><Link href="/status">服务状态</Link><Link href="/account">账户中心</Link><Link href="/changelog">更新日志</Link></div>
    <small>© {new Date().getFullYear()} Oraculus. 保留所有权利。</small>
  </div></footer>;
}
