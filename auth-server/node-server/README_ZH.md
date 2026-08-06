# Oraculus Node 认证核心

这是不依赖 .NET、IIS、npm 包或原生扩展的认证服务核心。生产部署使用 Ubuntu 发布包，Windows 部署仅作为历史兼容方案保留。

本地验证：

```powershell
node server.js --self-test
node server.js --config server.production.json --check-config
```

命令：

- `--self-test`：在临时目录中验证注册、心跳、刷新轮换、重放撤销、Beta 权限、管理员保留、支持管理员层级、用户搜索、强制改密和审计记录行为；
- `--check-config`：解析配置并检查数据目录；
- `--check-ready`：检查持久化数据结构；
- `--ensure-admin root_admin`：从标准输入读取初始密码；仅在账号不存在时创建，不会在重复部署时重置现有管理员密码。

生产数据由一个原子替换写入的 JSON 文件和独立密钥目录组成。`keys/` 包含 entitlement Ed25519 私钥；备份时必须同时保存数据文件与完整 `keys/`，不得单独轮换或恢复其中一部分。

管理后台角色：

- `SUPER_ADMIN` 可以创建和管理 `SUPPORT_ADMIN`；
- `SUPPORT_ADMIN` 对普通用户拥有完整管理权限，但不能创建或操作任何管理员；
- 账号列表支持搜索和每页 200 条分页；
- `/admin/audit` 显示最近 200 条不含密码、令牌、HWID 和原始 IP 的简明审计记录。
