import type { RecentRequest } from "../api/analytics";
import { OutcomeLabel } from "./OutcomeLabel";
import { formatDateTime, formatMs } from "../utils/format";

interface RecentRequestsTableProps {
  requests: RecentRequest[];
  keyNameBy: Record<number, string | undefined>;
}

/** The 50 newest requests table on the Requests tab. */
export function RecentRequestsTable({ requests, keyNameBy }: RecentRequestsTableProps) {
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Time</th>
            <th>Method</th>
            <th>Path</th>
            <th className="num">Status</th>
            <th>Outcome</th>
            <th className="num">Latency</th>
            <th>Key</th>
          </tr>
        </thead>
        <tbody>
          {requests.map((r) => (
            <tr key={r.id}>
              <td className="muted">{formatDateTime(r.createdAt)}</td>
              <td>
                <span className="method method--sm">{r.method}</span>
              </td>
              <td className="mono">{r.path}</td>
              <td className="num">{r.statusCode || "—"}</td>
              <td>
                <OutcomeLabel outcome={r.outcome} />
              </td>
              <td className="num">{formatMs(r.latencyMs)}</td>
              <td>
                {r.keyId == null ? (
                  <span className="muted">—</span>
                ) : (
                  keyNameBy[r.keyId] ?? <span className="muted">#{r.keyId}</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
