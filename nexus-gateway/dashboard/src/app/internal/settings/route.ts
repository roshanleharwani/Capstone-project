import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

export async function GET(req: Request) {
  const url = new URL(req.url);
  let gatewayUrl = url.searchParams.get("gateway");
  if (!gatewayUrl) {
    gatewayUrl = process.env.NODE_ENV === 'development' ? 'http://127.0.0.1:8081' : 'http://nexus-proxy:8080';
  }
  try {
    const res = await fetch(`${gatewayUrl}/api/v1/settings`, { cache: 'no-store' });
    if (!res.ok) throw new Error("Backend error");
    const data = await res.json();
    return NextResponse.json(data);
  } catch (error) {
    // Return mock defaults if backend is down or not provided
    return NextResponse.json({
        regexScanner: true,
        secretScanner: true,
        nerScanner: true,
        cascadeGuard: true,
        stackelberg: true,
        cacheEnabled: true
    });
  }
}

export async function POST(req: Request) {
  const url = new URL(req.url);
  let gatewayUrl = url.searchParams.get("gateway");
  if (!gatewayUrl) {
    gatewayUrl = process.env.NODE_ENV === 'development' ? 'http://127.0.0.1:8081' : 'http://nexus-proxy:8080';
  }
  try {
    const body = await req.json();
    const res = await fetch(`${gatewayUrl}/api/v1/settings`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    const data = await res.json();
    return NextResponse.json(data);
  } catch (error) {
    return NextResponse.json({ success: false });
  }
}
