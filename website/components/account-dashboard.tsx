"use client";

import { useState, type FormEvent } from "react";
import { KeyRound, LogOut, MonitorOff, ShieldAlert, TicketCheck } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import type { Account } from "@/lib/auth-types";
import { formatDate } from "@/lib/utils";

async function csrfToken() {
  const response = await fetch("/api/auth/csrf", { cache: "no-store" });
  const body = (await response.json()) as { csrfToken?: string };
  if (!response.ok || !body.csrfToken) throw new Error("安全校验初始化失败，请刷新页面后重试");
  return body.csrfToken;
}

export function AccountDashboard({ account: initialAccount }: { account: Account }) {
  const [account, setAccount] = useState(initialAccount);
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState("");

  async function post<T>(route: string, payload?: Record<string, FormDataEntryValue>) {
    const csrf = await csrfToken();
    const response = await fetch(route, {
      method: "POST",
      headers: { "content-type": "application/json", "x-oraculus-csrf": csrf },
      body: JSON.stringify(payload ?? {}),
    });
    const body = (await response.json()) as T & { message?: string; reauthenticationRequired?: boolean };
    if (!response.ok) throw new Error(body.message || "操作未完成，请稍后再试");
    if (body.reauthenticationRequired) window.location.assign("/login");
    return body;
  }

  async function logout() {
    setBusy("logout"); setNotice("");
    try { await post("/api/auth/logout"); window.location.assign("/"); }
    catch (cause) { setNotice(cause instanceof Error ? cause.message : "退出失败"); }
    finally { setBusy(""); }
  }

  async function redeem(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy("redeem"); setNotice("");
    try {
      const result = await post<{ account: Account }>("/api/account/beta/redeem", Object.fromEntries(new FormData(event.currentTarget)));
      if (result.account) setAccount(result.account);
      event.currentTarget.reset();
      setNotice(result.message ?? "Beta 授权兑换成功，请重新登录 Beta 客户端");
    } catch (cause) { setNotice(cause instanceof Error ? cause.message : "Beta 卡密兑换失败"); }
    finally { setBusy(""); }
  }

  async function changePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy("password"); setNotice("");
    try { await post("/api/account/password", Object.fromEntries(new FormData(event.currentTarget))); }
    catch (cause) { setNotice(cause instanceof Error ? cause.message : "修改密码失败"); }
    finally { setBusy(""); }
  }

  async function resetHwid(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy("hwid"); setNotice("");
    try { await post("/api/account/hwid/reset", Object.fromEntries(new FormData(event.currentTarget))); }
    catch (cause) { setNotice(cause instanceof Error ? cause.message : "重置失败"); }
    finally { setBusy(""); }
  }

  const beta = account.betaActive;
  const expiry = account.betaPermanent ? "永久（2099-12-31）" : formatDate(account.betaExpiresAt);
  return <section className="account-layout"><div className="container">
    <div className="account-top"><div><Badge className={beta ? "" : "badge-muted"}>{beta ? "Beta" : "Free"}</Badge><h1>你好，{account.username}</h1><p className="account-subtitle">管理你的授权与安全设置。</p></div><Button variant="secondary" onClick={logout} disabled={busy === "logout"}><LogOut size={16} /> 退出登录</Button></div>
    {account.forcePasswordChange && <Card className="warning"><ShieldAlert size={19} /><h3>需要更新临时密码</h3><p>管理员设置的临时密码必须在此处更新。</p></Card>}
    {notice && <p className="form-note" role="alert">{notice}</p>}
    <div className="account-grid">
      <Card><h3>授权状态</h3><div className="detail-list"><div className="detail-row"><span>账号等级</span><strong>{beta ? "BETA" : "FREE"}</strong></div><div className="detail-row"><span>Beta 到期</span><strong>{beta ? expiry : "未开通"}</strong></div><div className="detail-row"><span>账号状态</span><strong>{account.status}</strong></div><div className="detail-row"><span>账号角色</span><strong>{account.role}</strong></div></div></Card>
      <Card><TicketCheck size={20} className="icon-box" /><h3>兑换 Beta 卡密</h3><p>兑换时长卡会从当前有效到期时间继续叠加。永久卡固定到期至 2099-12-31。</p><form className="form-stack" style={{ marginTop: 18 }} onSubmit={redeem}><div className="field"><label>卡密</label><input className="input" name="code" autoComplete="off" placeholder="ORA-BETA-..." maxLength={80} required /></div><Button disabled={busy === "redeem"}>{busy === "redeem" ? "正在兑换..." : "兑换并升级账号"}</Button></form><p className="form-note">兑换成功后请重新登录 Beta 客户端。</p></Card>
      <Card><h3>最近兑换</h3>{account.betaRedemptions?.length ? <div className="detail-list">{account.betaRedemptions.map(item => <div className="detail-row" key={item.id}><span>{item.product === "BETA_PERMANENT" ? "永久卡" : `${Math.floor((item.durationSeconds ?? 0) / 86400)} 天`} · ****{item.codeSuffix}</span><strong>{formatDate(item.redeemedAt)}</strong></div>)}</div> : <p className="form-note">暂无兑换记录。</p>}</Card>
      <Card><MonitorOff size={20} className="icon-box" /><h3>设备绑定</h3><div className="detail-list"><div className="detail-row"><span>当前状态</span><strong>{account.hwidBound ? `已绑定 · ${account.hwidQuality || "已验证"}` : "将在首次客户端登录时绑定"}</strong></div><div className="detail-row"><span>最近变更</span><strong>{formatDate(account.hwidChangedAt)}</strong></div></div></Card>
      <Card><KeyRound size={20} className="icon-box" /><h3>修改密码</h3><p>操作完成后将注销所有客户端和网站会话。</p><form className="form-stack" style={{ marginTop: 18 }} onSubmit={changePassword}><div className="field"><label>当前密码</label><input className="input" name="currentPassword" type="password" autoComplete="current-password" required /></div><div className="field"><label>新密码</label><input className="input" name="newPassword" type="password" autoComplete="new-password" minLength={12} maxLength={128} required /></div><Button disabled={busy === "password"}>{busy === "password" ? "正在更新..." : "更新并退出所有会话"}</Button></form></Card>
      <Card><MonitorOff size={20} className="icon-box" /><h3>重置设备绑定</h3><p>重置后，下次从客户端登录会绑定新设备。</p><form className="form-stack" style={{ marginTop: 18 }} onSubmit={resetHwid}><div className="field"><label>当前密码</label><input className="input" name="currentPassword" type="password" autoComplete="current-password" required /></div><Button variant="danger" disabled={busy === "hwid"}>{busy === "hwid" ? "正在重置..." : "重置设备绑定"}</Button></form></Card>
    </div>
  </div></section>;
}
