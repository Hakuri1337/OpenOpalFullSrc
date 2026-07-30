import { NextRequest, NextResponse } from "next/server";
import { authRequest, errorResponse, input, readPayload, requireCsrf, setSession } from "@/app/api/auth/_lib";

export async function POST(request: NextRequest) { if (!requireCsrf(request)) return errorResponse(403, "安全校验失败，请刷新页面后重试"); const body = await readPayload(request); if (!body) return errorResponse(400, "请求格式无效"); const result = await authRequest<{ sessionToken: string }>(request, "/internal/web/v1/session/login", { username: input(body.username), password: input(body.password) }); if (!result.payload.ok || !result.payload.sessionToken) return errorResponse(result.status, result.payload.message ?? "登录失败"); const response = NextResponse.json({ ok: true }); setSession(response, result.payload.sessionToken); return response; }
