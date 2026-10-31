import { formatDateTime, formatNumber, toNumber } from "../utils/format";
/** Keys table: limits, status, last use and request count. */
export function KeysTable({ keys, usageBy, onEdit, onRevoke }) {
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Name</th>
            <th>Starts with</th>
            <th className="num">Burst</th>
            <th className="num">Per second</th>
            <th>Status</th>
            <th>Expires</th>
            <th>Last used</th>
            <th className="num">Requests</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {keys.map((k) => {
            const usage = usageBy[k.id];
            return (
              <tr key={k.id}>
                <td>{k.name}</td>
                <td className="mono">{k.prefix}…</td>
                <td className="num">{k.capacity}</td>
                <td className="num">{toNumber(k.refillPerSec) ?? "—"}</td>
                <td>
                  <span
                    className={`status-pill status-pill--${k.status === "ACTIVE" ? "active" : "revoked"}`}
                  >
                    {k.status === "ACTIVE" ? "Active" : "Revoked"}
                  </span>
                </td>
                <td>{k.expiresAt ? formatDateTime(k.expiresAt) : "Never"}</td>
                <td>
                  {k.lastUsedAt ? formatDateTime(k.lastUsedAt) : "Not used"}
                </td>
                <td className="num">
                  {usage ? formatNumber(usage.total) : "—"}
                </td>
                <td className="row-actions">
                  <button
                    type="button"
                    className="btn btn--ghost btn--sm"
                    onClick={() => onEdit(k)}
                    disabled={k.status !== "ACTIVE"}
                  >
                    Edit
                  </button>
                  <button
                    type="button"
                    className="btn btn--danger-ghost btn--sm"
                    onClick={() => onRevoke(k)}
                    disabled={k.status !== "ACTIVE"}
                  >
                    Revoke
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
