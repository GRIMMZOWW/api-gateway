import { Link } from "react-router-dom";
import { apisApi } from "../api/apis";
import { analyticsApi } from "../api/analytics";
import { useAsync } from "../hooks/useAsync";
import { SkeletonRows } from "../components/Spinner";
import { EmptyState } from "../components/EmptyState";
import { ErrorState } from "../components/ErrorState";
import { SummaryCards } from "../components/SummaryCards";
import { formatNumber, formatPercent } from "../utils/format";
/** The backend reports per API, so the totals across APIs are added up here. */
function combine(rows) {
  const sum = (pick) => rows.reduce((n, r) => n + pick(r.s), 0);
  const total = sum((s) => s.totalRequests);
  const success = sum((s) => s.successCount);
  return {
    totalRequests: total,
    successCount: success,
    rateLimitedCount: sum((s) => s.rateLimitedCount),
    unauthorizedCount: sum((s) => s.unauthorizedCount),
    errorCount: sum((s) => s.errorCount),
    successRate: total === 0 ? 0 : (success / total) * 100,
    avgLatencyMs:
      total === 0
        ? 0
        : rows.reduce((n, r) => n + r.s.avgLatencyMs * r.s.totalRequests, 0) /
          total,
  };
}
export function OverviewPage() {
  const { data, loading, error, reload } = useAsync(async () => {
    const apis = await apisApi.list();
    const to = Date.now();
    const range = {
      from: new Date(to - 24 * 60 * 60 * 1000).toISOString(),
      to: new Date(to).toISOString(),
    };
    const sums = await Promise.all(
      apis.map((a) => analyticsApi.summary({ apiId: a.id, ...range })),
    );
    return apis.map((api, i) => ({ api, s: sums[i] }));
  }, []);
  return (
    <div className="page">
      <div className="page__header">
        <div>
          <h1 className="page__title">Overview</h1>
          <p className="page__subtitle">
            Everything that went through your APIs in the last 24 hours.
          </p>
        </div>
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          onClick={reload}
        >
          Refresh
        </button>
      </div>

      {loading && <SkeletonRows rows={4} cols={5} />}
      {!loading && error != null && (
        <ErrorState error={error} onRetry={reload} />
      )}

      {!loading && !error && data && data.length === 0 && (
        <EmptyState
          title="No APIs yet"
          description="Add the server you want to protect, then come back here to see its traffic."
          action={
            <Link to="/apis" className="btn btn--primary">
              Add an API
            </Link>
          }
        />
      )}

      {!loading && !error && data && data.length > 0 && (
        <div className="tab-pane">
          <SummaryCards summary={combine(data)} />
          <div className="panel panel--flush">
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>API</th>
                    <th>Status</th>
                    <th className="num">Requests</th>
                    <th className="num">Rate limited</th>
                    <th className="num">Failed</th>
                    <th className="num">Success rate</th>
                  </tr>
                </thead>
                <tbody>
                  {data.map(({ api, s }) => (
                    <tr key={api.id}>
                      <td>
                        <Link to={`/apis/${api.id}`}>{api.name}</Link>
                      </td>
                      <td>
                        <span
                          className={`status-pill status-pill--${api.enabled ? "active" : "inactive"}`}
                        >
                          {api.enabled ? "On" : "Off"}
                        </span>
                      </td>
                      <td className="num">{formatNumber(s.totalRequests)}</td>
                      <td className="num">
                        {formatNumber(s.rateLimitedCount)}
                      </td>
                      <td className="num">
                        {formatNumber(s.errorCount + s.unauthorizedCount)}
                      </td>
                      <td className="num">
                        {s.totalRequests === 0
                          ? "none"
                          : formatPercent(s.successRate)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
