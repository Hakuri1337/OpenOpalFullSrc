import Link from "next/link";
import { Download as DownloadIcon, ExternalLink } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { downloadChannels } from "@/lib/site";

export default function DownloadPage() { return <><section className="page-head"><div className="container"><span className="eyebrow">Downloads</span><h1>选择你的发布渠道。</h1><p>下载链接会在每次正式发布后填写并附带校验信息。Beta 下载需要账户具有有效授权。</p></div></section><section className="section"><div className="container grid grid-2">{downloadChannels.map(item => <Card key={item.name}><Badge className={item.name === "Beta" ? "" : "badge-ok"}>{item.badge}</Badge><h3>{item.name}</h3><p>{item.description}</p><p style={{ marginTop: 16, fontSize: 13 }}>{item.requires}</p><div className="hero-actions">{item.href.startsWith("#") ? <button className="button button-secondary" disabled><DownloadIcon size={16} /> 发布链接待配置</button> : <Link className="button button-primary" href={item.href}>进入账户中心 <ExternalLink size={16} /></Link>}</div></Card>)}</div></section></>; }
