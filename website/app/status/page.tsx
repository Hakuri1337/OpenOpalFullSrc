import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";

const services = ["认证 API", "账户中心", "更新分发"];
export default function StatusPage() { return <><section className="page-head"><div className="container"><span className="eyebrow">Status</span><h1>服务状态。</h1><p>这是官网展示层的状态页。正式上线时将由受限监控数据源驱动，不会直接暴露管理或健康检查接口。</p></div></section><section className="section"><div className="container"><Card><Badge className="badge-ok">所有公开服务正常</Badge>{services.map(service => <div className="status-row" key={service}><div><strong>{service}</strong><span>过去 24 小时状态正常</span></div><Badge className="badge-ok">Operational</Badge></div>)}</Card></div></section></>; }
