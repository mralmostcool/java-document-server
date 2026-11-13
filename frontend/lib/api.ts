// Backend contract (Spring Boot, all under /api/v1, reached through the proxy as /api/...):
//  auth/webauthn/{register,login}/start   {email, displayName} -> {requestId, options}
//  auth/webauthn/{register,login}/finish  {requestId, credential} -> {} (login returns {token}; proxy moves it to a cookie)
//  documents (multipart "file")           -> {documentId}
//  signing/start   {documentId, sha256, location?} -> {requestId, options}
//  signing/finish  {requestId, credential}         -> {certificateId}
//  certificates/{id}                      -> certificate file
export async function api<T = unknown>(path: string, init: Omit<RequestInit, "body"> & { json?: unknown; body?: BodyInit } = {}): Promise<T> {
  const { json, headers, body, ...rest } = init;
  const res = await fetch(`/api/${path}`, {
    ...rest,
    credentials: "same-origin",
    headers: { "X-Requested-With": "fetch", ...(json !== undefined ? { "Content-Type": "application/json" } : {}), ...headers },
    body: json !== undefined ? JSON.stringify(json) : body,
  });
  if (!res.ok) {
    const e = await res.json().catch(() => null);
    throw new Error(e?.message ?? `Request failed (${res.status}).`);
  }
  return res.json();
}

export async function sha256Hex(file: File): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", await file.arrayBuffer());
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function friendly(e: unknown): string {
  if (e instanceof Error && e.name === "NotAllowedError") return "The security key request was cancelled or timed out. Try again and touch the key.";
  return e instanceof Error ? e.message : "Something went wrong.";
}
