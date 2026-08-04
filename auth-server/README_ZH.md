# Oraculus 认证服务器

当前生产实现为无 npm 依赖的 Node.js 服务，部署目标为 Ubuntu。旧的 .NET Framework 源码与 Windows 部署脚本仅保留用于历史参考，不再是推荐生产路径。

其他客户端开发者接入前请完整阅读 [`API_INTEGRATION_ZH.md`](API_INTEGRATION_ZH.md)。

## 客户端兼容协议

客户端继续使用：

```text
https://auth.hakuri.tech/api/v1/
```

保留的接口包括：

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/heartbeat`
- `GET /api/v1/auth/status`
- `POST /api/v1/auth/logout`
- `GET /health/live`
- `GET /health/ready`
- `/user/*`
- `/admin/*`

客户端版本、启动器版本、构建 ID、Free/Beta 权限、HWID、访问令牌、刷新令牌轮换和刷新令牌重放撤销逻辑均由 `node-server/server.js` 实现。成功会话附带 Ed25519 签名的 `EntitlementProof`，客户端必须验证其账号、版本、设备和令牌绑定。`launcherVersion` 为可选字段：提供时仅校验启动器版本（当前允许 `v0.9.21`）；未提供时改为校验 `edition + clientVersion + buildId`。

## 管理权限

- `SUPER_ADMIN`：可以创建和管理 `SUPPORT_ADMIN`，也可以执行全部普通用户管理操作；
- `SUPPORT_ADMIN`：可以创建普通用户，并对普通用户执行与超级管理员相同的封禁、删除、改密、HWID 和 Free/Beta 管理操作，但不能创建或操作任何管理员；
- `USER`：仅能使用客户端和个人账号面板；
- `FREE` / `BETA` 是独立于管理员角色的客户端授权等级。

管理后台支持按用户名、ID、角色、等级、状态和创建来源搜索。账号列表使用每页 200 条的服务端分页，不再截断为前 500 个账号。`/admin/audit` 提供最近 200 条简明审计记录，完整审计数据仍持久化于认证数据文件中。

管理员设置临时密码后，账号必须先进入用户面板完成强制改密，才能重新进入管理后台或客户端；这次强制改密不受 168 小时自助改密冷却限制。

`SUPER_ADMIN` 可在 `/admin` 的“限时 Beta 公益”面板开启全局临时授权，并必须设置未来的 UTC 截止时间。开放期间，原始等级为 `FREE` 的用户可以登录 Beta 客户端；服务端仅对该 Beta 会话返回临时 `BETA` 授权，账号数据库中的 `tier` 不会被改写。到期或手动关闭后，Beta 会话会在下一次心跳或刷新时失效。`SUPPORT_ADMIN` 只能查看状态，不能修改该开关；每次变更都会写入简明审计日志。

## Ubuntu 生产结构

```text
/opt/oraculus-auth/                 程序和私有 Node.js 运行时
/etc/oraculus-auth/server.json      服务配置
/var/lib/oraculus-auth/             认证数据、密钥和 TLS 证书副本
/etc/systemd/system/                systemd 服务与证书续期计时器
```

运行特点：

- 不使用 IIS、.NET、Docker、Nginx 或 Apache；
- Node 直接监听 HTTPS 443；
- 专用低权限用户运行；
- systemd 仅授予绑定低端口所需的 capability；
- Certbot 使用空闲的 TCP 80 完成 HTTP-01；
- 每日检查续期，续期后复制最小权限证书并重启服务；
- 失败自动恢复程序与 systemd 配置；
- 数据目录不会在升级或回滚时删除。

## 构建 Ubuntu 发布包

在项目根目录运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File auth-server\deploy\Build-OraculusUbuntuDeployment.ps1
```

输出：

```text
auth-server/publish/OraculusAuth-Ubuntu/
auth-server/publish/OraculusAuth-Ubuntu.tar.gz
```

## Ubuntu 一键安装

上传并解压 `OraculusAuth-Ubuntu.tar.gz`，然后运行：

```bash
sudo bash install.sh
```

完整操作说明见 `ubuntu/README_ZH.md`。

## 数据备份

必须一起备份：

```text
/var/lib/oraculus-auth/oraculus-auth.json
/var/lib/oraculus-auth/keys/
```

`keys/` 同时包含 HWID/IP/密码 pepper 与 entitlement Ed25519 私钥。私钥丢失或被替换后，
已发布客户端将无法验证新签发的会话，因此密钥轮换必须作为显式客户端版本迁移处理。

JSON 数据和 `keys/` 缺少任意一方都会导致现有密码、HWID 或会话数据无法正确验证。
