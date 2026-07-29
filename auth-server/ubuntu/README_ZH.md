# Oraculus Ubuntu 认证服务器

此发布包面向 Ubuntu 20.04、22.04 和 24.04 的 x86_64/arm64 服务器。认证接口、字段和错误码保持与现有 Minecraft 客户端兼容，客户端继续访问：

```text
https://auth.hakuri.tech/api/v1/
```

服务端不使用 IIS 或 .NET。运行结构为：

- 纯 Node.js 认证核心，无 npm 依赖；
- systemd 自动启动和故障重启；
- 专用低权限账号 `oraculus-auth`；
- Certbot HTTP-01 证书签发；
- 每日自动续期和证书热替换；
- 数据存放于 `/var/lib/oraculus-auth`；
- 配置存放于 `/etc/oraculus-auth/server.json`；
- 程序存放于 `/opt/oraculus-auth`；
- 日志通过 `journalctl -u oraculus-auth` 查看。

## 一键安装

先确保：

1. `auth.hakuri.tech` 的 A 记录指向此 Ubuntu 服务器；
2. 云安全组允许公网 TCP 80 和 443；
3. 本机没有 Nginx、Apache 或其他程序占用 80/443。

解压发布包后执行：

```bash
sudo bash install.sh
```

如需自定义域名或证书邮箱：

```bash
sudo ORACULUS_DOMAIN=auth.hakuri.tech ORACULUS_EMAIL=admin@hakuri.tech bash install.sh
```

如需强制核对 DNS 目标 IP：

```bash
sudo ORACULUS_EXPECTED_IP=203.0.113.10 bash install.sh
```

安装成功后，初始管理员密码仅写入：

```text
/root/oraculus-auth-deployment-result.txt
```

重复安装不会重置已经存在的 `root_admin` 密码。

## 常用命令

```bash
systemctl status oraculus-auth
journalctl -u oraculus-auth -n 200 --no-pager
curl https://auth.hakuri.tech/health/ready
systemctl restart oraculus-auth
systemctl list-timers oraculus-auth-renew.timer
```

备份时必须同时保存：

```text
/var/lib/oraculus-auth/oraculus-auth.json
/var/lib/oraculus-auth/keys/
```
