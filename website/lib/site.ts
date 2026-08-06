export const siteConfig = {
  name: "Oraculus",
  description: "为更专注的游戏体验而生。",
  navigation: [
    { href: "/features", label: "功能" },
    { href: "/download", label: "下载" },
    { href: "/changelog", label: "更新日志" },
    { href: "/status", label: "服务状态" },
  ],
} as const;

export const releases = [
  {
    version: "Beta 5",
    channel: "Beta",
    date: "2026-07-29",
    notes: ["新的 Scaffold 架构与旧配置兼容迁移", "完善账户授权与 Web 会话安全链路", "针对模块稳定性的一轮常规修复"],
  },
  {
    version: "Free 5",
    channel: "Free",
    date: "2026-07-29",
    notes: ["客户端认证协议保持兼容", "账户中心即将开放", "改进基础体验与错误提示"],
  },
] as const;

export const downloadChannels = [
  {
    name: "Free",
    description: "稳定的基础版本，适合首次体验。",
    badge: "公开",
    href: "#release-not-configured",
    requires: "Java 21 · Minecraft 1.21.4",
  },
  {
    name: "Beta",
    description: "面向已获授权用户的抢先版本。",
    badge: "需授权",
    href: "/account",
    requires: "Java 21 · Minecraft 1.21.4",
  },
] as const;
