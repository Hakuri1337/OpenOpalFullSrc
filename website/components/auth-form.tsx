"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";

type Props = { mode: "login" | "register" };

async function csrfToken() {
  const response = await fetch("/api/auth/csrf", { cache: "no-store" });
  const body = (await response.json()) as { csrfToken?: string };
  if (!response.ok || !body.csrfToken) throw new Error("安全校验初始化失败，请刷新页面后重试");
  return body.csrfToken;
}

export function AuthForm({ mode }: Props) {
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const isRegister = mode === "register";
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setMessage("");
    const fields = new FormData(event.currentTarget);
    try {
      const csrf = await csrfToken();
      const response = await fetch(`/api/auth/${mode}`, { method: "POST", headers: { "content-type": "application/json", "x-oraculus-csrf": csrf }, body: JSON.stringify({ username: fields.get("username"), password: fields.get("password") }) });
      const body = (await response.json()) as { message?: string };
      if (!response.ok) throw new Error(body.message || "操作未完成，请稍后再试");
      window.location.assign("/account");
    } catch (cause) { setMessage(cause instanceof Error ? cause.message : "操作未完成，请稍后再试"); }
    finally { setBusy(false); }
  }
  return <form className="card form-stack" onSubmit={submit}>
    <div className="field"><label htmlFor="username">用户名</label><input className="input" id="username" name="username" autoComplete="username" minLength={3} maxLength={24} pattern="[A-Za-z0-9_]+" required /></div>
    <div className="field"><label htmlFor="password">密码</label><input className="input" id="password" name="password" type="password" autoComplete={isRegister ? "new-password" : "current-password"} minLength={12} maxLength={128} required /></div>
    {isRegister && <p className="form-footer">密码长度须为 12–128 个字符；注册后首次客户端登录会绑定设备。</p>}
    <p className="form-note" role="alert">{message}</p><Button type="submit" disabled={busy}>{busy ? "正在处理…" : isRegister ? "创建账户" : "登录"}</Button>
    <p className="form-footer">{isRegister ? <>已有账户？<Link href="/login">登录</Link></> : <>还没有账户？<Link href="/register">创建账户</Link></>}</p>
  </form>;
}
