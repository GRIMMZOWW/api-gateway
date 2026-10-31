import { useEffect, useState } from "react";
import { ApiError } from "../api/client";
import { keysApi } from "../api/keys";
import { Spinner } from "./Spinner";
/** Edit an active key's name, capacity, and refill rate. */
export function EditKeyModal({ keyData, onClose, onUpdated }) {
  const open = keyData !== null;
  const [name, setName] = useState("");
  const [capacity, setCapacity] = useState("");
  const [refill, setRefill] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  useEffect(() => {
    if (keyData) {
      setName(keyData.name);
      setCapacity(String(keyData.capacity));
      setRefill(String(keyData.refillPerSec));
      setError(null);
      setSubmitting(false);
    }
  }, [keyData]);
  useEffect(() => {
    if (!open) return;
    const onEsc = (e) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onEsc);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onEsc);
      document.body.style.overflow = prev;
    };
  }, [open, onClose]);
  if (!open || !keyData) return null;
  function fieldError(name) {
    if (error instanceof ApiError && error.fields) return error.fields[name];
    return undefined;
  }
  const banner =
    error instanceof ApiError && !error.fields ? error.message : null;
  async function submit(e) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const updated = await keysApi.update(keyData.id, {
        name: name.trim(),
        capacity: capacity === "" ? undefined : Number(capacity),
        refillPerSec: refill === "" ? undefined : Number(refill),
      });
      onUpdated(updated);
      onClose();
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }
  return (
    <div
      className="modal-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="edit-key-title"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="modal">
        <div className="modal__header">
          <h2 id="edit-key-title" className="modal__title">
            Edit key
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
          {banner && (
            <div className="banner banner--danger" role="alert">
              {banner}
            </div>
          )}
          <label className="field">
            <span className="field__label">Name</span>
            <input
              className="input"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoFocus
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
                value={capacity}
                onChange={(e) => setCapacity(e.target.value)}
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
                value={refill}
                onChange={(e) => setRefill(e.target.value)}
              />
              {fieldError("refillPerSec") && (
                <span className="field__error">
                  {fieldError("refillPerSec")}
                </span>
              )}
            </label>
          </div>
          <div className="modal__actions">
            <button
              type="button"
              className="btn btn--ghost"
              onClick={onClose}
              disabled={submitting}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn btn--primary"
              disabled={submitting}
            >
              {submitting ? <Spinner label="Saving" /> : "Save changes"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
