# Oraculus 官方网站

独立的 Next.js App Router 网站；它不会影响根目录的 Gradle 客户端构建。生产输出使用 standalone 模式，可由 Node 18.20.8 直接运行，兼容 Windows Server 2012 R2。

## 本地运行

```powershell
cd website
npm install
Copy-Item .env.example .env.local
npm run dev
```

官网公开页不需要 Auth 服务即可浏览。要测试账户流程，必须同时满足：

1. 在 Auth 的 `server.json` 配置至少 32 位的 `InternalWebsiteSecret`；
2. 在 `website/.env.local` 中以 `ORACULUS_WEBSITE_SECRET` 填入相同值；
3. `AUTH_INTERNAL_URL` 必须指向 Auth 的 `InternalWebHost:InternalWebPort`；同机使用 `http://127.0.0.1:3101`，独立官网服务器使用 `https://auth.hakuri.tech:3101`；
4. 独立部署时，Auth 必须启用 TLS、仅白名单官网 IP，并配置与官网完全相同的共享密钥。

## 安全边界

- 浏览器从不接触游戏客户端的 AccessToken 或 RefreshToken。
- 网站只把自己的 HttpOnly、SameSite=Strict Web 会话 Cookie 发给浏览器。
- 所有 BFF 写操作使用双提交 CSRF Token。
- 内部接口为 `/internal/web/v1/*`，只在启用密钥后监听 `InternalWebHost:InternalWebPort`，并要求共享密钥与回环来源或显式 IP 白名单同时通过验证；非回环监听必须使用 TLS。
- `/api/v1/*` 是客户端协议，未被本网站改动或复用。

上线反向代理和 systemd 资产见仓库根目录的 `ORACULUS_OFFICIAL_WEBSITE_PLAN.md`；当前实现不修改线上端口、DNS 或证书。
