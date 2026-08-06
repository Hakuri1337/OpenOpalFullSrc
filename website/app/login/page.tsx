import { AuthForm } from "@/components/auth-form";

export const metadata = { title: "登录" };
export default function LoginPage() { return <section className="auth-shell"><h1>欢迎回来。</h1><p>使用 Oraculus 账户登录账户中心。</p><AuthForm mode="login" /></section>; }
