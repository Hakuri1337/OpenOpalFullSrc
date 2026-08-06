# 官网内部 Web API

这组接口只供 Oraculus 官网 Next.js BFF 调用。它不是客户端 API，不能被浏览器、客户端模组或第三方直接调用。

## 启用方式

同机部署时，在 Auth 的 `server.json` 中设置：

```json
{
  "InternalWebsiteSecret": "至少 32 位、随机且仅本机保存的共享密钥",
  "InternalWebHost": "127.0.0.1",
  "InternalWebPort": 3101,
  "InternalWebTls": false,
  "InternalWebAllowedIps": []
}
```

填写密钥且端口非 `0` 后，服务会额外监听 `127.0.0.1:3101` 的明文 HTTP。该端口不会绑定到公网网卡；内部请求还必须包含准确的 `X-Oraculus-Website-Secret`。

当官网与 Auth 分离部署时，禁止使用明文 HTTP。以官网服务器 `160.202.238.53` 为例，必须改为：

```json
{
  "InternalWebsiteSecret": "至少 32 位的随机共享密钥",
  "InternalWebHost": "0.0.0.0",
  "InternalWebPort": 3101,
  "InternalWebTls": true,
  "InternalWebAllowedIps": ["160.202.238.53"]
}
```

这时官网使用 `https://auth.hakuri.tech:3101`。Auth 会复用已有的受信任 TLS 证书；除共享密钥外，只有白名单中的源 IP 能访问该监听器。还必须在 Auth 主机防火墙上仅放行 `160.202.238.53` 到 TCP 3101，不能放行全网。

官网在 `.env.local` 使用同一密钥：

```dotenv
ORACULUS_WEBSITE_SECRET=与 server.json 完全一致的值
AUTH_INTERNAL_URL=http://127.0.0.1:3101
```

每个请求均需：

```text
X-Oraculus-Website-Secret: <shared secret>
X-Oraculus-Client-IP: <由 BFF 规范化后的单个 IPv4/IPv6>
X-Oraculus-Web-Session: <网站 Web 会话；登录/注册时为空>
```

## 路由

| 方法 | 路径 | 会话 | 作用 |
|---|---|---:|---|
| POST | `/internal/web/v1/session/register` | 否 | 创建 `USER`/`FREE` 账号并签发网站会话 |
| POST | `/internal/web/v1/session/login` | 否 | 登录账户中心 |
| POST | `/internal/web/v1/session/logout` | 是 | 注销网站会话 |
| GET | `/internal/web/v1/account` | 是 | 获取可安全展示的账户信息 |
| POST | `/internal/web/v1/account/password` | 是 | 校验当前密码、更新密码并撤销全部会话 |
| POST | `/internal/web/v1/account/hwid/reset` | 是 | 校验当前密码、重置 HWID 并撤销全部会话 |

请求正文为 JSON。成功返回 `ok: true`；失败返回 `ok: false`、`error`、`message` 与 `requestId`。严禁将 `sessionToken`、共享密钥、密码、HWID 哈希或客户端 RefreshToken 写入浏览器日志或前端状态。

## 与客户端协议的边界

- 现有 `/api/v1/*` 保持原样，仍只为游戏客户端提供设备指纹、AccessToken 和 RefreshToken 流程。
- 官网会话与客户端会话相互独立；浏览器永远不会取得客户端令牌。
- 官网注册不预绑定 HWID；账号首次从客户端登录时，仍走既有的设备绑定逻辑。
- 改密和重置 HWID 都会调用既有的 `revokeAll`，因此官网必须清除自己的 Cookie 并要求重新登录。
