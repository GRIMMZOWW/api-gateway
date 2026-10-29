import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { TimeseriesPoint } from "../api/analytics";
import { formatDateShort, formatTime } from "../utils/format";

interface RequestsChartProps {
  points: TimeseriesPoint[];
  bucket: "hour" | "day";
}

interface ChartRow {
  label: string;
  Success: number;
  "Rate limited": number;
  Errors: number;
}

const SERIES = [
  { key: "Success", color: "#16a34a" },
  { key: "Rate limited", color: "#d97706" },
  { key: "Errors", color: "#dc2626" },
] as const;

/**
 * Stacked bar chart of requests over time, split into success / rate-limited /
 * errors. The legend spells the labels out in text (color is never the sole
 * signal).
 */
export function RequestsChart({ points, bucket }: RequestsChartProps) {
  const rows: ChartRow[] = points.map((p) => ({
    label: bucket === "day" ? formatDateShort(p.bucketStart) : formatTime(p.bucketStart),
    Success: p.success,
    "Rate limited": p.rateLimited,
    Errors: p.errors,
  }));

  return (
    <div className="chart">
      <div className="chart__header">
        <h3 className="chart__title">Requests over time</h3>
        <div className="chart__legend-static" aria-hidden="false">
          {SERIES.map((s) => (
            <span className="chart__legend-item" key={s.key}>
              <span
                className="chart__legend-swatch"
                style={{ background: s.color }}
                aria-hidden="true"
              />
              {s.key}
            </span>
          ))}
        </div>
      </div>
      <div className="chart__body">
        <ResponsiveContainer width="100%" height={300}>
          <BarChart data={rows} margin={{ top: 8, right: 12, bottom: 4, left: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
            <XAxis
              dataKey="label"
              tick={{ fill: "var(--muted)", fontSize: 11 }}
              tickLine={false}
              axisLine={{ stroke: "var(--border)" }}
              minTickGap={16}
            />
            <YAxis
              allowDecimals={false}
              tick={{ fill: "var(--muted)", fontSize: 11 }}
              tickLine={false}
              axisLine={{ stroke: "var(--border)" }}
              width={40}
            />
            <Tooltip
              cursor={{ fill: "var(--accent-soft)" }}
              contentStyle={{
                background: "var(--surface-2)",
                border: "1px solid var(--border)",
                borderRadius: 8,
                color: "var(--fg)",
                fontSize: 12,
              }}
            />
            <Legend wrapperStyle={{ display: "none" }} />
            {SERIES.map((s) => (
              <Bar
                key={s.key}
                dataKey={s.key}
                stackId="requests"
                fill={s.color}
                radius={s.key === "Success" ? [0, 0, 0, 0] : [3, 3, 0, 0]}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
