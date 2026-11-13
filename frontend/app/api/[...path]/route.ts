// Backend-for-frontend proxy. The browser only talks to this origin; the JWT lives in an
// httpOnly cookie and is attached as a Bearer token here, so page scripts can never read it.
import { NextRequest, NextResponse } from "next/server";
import { COOKIE } from "@/lib/config";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8080";
const ORIGIN = process.env.PUBLIC_ORIGIN;
const ALLOWED = /^(auth|documents|signing|certificates)(\/|$)/;
const prod = process.env.NODE_ENV === "production";

async function handle(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const rest = (await ctx.params).path.join("/");
  if (!ALLOWED.test(rest)) return NextResponse.json({ error: "NOT_FOUND" }, { status: 404 });

  if (rest === "auth/logout") {
    const out = NextResponse.json({ ok: true });
    out.cookies.set(COOKIE, "", { path: "/", maxAge: 0 });
    return out;
  }

  const unsafe = req.method !== "GET" && req.method !== "HEAD";
  if (unsafe && (req.headers.get("origin") !== (ORIGIN ?? req.nextUrl.origin) || req.headers.get("x-requested-with") !== "fetch")) {
    return NextResponse.json({ error: "FORBIDDEN", message: "Cross-site request blocked." }, { status: 403 });
  }

  const headers = new Headers();
  for (const h of ["content-type", "accept", "x-correlation-id", "x-forwarded-for", "user-agent"]) {
    const v = req.headers.get(h);
    if (v) headers.set(h, v);
  }
  const token = req.cookies.get(COOKIE)?.value;
  if (token) headers.set("authorization", `Bearer ${token}`);

  let up: Response;
  try {
    up = await fetch(`${BACKEND}/api/v1/${rest}${req.nextUrl.search}`, {
      method: req.method, headers, redirect: "manual", signal: AbortSignal.timeout(30_000),
      body: unsafe ? await req.arrayBuffer() : undefined,
    });
  } catch {
    return NextResponse.json({ error: "BAD_GATEWAY", message: "The server is unreachable. Try again shortly." }, { status: 502 });
  }

  const type = up.headers.get("content-type") ?? "";
  if (rest.startsWith("auth/") && up.ok && type.includes("json")) {
    const body = await up.json();
    const { token: jwt, ...safe } = body;
    const out = NextResponse.json(safe, { status: up.status });
    if (jwt) out.cookies.set(COOKIE, jwt, { httpOnly: true, secure: prod, sameSite: "strict", path: "/", maxAge: 900 });
    return out;
  }
  const out = new NextResponse(up.body, { status: up.status });
  for (const h of ["content-type", "content-disposition"]) {
    const v = up.headers.get(h);
    if (v) out.headers.set(h, v);
  }
  out.headers.set("Cache-Control", "no-store");
  return out;
}

export { handle as GET, handle as POST, handle as PUT, handle as DELETE };
