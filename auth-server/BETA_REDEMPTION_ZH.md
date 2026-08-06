# Beta 卡密兑换与账号升级

## 功能

- `BETA_DURATION`：1-3650 天，账号已有有效 Beta 时从原到期时间叠加，否则从兑换时间开始。
- `BETA_PERMANENT`：固定写入 Unix 秒 `4102444799`，即 UTC `2099-12-31 23:59:59`。
- 同一账号重复提交同一卡密幂等；其他账号不能复用。
- 批次禁用只阻止未兑换卡密，不撤销已经生效的账号授权。

## 管理入口

超级管理员登录 `https://auth.hakuri.tech/admin`，进入“Beta 卡密”页面。创建批次成功后浏览器会立即下载明文卡密；服务器不提供明文恢复功能。明文丢失时应禁用原批次并重新生成。

### 客户端登录态自动发卡

Minecraft 客户端登录成功后，可以使用当前 Bearer AccessToken 调用
`POST /api/v1/beta-codes/issue` 自动生成并取得卡密。`USER`、`SUPPORT_ADMIN` 和
`SUPER_ADMIN` 均可调用；请求必须带 `Idempotency-Key`，支持时长卡和永久卡，单批最多 100 张，
单账号滚动 24 小时最多 1000 张。

客户端生成的批次立即可兑换，但会在后台标记为 `PENDING_REVIEW`。服务端记录请求账号、角色、
客户端会话、版本、构建 ID、启动器版本、requestId 和来源 IP 摘要。超级管理员可在 Beta 卡密
页面审查、填写备注或禁用异常批次；禁用只阻止未兑换卡密。

卡密只在成功响应或 15 分钟幂等重放窗口中以明文出现。服务端仅临时保存 AES-256-GCM 密文，
明文不写入认证 JSON。网络超时后必须用原 Idempotency-Key 重试；同键不同请求会冲突，交付窗口
过期后不得换键生成第二批。

### 卡密计费

超级管理员和支持管理员都可以在 Beta 卡密页面使用“卡密计费”工具，选择 UTC 起止自然日后查看该时间范围内生成的批次数量、卡密数量、分类单价和总价值。统计按批次生成时间计算，已兑换或已禁用的卡密仍计入生成总量。

| 分类 | 单价 |
| --- | ---: |
| 1 天卡 | 3 元 |
| 2 天卡 | 5 元 |
| 周卡（7 天） | 10 元 |
| 月卡（30 天） | 25 元 |
| 年卡（365 天） | 40 元 |
| 永久卡或时长超过 400 天 | 50 元 |

未列入定价表的时长会单独显示为“未定价”，不会计入总价值，避免把非标准时长静默折算为其他产品。

## 用户入口

- 认证服务自带用户页：`https://auth.hakuri.tech/user`。
- 官网账户中心：通过 `POST /api/account/beta/redeem` 调用内部接口 `POST /internal/web/v1/account/beta/redeem`。

兑换成功后，用户需要重新登录 Beta 客户端，取得新的 Ed25519 `EntitlementProof`。

## 持久化与备份

卡密数据保存在 `/var/lib/oraculus-auth/oraculus-auth.json` 的 `betaCodeBatches`、`betaCodes`、`betaRedemptions` 和客户端发卡幂等记录 `betaCodeIssueRequests` 数组中。明文卡密不落盘；查询键为：

```text
HMAC-SHA256(redeem-code-pepper, normalizedCode)
```

独立 pepper 位于 `/var/lib/oraculus-auth/keys/redeem-code-pepper.bin`，客户端发卡临时交付使用 `/var/lib/oraculus-auth/keys/beta-code-delivery-key.bin`。备份和恢复必须同时包含完整 JSON 数据文件和整个 `keys/` 目录，否则现有卡密将无法查询或在 15 分钟幂等窗口内恢复交付结果。

## 验证

```bash
node /opt/oraculus-auth/server.js --self-test
systemctl status oraculus-auth --no-pager
curl -fsS https://auth.hakuri.tech/health/live
curl -fsS https://auth.hakuri.tech/health/ready
```
