import Link from "next/link";
import { ArrowRight, Download, LockKeyhole, Sparkles } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";

const highlights = [
  [Sparkles, "克制的体验", "聚焦真正重要的功能与清晰反馈，不用冗余界面打断操作。"],
  [LockKeyhole, "账户即权限", "Free 与 Beta 授权由统一 Auth 服务校验，客户端与官网各自使用隔离会话。"],
  [Download, "持续迭代", "公开的更新记录、下载入口和服务状态，让每次变更都可追溯。"],
] as const;

export default function HomePage() {
  return <><section className="hero"><div className="container">
    <span className="eyebrow">Oraculus · Official</span>
    <h1>更专注的<br />游戏体验。</h1>
    <p>Oraculus 将客户端、账户和授权体验收拢为一个简单、可靠且可持续迭代的系统。</p>
    <div className="hero-actions"><Link className="button button-primary" href="/download">获取客户端 <ArrowRight size={16} /></Link><Link className="button button-secondary" href="/account">进入账户中心</Link></div>
    <div className="metric-strip"><div className="metric"><strong>Free / Beta</strong><span>清晰、独立的授权层级</span></div><div className="metric"><strong>Auth first</strong><span>账户安全由统一服务处理</span></div><div className="metric"><strong>Java 21</strong><span>当前客户端运行环境</span></div></div>
  </div></section>
  <section className="section"><div className="container"><Badge>设计原则</Badge><h2 className="section-title">少一点噪音，多一点确定性。</h2><p className="section-lead">官网采用深色、低干扰的视觉语言：信息层级清楚，操作路径短，账户状态始终可见。</p><div className="grid grid-3" style={{ marginTop: 30 }}>{highlights.map(([Icon, title, description]) => <Card key={title}><span className="icon-box"><Icon size={20} /></span><h3>{title}</h3><p>{description}</p></Card>)}</div></div></section>
  <section className="section"><div className="container"><Card className="cta-card"><Badge className="badge-ok">系统在线</Badge><h2 className="section-title">从账户开始。</h2><p className="section-lead">注册、登录、查看 Beta 授权和安全设置，均在账户中心完成。</p><div className="hero-actions"><Link className="button button-primary" href="/register">创建账户</Link><Link className="button button-ghost" href="/status">查看服务状态</Link></div></Card></div></section>
  </>;
}
