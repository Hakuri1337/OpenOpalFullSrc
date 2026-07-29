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

客户端版本、构建 ID、Free/Beta 权限、HWID、访问令牌、刷新令牌轮换和刷新令牌重放撤销逻辑均由 `node-server/server.js` 实现。

## 管理权限

- `SUPER_ADMIN`：可以创建和管理 `SUPPORT_ADMIN`，也可以执行全部普通用户管理操作；
- `SUPPORT_ADMIN`：可以创建普通用户，并对普通用户执行与超级管理员相同的封禁、删除、改密、HWID 和 Free/Beta 管理操作，但不能创建或操作任何管理员；
- `USER`：仅能使用客户端和个人账号面板；
- `FREE` / `BETA` 是独立于管理员角色的客户端授权等级。

管理后台支持按用户名、ID、角色、等级、状态和创建来源搜索。账号列表使用每页 200 条的服务端分页，不再截断为前 500 个账号。`/admin/audit` 提供最近 200 条简明审计记录，完整审计数据仍持久化于认证数据文件中。

管理员设置临时密码后，账号必须先进入用户面板完成强制改密，才能重新进入管理后台或客户端；这次强制改密不受 168 小时自助改密冷却限制。

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

JSON 数据和 `keys/` 缺少任意一方都会导致现有密码、HWID 或会话数据无法正确验证。
