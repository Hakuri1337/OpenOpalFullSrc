import crypto from "node:crypto";
import { NextRequest, NextResponse } from "next/server";
import { authInternalFetch, csrfCookieName, requestIp, sessionCookieName } from "@/lib/auth-server";

export function createCsrfToken() { return crypto.randomBytes(24).toString("base64url"); }

export function csrfCookieOptions() {
  return { httpOnly: false, secure: process.env.NODE_ENV === "production", sameSite: "strict" as const, path: "/", maxAge: 60 * 60 };
}

export function sessionCookieOptions() {
  return { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "strict" as const, path: "/", maxAge: 8 * 60 * 60 };
}

function timingSafeEqual(left: string, right: string) {
  const a = Buffer.from(left); const b = Buffer.from(right);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

export function requireCsrf(request: NextRequest) {
  const fromCookie = request.cookies.get(csrfCookieName)?.value ?? "";
  const fromHeader = request.headers.get("x-oraculus-csrf") ?? "";
  return fromCookie.length >= 24 && timingSafeEqual(fromCookie, fromHeader);
}

export async function readPayload(request: NextRequest) {
  try {
    const body = await request.json();
    return body && typeof body === "object" && !Array.isArray(body) ? body as Record<string, unknown> : null;
  } catch { return null; }
}

export function input(value: unknown) { return typeof value === "string" ? value : ""; }
export function errorResponse(status: number, message: string) { return NextResponse.json({ ok: false, message }, { status }); }

export async function authRequest<T>(request: NextRequest, route: string, payload?: unknown, sessionToken?: string) {
  return authInternalFetch<T>(route, { method: "POST", payload, sessionToken, clientIp: requestIp(request.headers) });
}

export function setSession(response: NextResponse, token: string) { response.cookies.set(sessionCookieName, token, sessionCookieOptions()); }
export function clearSession(response: NextResponse) { response.cookies.set(sessionCookieName, "", { ...sessionCookieOptions(), maxAge: 0 }); }
