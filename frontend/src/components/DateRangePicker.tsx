export type RangeKey = "1h" | "24h" | "7d" | "30d";

interface RangeOption {
  key: RangeKey;
  label: string;
  /** Milliseconds from `to` back to `from`. */
  ms: number;
  /** Recommended analytics bucket. */
  bucket: "hour" | "day";
}

export const RANGE_OPTIONS: RangeOption[] = [
  { key: "1h", label: "1 hour", ms: 60 * 60 * 1000, bucket: "hour" },
  { key: "24h", label: "24 hours", ms: 24 * 60 * 60 * 1000, bucket: "hour" },
  { key: "7d", label: "7 days", ms: 7 * 24 * 60 * 60 * 1000, bucket: "hour" },
  { key: "30d", label: "30 days", ms: 30 * 24 * 60 * 60 * 1000, bucket: "day" },
];

export function rangeByKey(key: RangeKey): RangeOption {
  return RANGE_OPTIONS.find((o) => o.key === key) ?? RANGE_OPTIONS[1];
}

/** Returns { from, to } as ISO-8601 UTC instants for the given range key. */
export function rangeWindow(key: RangeKey): { from: string; to: string } {
  const opt = rangeByKey(key);
  const to = Date.now();
  const from = to - opt.ms;
  return { from: new Date(from).toISOString(), to: new Date(to).toISOString() };
}

interface DateRangePickerProps {
  value: RangeKey;
  onChange: (key: RangeKey) => void;
}

export function DateRangePicker({ value, onChange }: DateRangePickerProps) {
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
