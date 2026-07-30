import "server-only";

import { headers } from "next/headers";
import type { Account, InternalResult } from "@/lib/auth-types";

const secret = process.env.ORACULUS_WEBSITE_SECRET ?? "";
const internalBaseUrl = (process.env.AUTH_INTERNAL_URL ?? "").replace(/\/$/, "");

export const sessionCookieName =
  process.env.NODE_ENV === "production" ? "__Host-oraculus_session" : "oraculus_session";
export const csrfCookieName = "oraculus_csrf";

export function authConfigured() {
  return secret.length >= 32 && /^https?:\/\//.test(internalBaseUrl);
}

export function requestIp(requestHeaders: Headers) {
  const candidate = (requestHeaders.get("x-forwarded-for") ?? "").split(",")[0].trim();
  return /^[0-9a-fA-F:.]{3,64}$/.test(candidate) ? candidate : "127.0.0.1";
}

export async function currentRequestIp() {
  return requestIp(await headers());
}

export async function authInternalFetch<T>(
  route: string,
  options: { method?: "GET" | "POST"; payload?: unknown; sessionToken?: string; clientIp?: string } = {},
): Promise<{ status: number; payload: InternalResult<T> }> {
  if (!authConfigured()) {
    return { status: 503, payload: { ok: false, error: "AUTH_NOT_CONFIGURED", message: "账户服务尚未配置" } as InternalResult<T> };
  }
  try {
    const response = await fetch(`${internalBaseUrl}${route}`, {
      method: options.method ?? "GET",
      cache: "no-store",
      headers: {
        "content-type": "application/json",
        "x-oraculus-website-secret": secret,
        "x-oraculus-web-session": options.sessionToken ?? "",
        "x-oraculus-client-ip": options.clientIp ?? "127.0.0.1",
      },
      body: options.payload === undefined ? undefined : JSON.stringify(options.payload),
    });
    return { status: response.status, payload: (await response.json()) as InternalResult<T> };
  } catch {
    return { status: 503, payload: { ok: false, error: "AUTH_UNAVAILABLE", message: "账户服务暂时不可用，请稍后再试" } as InternalResult<T> };
  }
}

export async function getAccount(sessionToken: string) {
  return authInternalFetch<{ account: Account; sessionExpiresAt: number }>("/internal/web/v1/account", { sessionToken });
}
