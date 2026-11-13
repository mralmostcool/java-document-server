"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { startAuthentication } from "@simplewebauthn/browser";
import { api, friendly, sha256Hex } from "@/lib/api";

type Start = { requestId: string; options: any };
type Loc = { lat: number; lon: number; accuracy: number };

const locate = () => new Promise<Loc>((resolve, reject) =>
  navigator.geolocation.getCurrentPosition(
    (p) => resolve({ lat: p.coords.latitude, lon: p.coords.longitude, accuracy: p.coords.accuracy }),
    () => reject(new Error("Location was blocked. Allow it in the browser or untick the option.")),
    { timeout: 10_000, maximumAge: 60_000 }));

export default function Sign() {
  const router = useRouter();
  const [file, setFile] = useState<File | null>(null);
  const [hash, setHash] = useState("");
  const [withLoc, setWithLoc] = useState(false);
  const [busy, setBusy] = useState(false);
  const [step, setStep] = useState("");
  const [err, setErr] = useState("");
  const [certId, setCertId] = useState("");

  async function pick(f: File | null) {
    setFile(f); setCertId(""); setErr(""); setHash(f ? await sha256Hex(f) : "");
  }

  async function sign() {
    if (!file) return;
    setBusy(true); setErr(""); setCertId("");
    try {
      setStep("Uploading the document");
      const form = new FormData(); form.append("file", file);
      const { documentId } = await api<{ documentId: string }>("documents", { method: "POST", body: form });
      const location = withLoc ? await locate() : undefined;
      setStep("Touch your security key");
      const s = await api<Start>("signing/start", { method: "POST", json: { documentId, sha256: hash, location } });
      const credential = await startAuthentication({ optionsJSON: s.options });
      setStep("Creating the certificate");
      const done = await api<{ certificateId: string }>("signing/finish", { method: "POST", json: { requestId: s.requestId, credential } });
      setCertId(done.certificateId);
    } catch (e) { setErr(friendly(e)); }
    finally { setBusy(false); setStep(""); }
  }

  async function logout() { await api("auth/logout", { method: "POST" }); router.replace("/login"); }

  return (
    <>
      <div className="top">
        <h1>Sign a document</h1>
        <button className="ghost" onClick={logout}>Sign out</button>
      </div>
      <p className="mute">Your key signs a record tied to this file&rsquo;s fingerprint. The fingerprint is calculated in your browser.</p>
      <div className="sheet">
        <label htmlFor="file">Document</label>
        <input id="file" type="file" onChange={(e) => pick(e.target.files?.[0] ?? null)} />
        {hash && <p className="hash mute">SHA-256 {hash}</p>}
        <label className="check">
          <input type="checkbox" checked={withLoc} onChange={(e) => setWithLoc(e.target.checked)} />
          Include my approximate location in the certificate
        </label>
        <div className="row">
          <button onClick={sign} disabled={busy || !file}>Sign with security key</button>
        </div>
        {step && <p role="status" className="msg mute">{step}&hellip;</p>}
        {err && <p role="alert" className="msg bad">{err}</p>}
        {certId && (
          <div className="row" role="status">
            <div className="seal" aria-hidden>&#10003;</div>
            <p><strong>Signed.</strong><br /><a href={`/api/certificates/${certId}`} download>Download certificate</a></p>
          </div>
        )}
      </div>
    </>
  );
}
