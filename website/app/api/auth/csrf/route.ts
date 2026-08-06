import { NextResponse } from "next/server";
import { createCsrfToken, csrfCookieOptions } from "@/app/api/auth/_lib";
import { csrfCookieName } from "@/lib/auth-server";

export const dynamic = "force-dynamic";
export async function GET() { const token = createCsrfToken(); const response = NextResponse.json({ ok: true, csrfToken: token }); response.cookies.set(csrfCookieName, token, csrfCookieOptions()); return response; }
