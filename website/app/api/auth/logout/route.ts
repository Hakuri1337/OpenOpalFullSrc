import { NextRequest, NextResponse } from "next/server";
import { authRequest, clearSession, errorResponse, requireCsrf } from "@/app/api/auth/_lib";
import { sessionCookieName } from "@/lib/auth-server";

export async function POST(request: NextRequest) { if (!requireCsrf(request)) return errorResponse(403, "安全校验失败，请刷新页面后重试"); const token = request.cookies.get(sessionCookieName)?.value ?? ""; const result = await authRequest(request, "/internal/web/v1/session/logout", {}, token); const response = NextResponse.json({ ok: result.payload.ok }); clearSession(response); return response; }
