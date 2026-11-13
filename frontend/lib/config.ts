// "__Host-" prefix forces Secure + Path=/ + no Domain; browsers reject it on plain http, so prod only.
export const COOKIE = process.env.NODE_ENV === "production" ? "__Host-session" : "session";
