import { NextRequest, NextResponse } from "next/server";
import { authRequest, clearSession, errorResponse, input, readPayload, requireCsrf } from "@/app/api/auth/_lib";
import { sessionCookieName } from "@/lib/auth-server";

export async function POST(request: NextRequest) { if (!requireCsrf(request)) return errorResponse(403, "安全校验失败，请刷新页面后重试"); const body = await readPayload(request); if (!body) return errorResponse(400, "请求格式无效"); const token = request.cookies.get(sessionCookieName)?.value ?? ""; const result = await authRequest<{ reauthenticationRequired: boolean }>(request, "/internal/web/v1/account/hwid/reset", { currentPassword: input(body.currentPassword) }, token); if (!result.payload.ok) return errorResponse(result.status, result.payload.message ?? "重置设备失败"); const response = NextResponse.json({ ok: true, reauthenticationRequired: true }); clearSession(response); return response; }
