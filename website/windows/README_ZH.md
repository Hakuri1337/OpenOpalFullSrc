# Oraculus 官网 Windows Server 2012 R2 一键部署包

目标服务器已固定为：

```text
域名：ora.hakuri.tech
IP：160.202.238.53
系统：Windows Server 2012 R2 x64
```

双击 `Deploy-OraculusWebsite.cmd` 即可安装。脚本会请求管理员权限、校验包完整性、下载 Node 18.20.8、Nginx 与 ACME 工具、开放 TCP 80/443、签发 Let's Encrypt 证书、注册开机自启和每日续期任务。

首次运行会安全地要求输入 **Auth 远程官网桥接密钥**。该密钥不能由两台不同服务器自动生成或猜测；必须先在 Ubuntu Auth 服务器运行包内的 `Auth-Bridge-Setup-Ubuntu.sh`，它会输出一份随机密钥。

Auth 服务器一次性操作：

```bash
sudo bash Auth-Bridge-Setup-Ubuntu.sh 160.202.238.53
```

然后将终端输出的密钥粘贴回 Windows 安装器。该脚本会把 Auth 内部接口限制为：TLS、TCP 3101、源 IP `160.202.238.53` 与共享密钥三重验证。还要在 Auth 服务器的云防火墙中只允许 `160.202.238.53` 访问 TCP 3101。

不要把 TCP 3101 暴露给全网，也不要把桥接密钥发到聊天记录、Git、截图或前端代码中。
