import type { TopPath } from "../api/analytics";
import { formatMs, formatNumber } from "../utils/format";

interface TopPathsTableProps {
  paths: TopPath[];
}

/** Top-paths table on the Overview tab. */
export function TopPathsTable({ paths }: TopPathsTableProps) {
  return (
    <div className="panel">
      <div className="panel__header">
        <h3 className="panel__title">Busiest paths</h3>
      </div>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Method</th>
              <th>Path</th>
              <th className="num">Requests</th>
              <th className="num">Average time</th>
            </tr>
          </thead>
          <tbody>
            {paths.map((p, i) => (
              <tr key={`${p.method}:${p.path}:${i}`}>
                <td>
                  <span className="method method--sm">{p.method}</span>
                </td>
                <td className="mono">{p.path}</td>
                <td className="num">{formatNumber(p.count)}</td>
                <td className="num">{formatMs(p.avgLatencyMs)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
