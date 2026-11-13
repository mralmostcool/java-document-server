"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { startAuthentication, startRegistration } from "@simplewebauthn/browser";
import { api, friendly } from "@/lib/api";

type Start = { requestId: string; options: any };

export default function Login() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  async function run(kind: "login" | "register") {
    setBusy(true); setMsg(null);
    try {
      const s = await api<Start>(`auth/webauthn/${kind}/start`, { method: "POST", json: { email, displayName: name } });
      const credential = kind === "login"
        ? await startAuthentication({ optionsJSON: s.options })
        : await startRegistration({ optionsJSON: s.options });
      await api(`auth/webauthn/${kind}/finish`, { method: "POST", json: { requestId: s.requestId, credential } });
      if (kind === "login") router.replace("/sign");
      else setMsg({ ok: true, text: "Key registered. Sign in with it now." });
    } catch (e) {
      setMsg({ ok: false, text: friendly(e) });
    } finally { setBusy(false); }
  }

  return (
    <>
      <h1>Sign in with your security key</h1>
      <p className="mute">Insert your key, enter your email, then touch the key when it blinks.</p>
      <form className="sheet" onSubmit={(e) => { e.preventDefault(); run("login"); }}>
        <label htmlFor="email">Work email</label>
        <input id="email" type="email" autoComplete="username webauthn" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <label htmlFor="name">Your name <span className="mute">(only when registering a new key)</span></label>
        <input id="name" type="text" autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} />
        <div className="row">
          <button type="submit" disabled={busy || !email}>Sign in with key</button>
          <button type="button" className="ghost" disabled={busy || !email} onClick={() => run("register")}>Register a new key</button>
        </div>
        {msg && <p role="status" className={`msg ${msg.ok ? "" : "bad"}`}>{msg.text}</p>}
      </form>
    </>
  );
}
