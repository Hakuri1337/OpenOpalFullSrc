import { Blocks, Gauge, ShieldCheck } from "lucide-react";
import { Card } from "@/components/ui/card";

const entries = [[Blocks, "模块化架构", "功能以独立模块组织，版本更新可以更精确地定位影响范围。"], [Gauge, "稳定优先", "每项变更都应先经过构建与兼容性验证，再进入发布渠道。"], [ShieldCheck, "授权边界", "账号角色、Free/Beta 等级与客户端会话各司其职。"]] as const;

export default function FeaturesPage() { return <><section className="page-head"><div className="container"><span className="eyebrow">Features</span><h1>为可靠性而设计。</h1><p>Oraculus 的功能体验建立在稳定的构建、清晰的授权边界和可维护的模块架构之上。</p></div></section><section className="section"><div className="container grid grid-3">{entries.map(([Icon, title, text]) => <Card key={title}><span className="icon-box"><Icon size={20} /></span><h3>{title}</h3><p>{text}</p></Card>)}</div></section></>; }
