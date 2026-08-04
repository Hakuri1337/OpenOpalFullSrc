# Beta 卡密兑换与账号升级

## 功能

- `BETA_DURATION`：1-3650 天，账号已有有效 Beta 时从原到期时间叠加，否则从兑换时间开始。
- `BETA_PERMANENT`：固定写入 Unix 秒 `4102444799`，即 UTC `2099-12-31 23:59:59`。
- 同一账号重复提交同一卡密幂等；其他账号不能复用。
- 批次禁用只阻止未兑换卡密，不撤销已经生效的账号授权。

## 管理入口

超级管理员登录 `https://auth.hakuri.tech/admin`，进入“Beta 卡密”页面。创建批次成功后浏览器会立即下载明文卡密；服务器不提供明文恢复功能。明文丢失时应禁用原批次并重新生成。

## 用户入口

- 认证服务自带用户页：`https://auth.hakuri.tech/user`。
- 官网账户中心：通过 `POST /api/account/beta/redeem` 调用内部接口 `POST /internal/web/v1/account/beta/redeem`。

兑换成功后，用户需要重新登录 Beta 客户端，取得新的 Ed25519 `EntitlementProof`。

## 持久化与备份

卡密数据保存在 `/var/lib/oraculus-auth/oraculus-auth.json` 的 `betaCodeBatches`、`betaCodes` 和 `betaRedemptions` 数组中。明文卡密不落盘；查询键为：

```text
HMAC-SHA256(redeem-code-pepper, normalizedCode)
```

独立 pepper 位于 `/var/lib/oraculus-auth/keys/redeem-code-pepper.bin`。备份和恢复必须同时包含完整 JSON 数据文件和整个 `keys/` 目录，否则现有卡密将无法查询。

## 验证

```bash
node /opt/oraculus-auth/server.js --self-test
systemctl status oraculus-auth --no-pager
curl -fsS https://auth.hakuri.tech/health/live
curl -fsS https://auth.hakuri.tech/health/ready
```
