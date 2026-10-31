import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../api/client";
import { apisApi } from "../api/apis";
import { API_BASE_URL } from "../api/client";
import { Spinner } from "./Spinner";
/** Settings tab: edit name/upstream/timeout, enable/disable toggle, delete with typed-name confirmation, and a copy-ready curl example using the gateway URL and a placeholder key. */
export function SettingsTab({ api, onUpdated }) {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    name: api.name,
    upstreamBaseUrl: api.upstreamBaseUrl,
    timeoutMs: String(api.timeoutMs),
  });
  const [enabled, setEnabled] = useState(api.enabled);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const [saved, setSaved] = useState(false);
  const [toggling, setToggling] = useState(false);
  const [confirmName, setConfirmName] = useState("");
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState(null);
  const [curlCopied, setCurlCopied] = useState(false);
  const dirty =
    form.name !== api.name ||
    form.upstreamBaseUrl !== api.upstreamBaseUrl ||
    Number(form.timeoutMs) !== api.timeoutMs;
  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
    setSaved(false);
  }
  function fieldError(name) {
    if (saveError instanceof ApiError && saveError.fields)
      return saveError.fields[name];
    return fieldErrors[name];
  }
  async function save(e) {
    e.preventDefault();
    setSaving(true);
    setSaveError(null);
    setFieldErrors({});
    setSaved(false);
    try {
      const timeoutMs = Number(form.timeoutMs);
      const updated = await apisApi.update(api.id, {
        name: form.name.trim(),
        upstreamBaseUrl: form.upstreamBaseUrl.trim(),
        timeoutMs,
      });
      onUpdated(updated);
      setSaved(true);
    } catch (err) {
      setSaveError(err);
    } finally {
      setSaving(false);
    }
  }
  async function toggleEnabled() {
    setToggling(true);
    setSaveError(null);
    try {
      const updated = await apisApi.update(api.id, { enabled: !enabled });
      setEnabled(updated.enabled);
      onUpdated(updated);
    } catch (err) {
      setSaveError(err);
    } finally {
      setToggling(false);
    }
  }
  async function remove() {
    if (confirmName !== api.name) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await apisApi.remove(api.id);
      navigate("/apis", { replace: true });
    } catch (err) {
      setDeleteError(err);
    } finally {
      setDeleting(false);
    }
  }
  const curl = `curl -H "X-API-Key: YOUR_KEY" \\\n  ${API_BASE_URL}/gw/${api.slug}/your/path`;
  async function copyCurl() {
    try {
      await navigator.clipboard.writeText(curl);
      setCurlCopied(true);
      setTimeout(() => setCurlCopied(false), 1800);
    } catch {
      setCurlCopied(false);
    }
  }
  return (
    <div className="settings-tab">
      <div className="panel">
        <div className="panel__header">
          <h3 className="panel__title">Details</h3>
        </div>
        <form className="form" onSubmit={save} noValidate>
          {saveError instanceof ApiError && !saveError.fields && (
            <div className="banner banner--danger" role="alert">
              {saveError.message}
            </div>
          )}
          <label className="field">
            <span className="field__label">Name</span>
            <input
              className="input"
              type="text"
              value={form.name}
              onChange={(e) => update("name", e.target.value)}
              maxLength={120}
              required
            />
            {fieldError("name") && (
              <span className="field__error">{fieldError("name")}</span>
            )}
          </label>
          <label className="field">
            <span className="field__label">Forward requests to</span>
            <input
              className="input input--mono"
              type="url"
              value={form.upstreamBaseUrl}
              onChange={(e) => update("upstreamBaseUrl", e.target.value)}
              placeholder="https://api.example.com"
              required
            />
            {fieldError("upstreamBaseUrl") && (
              <span className="field__error">
                {fieldError("upstreamBaseUrl")}
              </span>
            )}
          </label>
          <label className="field">
            <span className="field__label">Give up after (ms)</span>
            <input
              className="input"
              type="number"
              min={100}
              max={60000}
              value={form.timeoutMs}
              onChange={(e) => update("timeoutMs", e.target.value)}
              required
            />
            {fieldError("timeoutMs") && (
              <span className="field__error">{fieldError("timeoutMs")}</span>
            )}
          </label>
          <div className="form__actions">
            <button
              type="submit"
              className="btn btn--primary"
              disabled={saving || !dirty}
            >
              {saving ? <Spinner label="Saving" /> : "Save changes"}
            </button>
            {saved && <span className="form__ok">Saved</span>}
          </div>
        </form>
      </div>

      <div className="panel">
        <div className="panel__header">
          <h3 className="panel__title">Turn on or off</h3>
        </div>
        <div className="toggle-row">
          <div>
            <div className="toggle-row__label">Accept requests</div>
            <div className="toggle-row__hint">
              While this is off, anything sent to{" "}
              <code className="mono">/gw/{api.slug}</code> gets a 503.
            </div>
          </div>
          <button
            type="button"
            role="switch"
            aria-checked={enabled}
            className={`switch${enabled ? " is-on" : ""}`}
            onClick={toggleEnabled}
            disabled={toggling}
          >
            <span className="switch__knob" aria-hidden="true" />
            <span className="switch__label">{enabled ? "On" : "Off"}</span>
          </button>
        </div>
      </div>

      <div className="panel">
        <div className="panel__header">
          <h3 className="panel__title">Try a request</h3>
        </div>
        <p className="muted">
          Swap in one of your keys and run this in a terminal:
        </p>
        <pre className="code-block">{curl}</pre>
        <div className="form__actions">
          <button type="button" className="btn btn--ghost" onClick={copyCurl}>
            {curlCopied ? "Copied" : "Copy command"}
          </button>
        </div>
      </div>

      <div className="panel panel--danger">
        <div className="panel__header">
          <h3 className="panel__title panel__title--danger">Delete this API</h3>
        </div>
        <p className="muted">
          This removes the API, all of its keys and its request history for
          good.
        </p>
        <div className="confirm-delete">
          <label className="field">
            <span className="field__label">
              To confirm, type <code className="mono">{api.name}</code>
            </span>
            <input
              className="input"
              type="text"
              value={confirmName}
              onChange={(e) => setConfirmName(e.target.value)}
              placeholder={api.name}
            />
          </label>
          <button
            type="button"
            className="btn btn--danger"
            onClick={remove}
            disabled={deleting || confirmName !== api.name}
          >
            {deleting ? <Spinner label="Deleting" /> : "Delete API"}
          </button>
        </div>
        {deleteError instanceof ApiError && (
          <div className="banner banner--danger" role="alert">
            {deleteError.message}
          </div>
        )}
      </div>
    </div>
  );
}
