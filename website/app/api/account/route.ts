import { NextRequest, NextResponse } from "next/server";
import { getAccount, sessionCookieName } from "@/lib/auth-server";

export async function GET(request: NextRequest) { const token = request.cookies.get(sessionCookieName)?.value ?? ""; const result = await getAccount(token); return NextResponse.json(result.payload, { status: result.status }); }
