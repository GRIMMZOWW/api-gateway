import type { AnalyticsSummary } from "../api/analytics";
import { formatMs, formatNumber, formatPercent } from "../utils/format";

interface SummaryCardsProps {
  summary: AnalyticsSummary;
}

interface Card {
  label: string;
  value: string;
  tone: "neutral" | "success" | "warning" | "danger";
  hint: string;
}

/**
 * The five summary cards on the Overview tab: total requests, success rate,
 * rate-limited, errors, average latency. Each card has a tone color AND a
 * text label so color is never the sole signal.
 */
export function SummaryCards({ summary }: SummaryCardsProps) {
  const cards: Card[] = [
    {
      label: "Total requests",
      value: formatNumber(summary.totalRequests),
      tone: "neutral",
      hint: "every request in range",
    },
    {
      label: "Success rate",
      value: formatPercent(summary.successRate),
      tone: "success",
      hint: "success / total",
    },
    {
      label: "Rate limited",
      value: formatNumber(summary.rateLimitedCount),
      tone: "warning",
      hint: "429 responses",
    },
    {
      label: "Errors",
      value: formatNumber(summary.errorCount + summary.unauthorizedCount),
      tone: "danger",
      hint: "errors + unauthorized",
    },
    {
      label: "Average latency",
      value: formatMs(summary.avgLatencyMs),
      tone: "neutral",
      hint: "mean over range",
    },
  ];

  return (
    <div className="summary-cards">
      {cards.map((c) => (
        <div key={c.label} className={`summary-card summary-card--${c.tone}`}>
          <div className="summary-card__label">{c.label}</div>
          <div className="summary-card__value">{c.value}</div>
          <div className="summary-card__hint">{c.hint}</div>
        </div>
      ))}
    </div>
  );
}
