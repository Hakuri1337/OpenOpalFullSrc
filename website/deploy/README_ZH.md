# 官网部署资产

`oraculus-website.service` 将 Next.js 仅监听在 `127.0.0.1:3000`，避免应用自身暴露公网端口。

部署前先在 `/opt/oraculus-website` 执行：

```bash
npm ci
npm run build
```

然后创建仅 root 与 `oraculus` 可读的 `/etc/oraculus-website/website.env`：

```dotenv
NODE_ENV=production
NEXT_PUBLIC_SITE_URL=https://oraculus.hakuri.tech
AUTH_INTERNAL_URL=http://127.0.0.1:3101
ORACULUS_WEBSITE_SECRET=与 /etc/oraculus-auth/server.json 的 InternalWebsiteSecret 相同
```

安装 systemd 文件后执行 `sudo systemctl daemon-reload && sudo systemctl enable --now oraculus-website`。

`Caddyfile.snippet` 是未来的反向代理迁移资产：只有 Auth 不再直接占用公网 443 后才能启用。当前阶段不要因此停止、替换或重配正在运行的 Auth 服务。
