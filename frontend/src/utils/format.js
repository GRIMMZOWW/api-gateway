/** Small formatting helpers shared across components. */
/** Format an ISO-8601 instant as a local date/time. */
export function formatDateTime(iso) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}
/** Compact time-of-day for time-series axes. */
export function formatTime(iso) {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleTimeString(undefined, {
    hour: "2-digit",
    minute: "2-digit",
  });
}
/** Short date for day buckets. */
export function formatDateShort(iso) {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString(undefined, { month: "short", day: "2-digit" });
}
export function formatNumber(n) {
  if (!Number.isFinite(n)) return "—";
  return n.toLocaleString();
}
export function formatMs(ms) {
  if (!Number.isFinite(ms)) return "—";
  if (ms < 10) return `${ms.toFixed(1)} ms`;
  return `${Math.round(ms)} ms`;
}
export function formatPercent(rate) {
  if (!Number.isFinite(rate)) return "—";
  return `${rate.toFixed(1)}%`;
}
/** Parse a BigDecimal that may arrive as a number or a string. */
export function toNumber(value) {
  if (value === null || value === undefined) return null;
  const n = typeof value === "string" ? parseFloat(value) : value;
  return Number.isFinite(n) ? n : null;
}
/** Convert a Date to the datetime-local input format (no seconds). */
export function toLocalInput(d) {
  const pad = (x) => String(x).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
/** Convert a datetime-local input value to an ISO-8601 instant, or undefined. */
export function localInputToIso(value) {
  if (!value) return undefined;
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return undefined;
  return d.toISOString();
}
