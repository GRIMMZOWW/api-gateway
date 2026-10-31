import { formatMs, formatNumber, formatPercent } from "../utils/format";
/** Overview header: one plain sentence, then the same numbers in a row. */
export function SummaryCards({ summary }) {
  const failed = summary.errorCount + summary.unauthorizedCount;
  const plural = (n, one, many) => (n === 1 ? one : many);
  return (
    <section>
      {summary.totalRequests === 0 ? (
        <p className="lede">Nothing came through in this period.</p>
      ) : (
        <p className="lede">
          <strong>{formatNumber(summary.totalRequests)}</strong>{" "}
          {plural(summary.totalRequests, "request", "requests")} in this period.{" "}
          <strong>{formatNumber(summary.successCount)}</strong> went through,{" "}
          <strong>{formatNumber(summary.rateLimitedCount)}</strong>{" "}
          {plural(summary.rateLimitedCount, "was", "were")} rate limited and{" "}
          <strong>{formatNumber(failed)}</strong> failed or{" "}
          {plural(failed, "was", "were")} rejected.
        </p>
      )}
      <dl className="stats" style={{ marginTop: 20 }}>
        <div>
          <dt>Requests</dt>
          <dd>{formatNumber(summary.totalRequests)}</dd>
        </div>
        <div>
          <dt>Success rate</dt>
          <dd>{formatPercent(summary.successRate)}</dd>
        </div>
        <div className="is-warn">
          <dt>Rate limited</dt>
          <dd>{formatNumber(summary.rateLimitedCount)}</dd>
        </div>
        <div className="is-bad">
          <dt>Failed or rejected</dt>
          <dd>{formatNumber(failed)}</dd>
        </div>
        <div>
          <dt>Average latency</dt>
          <dd>{formatMs(summary.avgLatencyMs)}</dd>
        </div>
      </dl>
    </section>
  );
}
