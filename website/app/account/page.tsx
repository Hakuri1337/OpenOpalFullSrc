import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { AccountDashboard } from "@/components/account-dashboard";
import { getAccount, sessionCookieName } from "@/lib/auth-server";

export const dynamic = "force-dynamic";
export const metadata = { title: "账户中心" };

export default async function AccountPage() {
  const token = (await cookies()).get(sessionCookieName)?.value;
  if (!token) redirect("/login");
  const result = await getAccount(token);
  if (!result.payload.ok || !result.payload.account) redirect("/login");
  return <AccountDashboard account={result.payload.account} />;
}
