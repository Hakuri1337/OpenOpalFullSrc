import { Badge } from "@/components/ui/badge";
import { releases } from "@/lib/site";

export default function ChangelogPage() { return <><section className="page-head"><div className="container"><span className="eyebrow">Changelog</span><h1>更新应当可见。</h1><p>这里记录公开发布渠道的变化。安全敏感细节不会在公开日志中披露。</p></div></section><section className="section"><div className="container">{releases.map(release => <article className="release" key={release.version}><div><Badge className={release.channel === "Beta" ? "" : "badge-muted"}>{release.channel}</Badge><h3>{release.version}</h3><time>{release.date}</time></div><div><strong>本次更新</strong><ul>{release.notes.map(note => <li key={note}>{note}</li>)}</ul></div></article>)}</div></section></>; }
