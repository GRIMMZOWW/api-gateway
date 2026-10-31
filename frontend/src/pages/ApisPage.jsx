import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ApiError } from "../api/client";
import { apisApi } from "../api/apis";
import { analyticsApi } from "../api/analytics";
import { useAsync } from "../hooks/useAsync";
import { Spinner, SkeletonRows } from "../components/Spinner";
import { EmptyState } from "../components/EmptyState";
import { ErrorState } from "../components/ErrorState";
import { formatNumber } from "../utils/format";
/** One row in the list. */
function ApiRow({ api }) {
  const { data, loading, error } = useAsync(async () => {
    const to = Date.now();
    const from = to - 24 * 60 * 60 * 1000;
    return analyticsApi.summary({
      apiId: api.id,
      from: new Date(from).toISOString(),
      to: new Date(to).toISOString(),
    });
  }, [api.id]);
  return (
    <Link to={`/apis/${api.id}`} className="api-row">
      <div>
        <div className="api-row__name">{api.name}</div>
        <div className="api-row__where">
          /gw/{api.slug} forwards to {api.upstreamBaseUrl}
        </div>
      </div>
      <span
        className={`status-pill status-pill--${api.enabled ? "active" : "inactive"}`}
      >
        {api.enabled ? "On" : "Off"}
      </span>
      <div className="api-row__count">
        <strong>
          {loading ? "…" : error ? "–" : formatNumber(data?.totalRequests ?? 0)}
        </strong>
        <span>last 24 hours</span>
      </div>
    </Link>
  );
}
const INITIAL_FORM = {
  name: "",
  slug: "",
  upstreamBaseUrl: "",
  timeoutMs: "5000",
};
export function ApisPage() {
  const navigate = useNavigate();
  const {
    data: apis,
    loading,
    error,
    reload,
  } = useAsync(() => apisApi.list(), []);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(INITIAL_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState(null);
  useEffect(() => {
    if (!showForm) {
      setForm(INITIAL_FORM);
      setFormError(null);
      setSubmitting(false);
    }
  }, [showForm]);
  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }
  function fieldError(name) {
    if (formError instanceof ApiError && formError.fields)
      return formError.fields[name];
    return undefined;
  }
  const banner =
    formError instanceof ApiError && !formError.fields
      ? formError.message
      : null;
  async function submit(e) {
    e.preventDefault();
    setSubmitting(true);
    setFormError(null);
    try {
      const created = await apisApi.create({
        name: form.name.trim(),
        slug: form.slug.trim(),
        upstreamBaseUrl: form.upstreamBaseUrl.trim(),
        timeoutMs: form.timeoutMs ? Number(form.timeoutMs) : undefined,
      });
      setShowForm(false);
      navigate(`/apis/${created.id}`);
    } catch (err) {
      setFormError(err);
    } finally {
      setSubmitting(false);
    }
  }
  return (
    <div className="page">
      <div className="page__header">
        <div>
          <h1 className="page__title">APIs</h1>
          <p className="page__subtitle">
            Each API gets an address on this gateway, like /gw/my-api, and
            forwards to a server you choose.
          </p>
        </div>
        <button
          type="button"
          className="btn btn--primary"
          onClick={() => setShowForm(true)}
        >
          Add an API
        </button>
      </div>

      {loading && (
        <div className="panel">
          <SkeletonRows rows={4} cols={3} />
        </div>
      )}

      {!loading && error != null && (
        <div className="panel">
          <ErrorState error={error} onRetry={reload} />
        </div>
      )}

      {!loading && !error && apis && apis.length === 0 && (
        <div className="panel">
          <EmptyState
            title="Nothing here yet"
            description="Add the server you want to protect. You will get a gateway address and can then create keys for it."
            action={
              <button
                type="button"
                className="btn btn--primary"
                onClick={() => setShowForm(true)}
              >
                Add an API
              </button>
            }
          />
        </div>
      )}

      {!loading && !error && apis && apis.length > 0 && (
        <div className="api-list">
          {apis.map((api) => (
            <ApiRow key={api.id} api={api} />
          ))}
        </div>
      )}

      {showForm && (
        <div
          className="modal-overlay"
          role="dialog"
          aria-modal="true"
          aria-labelledby="new-api-title"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) setShowForm(false);
          }}
        >
          <div className="modal">
            <div className="modal__header">
              <h2 id="new-api-title" className="modal__title">
                Add an API
              </h2>
              <button
                type="button"
                className="modal__close"
                aria-label="Close"
                onClick={() => setShowForm(false)}
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
                  value={form.name}
                  onChange={(e) => update("name", e.target.value)}
                  placeholder="Weather service"
                  autoFocus
                  required
                  maxLength={120}
                />
                {fieldError("name") && (
                  <span className="field__error">{fieldError("name")}</span>
                )}
              </label>
              <label className="field">
                <span className="field__label">Slug</span>
                <input
                  className="input input--mono"
                  type="text"
                  value={form.slug}
                  onChange={(e) => update("slug", e.target.value)}
                  placeholder="weather"
                  required
                  pattern="[a-z0-9-]+"
                  maxLength={64}
                />
                <span className="field__hint">
                  Lowercase letters, numbers and dashes. Requests go to{" "}
                  <code className="mono">/gw/{form.slug || "slug"}</code>
                </span>
                {fieldError("slug") && (
                  <span className="field__error">{fieldError("slug")}</span>
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
                  <span className="field__error">
                    {fieldError("timeoutMs")}
                  </span>
                )}
              </label>
              <div className="modal__actions">
                <button
                  type="button"
                  className="btn btn--ghost"
                  onClick={() => setShowForm(false)}
                  disabled={submitting}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn btn--primary"
                  disabled={submitting}
                >
                  {submitting ? <Spinner label="Adding" /> : "Add API"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
