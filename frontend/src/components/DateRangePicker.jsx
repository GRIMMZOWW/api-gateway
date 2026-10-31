export const RANGE_OPTIONS = [
  { key: "1h", label: "1 hour", ms: 60 * 60 * 1000, bucket: "hour" },
  { key: "24h", label: "24 hours", ms: 24 * 60 * 60 * 1000, bucket: "hour" },
  { key: "7d", label: "7 days", ms: 7 * 24 * 60 * 60 * 1000, bucket: "hour" },
  { key: "30d", label: "30 days", ms: 30 * 24 * 60 * 60 * 1000, bucket: "day" },
];
export function rangeByKey(key) {
  return RANGE_OPTIONS.find((o) => o.key === key) ?? RANGE_OPTIONS[1];
}
/** Returns { from, to } as ISO-8601 UTC instants for the given range key. */
export function rangeWindow(key) {
  const opt = rangeByKey(key);
  const to = Date.now();
  const from = to - opt.ms;
  return { from: new Date(from).toISOString(), to: new Date(to).toISOString() };
}
export function DateRangePicker({ value, onChange }) {
  return (
    <div className="range-picker" role="group" aria-label="Date range">
      {RANGE_OPTIONS.map((opt) => (
        <button
          key={opt.key}
          type="button"
          className={`range-picker__btn${opt.key === value ? " is-active" : ""}`}
          aria-pressed={opt.key === value}
          onClick={() => onChange(opt.key)}
        >
          {opt.label}
        </button>
      ))}
    </div>
  );
}
