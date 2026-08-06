import { AuthForm } from "@/components/auth-form";

export const metadata = { title: "创建账户" };
export default function RegisterPage() { return <section className="auth-shell"><h1>创建账户。</h1><p>账户可用于客户端登录、授权验证和账户安全管理。</p><AuthForm mode="register" /></section>; }
