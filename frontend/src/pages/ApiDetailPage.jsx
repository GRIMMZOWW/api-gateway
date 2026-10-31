import { useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { ApiError } from "../api/client";
import { apisApi } from "../api/apis";
import { keysApi } from "../api/keys";
import { analyticsApi } from "../api/analytics";
import { useAsync } from "../hooks/useAsync";
import { Spinner, SkeletonRows } from "../components/Spinner";
import { EmptyState } from "../components/EmptyState";
import { ErrorState } from "../components/ErrorState";
import { SummaryCards } from "../components/SummaryCards";
import { RequestsChart } from "../components/RequestsChart";
import { TopPathsTable } from "../components/TopPathsTable";
import { KeysTable } from "../components/KeysTable";
import { CreateKeyModal } from "../components/CreateKeyModal";
import { EditKeyModal } from "../components/EditKeyModal";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { RecentRequestsTable } from "../components/RecentRequestsTable";
import { SettingsTab } from "../components/SettingsTab";
import {
  DateRangePicker,
  rangeByKey,
  rangeWindow,
} from "../components/DateRangePicker";
export function ApiDetailPage() {
  const { id } = useParams();
  const {
    data: api,
    loading,
    error,
    reload,
  } = useAsync(() => apisApi.get(id), [id]);
  const [tab, setTab] = useState("overview");
  if (loading) {
    return (
      <div className="page">
        <Spinner block label="Loading" />
      </div>
    );
  }
  if (error) {
    return (
      <div className="page">
        <div className="panel">
          <ErrorState error={error} onRetry={reload} />
        </div>
      </div>
    );
  }
  if (!api) return null;
  return (
    <div className="page">
      <div className="detail-head">
        <div className="detail-head__title">
          <h1 className="page__title">{api.name}</h1>
          <div className="detail-head__meta">
            <span className="mono">/gw/{api.slug}</span>
            <span
              className={`status-pill status-pill--${api.enabled ? "active" : "inactive"}`}
            >
              {api.enabled ? "On" : "Off"}
            </span>
          </div>
        </div>
      </div>

      <nav className="tabs" role="tablist" aria-label="API sections">
        {[
          ["overview", "Overview"],
          ["keys", "Keys"],
          ["requests", "Requests"],
          ["settings", "Settings"],
        ].map(([key, label]) => (
          <button
            key={key}
            role="tab"
            aria-selected={tab === key}
            className={`tabs__btn${tab === key ? " is-active" : ""}`}
            onClick={() => setTab(key)}
          >
            {label}
          </button>
        ))}
      </nav>

      {tab === "overview" && <OverviewTab api={api} />}
      {tab === "keys" && <KeysTab api={api} />}
      {tab === "requests" && <RequestsTab api={api} />}
      {tab === "settings" && (
        <SettingsTab api={api} onUpdated={() => reload()} />
      )}
    </div>
  );
}
function OverviewTab({ api }) {
  const [range, setRange] = useState("24h");
  const window = useMemo(() => rangeWindow(range), [range]);
  const bucket = rangeByKey(range).bucket;
  const { data, loading, error, reload } = useAsync(async () => {
    const [summary, timeseries, topPaths] = await Promise.all([
      analyticsApi.summary({ apiId: api.id, ...window }),
      analyticsApi.timeseries({ apiId: api.id, ...window, bucket }),
      analyticsApi.topPaths({ apiId: api.id, ...window, limit: 10 }),
    ]);
    return { summary, timeseries, topPaths };
  }, [api.id, window.from, window.to, bucket]);
  return (
    <div className="tab-pane">
      <div className="tab-pane__bar">
        <DateRangePicker value={range} onChange={setRange} />
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          onClick={reload}
        >
          Refresh
        </button>
      </div>

      {loading && (
        <>
          <div className="skeleton-block" />
          <div className="chart chart--skeleton" />
        </>
      )}

      {!loading && error != null && (
        <div className="panel">
          <ErrorState error={error} onRetry={reload} />
        </div>
      )}

      {!loading && !error && data && (
        <>
          <SummaryCards summary={data.summary} />
          <RequestsChart points={data.timeseries} bucket={bucket} />
          {data.topPaths.length > 0 ? (
            <TopPathsTable paths={data.topPaths} />
          ) : (
            <div className="panel">
              <EmptyState
                title="No requests yet"
                description="Send a request to this API's gateway address and the busiest paths will show up here."
              />
            </div>
          )}
        </>
      )}
    </div>
  );
}
function KeysTab({ api }) {
  const { data, loading, error, reload } = useAsync(async () => {
    const [keys, usage] = await Promise.all([
      keysApi.list(api.id),
      analyticsApi.keys({ apiId: api.id }),
    ]);
    const usageBy = {};
    for (const u of usage) usageBy[u.keyId] = u;
    return { keys, usageBy };
  }, [api.id]);
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [revoking, setRevoking] = useState(null);
  const [revokingBusy, setRevokingBusy] = useState(false);
  const [revokeError, setRevokeError] = useState(null);
  async function confirmRevoke() {
    if (!revoking) return;
    setRevokingBusy(true);
    setRevokeError(null);
    try {
      await keysApi.revoke(revoking.id);
      setRevoking(null);
      reload();
    } catch (err) {
      setRevokeError(err);
    } finally {
      setRevokingBusy(false);
    }
  }
  return (
    <div className="tab-pane">
      <div className="tab-pane__bar">
        <h2 className="tab-pane__heading">Keys</h2>
        <button
          type="button"
          className="btn btn--primary btn--sm"
          onClick={() => setCreateOpen(true)}
        >
          New key
        </button>
      </div>

      {loading && (
        <div className="panel">
          <SkeletonRows rows={4} cols={6} />
        </div>
      )}

      {!loading && error != null && (
        <div className="panel">
          <ErrorState error={error} onRetry={reload} />
        </div>
      )}

      {!loading && !error && data && data.keys.length === 0 && (
        <div className="panel">
          <EmptyState
            title="No keys"
            description="Callers need a key to use this API. Make one, copy it, and send it in the X-API-Key header."
            action={
              <button
                type="button"
                className="btn btn--primary"
                onClick={() => setCreateOpen(true)}
              >
                New key
              </button>
            }
          />
        </div>
      )}

      {!loading && !error && data && data.keys.length > 0 && (
        <div className="panel panel--flush">
          <KeysTable
            keys={data.keys}
            usageBy={data.usageBy}
            onEdit={(k) => setEditing(k)}
            onRevoke={(k) => {
              setRevokeError(null);
              setRevoking(k);
            }}
          />
        </div>
      )}

      <CreateKeyModal
        apiId={api.id}
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={() => reload()}
      />
      <EditKeyModal
        key={editing?.id ?? "none"}
        keyData={editing}
        onClose={() => setEditing(null)}
        onUpdated={() => reload()}
      />
      <ConfirmDialog
        open={revoking !== null}
        title="Revoke this key?"
        tone="danger"
        confirmLabel="Revoke"
        busy={revokingBusy}
        onConfirm={confirmRevoke}
        onCancel={() => (revokingBusy ? undefined : setRevoking(null))}
        message={
          <>
            <p>
              <strong>{revoking?.name}</strong> (
              <code className="mono">{revoking?.prefix}…</code>)
            </p>
            <p className="muted">
              The next request that uses it will get a 401. You can't undo this.
              Make a new key if the caller still needs access.
            </p>
            {revokeError instanceof ApiError && (
              <div className="banner banner--danger" role="alert">
                {revokeError.message}
              </div>
            )}
          </>
        }
      />
    </div>
  );
}
function RequestsTab({ api }) {
  const { data, loading, error, reload } = useAsync(async () => {
    const [recent, keys] = await Promise.all([
      analyticsApi.recent({ apiId: api.id, limit: 50 }),
      keysApi.list(api.id),
    ]);
    const keyNameBy = {};
    for (const k of keys) keyNameBy[k.id] = k.name;
    return { recent, keyNameBy };
  }, [api.id]);
  return (
    <div className="tab-pane">
      <div className="tab-pane__bar">
        <h2 className="tab-pane__heading">Latest 50 requests</h2>
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          onClick={reload}
        >
          Refresh
        </button>
      </div>

      {loading && (
        <div className="panel">
          <SkeletonRows rows={8} cols={7} />
        </div>
      )}

      {!loading && error != null && (
        <div className="panel">
          <ErrorState error={error} onRetry={reload} />
        </div>
      )}

      {!loading && !error && data && data.recent.length === 0 && (
        <div className="panel">
          <EmptyState
            title="No requests yet"
            description="Send a request to this API's gateway address and it will show up here."
          />
        </div>
      )}

      {!loading && !error && data && data.recent.length > 0 && (
        <div className="panel panel--flush log">
          <RecentRequestsTable
            requests={data.recent}
            keyNameBy={data.keyNameBy}
          />
        </div>
      )}
    </div>
  );
}
