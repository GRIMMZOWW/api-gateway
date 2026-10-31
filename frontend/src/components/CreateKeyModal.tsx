import { useEffect, useState } from "react";
import { ApiError } from "../api/client";
import { keysApi, type ApiKey } from "../api/keys";
import { Spinner } from "./Spinner";
import { ErrorState } from "./ErrorState";

interface CreateKeyModalProps {
  apiId: number | string;
  open: boolean;
  onClose: () => void;
  /** Called after a key is created (so the table can refresh). */
  onCreated: (key: ApiKey) => void;
}

interface FormState {
  name: string;
  capacity: string;
  refillPerSec: string;
  expiresAt: string; // datetime-local input, blank = no expiry
}

const INITIAL: FormState = {
  name: "",
  capacity: "",
  refillPerSec: "",
  expiresAt: "",
};

/**
 * Two-step modal: step 1 is the create form, step 2 reveals the full key once.
 * Step 2 is dismissible but the key cannot be recovered afterward.
 */
export function CreateKeyModal({ apiId, open, onClose, onCreated }: CreateKeyModalProps) {
  const [step, setStep] = useState<"form" | "reveal">("form");
  const [form, setForm] = useState<FormState>(INITIAL);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [fullKey, setFullKey] = useState<string>("");
  const [copied, setCopied] = useState(false);

  // Reset whenever the modal opens.
  useEffect(() => {
    if (open) {
      setStep("form");
      setForm(INITIAL);
      setError(null);
      setFullKey("");
      setCopied(false);
      setSubmitting(false);
    }
  }, [open]);

  // Lock body scroll + close on Escape while open.
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
    };
  }, [open, onClose]);

  if (!open) return null;

  function update(field: keyof FormState, value: string) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  function fieldError(name: string): string | undefined {
    if (error instanceof ApiError && error.fields) return error.fields[name];
    return undefined;
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    setCopied(false);
    try {
      const capacity = form.capacity === "" ? undefined : Number(form.capacity);
      const refill = form.refillPerSec === "" ? undefined : Number(form.refillPerSec);
      const expiresAt = form.expiresAt
        ? new Date(form.expiresAt).toISOString()
        : undefined;
      if (capacity !== undefined && !Number.isFinite(capacity)) {
        throw new ApiError(400, {
          code: "BAD_REQUEST",
          message: "Capacity must be a number.",
          fields: { capacity: "Capacity must be a number." },
        });
      }
      const res = await keysApi.create(apiId, {
        name: form.name.trim(),
        capacity,
        refillPerSec: refill,
        expiresAt,
      });
      setFullKey(res.fullKey);
      onCreated(res.key);
      setStep("reveal");
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  async function copyKey() {
    try {
      await navigator.clipboard.writeText(fullKey);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      // Older browsers: select-and-copy fallback handled by the readonly input.
      setCopied(false);
    }
  }

  return (
    <div
      className="modal-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="create-key-title"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="modal">
        {step === "form" ? (
          <>
            <div className="modal__header">
              <h2 id="create-key-title" className="modal__title">
                New key
              </h2>
              <button
                type="button"
                className="modal__close"
                aria-label="Close"
                onClick={onClose}
              >
                ×
              </button>
            </div>
            <form className="form" onSubmit={submit} noValidate>
              {error instanceof ApiError && !error.fields && (
                <div className="banner banner--danger" role="alert">
                  {error.message}
                </div>
              )}
              <label className="field">
                <span className="field__label">Name</span>
                <input
                  className="input"
                  type="text"
                  value={form.name}
                  onChange={(e) => update("name", e.target.value)}
                  placeholder="Mobile app"
                  autoFocus
                  required
                  maxLength={80}
                />
                {fieldError("name") && (
                  <span className="field__error">{fieldError("name")}</span>
                )}
              </label>
              <div className="field-row">
                <label className="field">
                  <span className="field__label">Burst size</span>
                  <input
                    className="input"
                    type="number"
                    min={1}
                    value={form.capacity}
                    onChange={(e) => update("capacity", e.target.value)}
                    placeholder="60"
                  />
                  {fieldError("capacity") && (
                    <span className="field__error">{fieldError("capacity")}</span>
                  )}
                </label>
                <label className="field">
                  <span className="field__label">Requests per second</span>
                  <input
                    className="input"
                    type="number"
                    min={0}
                    step="0.1"
                    value={form.refillPerSec}
                    onChange={(e) => update("refillPerSec", e.target.value)}
                    placeholder="1"
                  />
                  {fieldError("refillPerSec") && (
                    <span className="field__error">{fieldError("refillPerSec")}</span>
                  )}
                </label>
              </div>
              <label className="field">
                <span className="field__label">Expires (optional)</span>
                <input
                  className="input"
                  type="datetime-local"
                  value={form.expiresAt}
                  onChange={(e) => update("expiresAt", e.target.value)}
                />
                {fieldError("expiresAt") && (
                  <span className="field__error">{fieldError("expiresAt")}</span>
                )}
              </label>
              {fieldError("") && (
                <span className="field__error">{fieldError("")}</span>
              )}
              <div className="modal__actions">
                <button
                  type="button"
                  className="btn btn--ghost"
                  onClick={onClose}
                  disabled={submitting}
                >
                  Cancel
                </button>
                <button type="submit" className="btn btn--primary" disabled={submitting}>
                  {submitting ? <Spinner label="Creating" /> : "Create key"}
                </button>
              </div>
            </form>
          </>
        ) : (
          <>
            <div className="modal__header">
              <h2 className="modal__title">Copy your key now</h2>
              <button
                type="button"
                className="modal__close"
                aria-label="Close"
                onClick={onClose}
              >
                ×
              </button>
            </div>
            <div className="reveal">
              <div className="banner banner--warning" role="alert">
                <strong>This is the only time you will see it.</strong> The server
                keeps a hash, not the key, so it can’t be shown again. Paste it
                somewhere safe before you close this.
              </div>
              <label className="field">
                <span className="field__label">Key</span>
                <textarea
                  className="input input--mono"
                  readOnly
                  rows={3}
                  value={fullKey}
                  onFocus={(e) => e.currentTarget.select()}
                />
              </label>
              <div className="modal__actions">
                <button type="button" className="btn btn--primary" onClick={copyKey}>
                  {copied ? "Copied" : "Copy key"}
                </button>
                <button type="button" className="btn btn--ghost" onClick={onClose}>
                  Done
                </button>
              </div>
            </div>
          </>
        )}
        {error != null && !(error instanceof ApiError) && (
          <div className="modal__error">
            <ErrorState error={error} />
          </div>
        )}
      </div>
    </div>
  );
}
