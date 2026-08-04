import { NextRequest, NextResponse } from "next/server";
import { authRequest, errorResponse, input, readPayload, requireCsrf } from "@/app/api/auth/_lib";
import { sessionCookieName } from "@/lib/auth-server";
import type { Account, BetaRedemption } from "@/lib/auth-types";

export async function POST(request: NextRequest) {
  if (!requireCsrf(request)) return errorResponse(403, "安全校验失败，请刷新页面后重试");
  const body = await readPayload(request);
  if (!body) return errorResponse(400, "请求格式无效");
  const token = request.cookies.get(sessionCookieName)?.value ?? "";
  const result = await authRequest<{ account: Account; redemption: BetaRedemption }>(
    request,
    "/internal/web/v1/account/beta/redeem",
    { code: input(body.code) },
    token,
  );
  if (!result.payload.ok) return errorResponse(result.status, result.payload.message ?? "Beta 卡密兑换失败");
  return NextResponse.json(result.payload, { status: result.status, headers: { "cache-control": "no-store" } });
}
