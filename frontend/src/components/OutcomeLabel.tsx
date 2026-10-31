import type { CSSProperties } from "react";

/**
 * Outcome label mapping. Every outcome has BOTH a text label and a color —
 * color is never the sole signal (spec UX rule).
 */
const OUTCOMES: Record<string, { label: string; cls: string; dot: string }> = {
  SUCCESS: { label: "Success", cls: "outcome--success", dot: "#2b7a4b" },
  RATE_LIMITED: { label: "Rate limited", cls: "outcome--warning", dot: "#d9a13b" },
  UNAUTHORIZED: { label: "Unauthorized", cls: "outcome--danger", dot: "#b0352a" },
  UPSTREAM_ERROR: { label: "Upstream error", cls: "outcome--danger", dot: "#b0352a" },
  UPSTREAM_TIMEOUT: { label: "Upstream timeout", cls: "outcome--danger", dot: "#b0352a" },
  REJECTED: { label: "Rejected", cls: "outcome--muted", dot: "#5b656d" },
};

const FALLBACK = { label: "Unknown", cls: "outcome--muted", dot: "#5b656d" };

export function OutcomeLabel({ outcome }: { outcome: string }) {
  const info = OUTCOMES[outcome] ?? FALLBACK;
  const style = { "--dot": info.dot } as CSSProperties;
  return (
    <span className={`outcome ${info.cls}`} style={style}>
      <span className="outcome__dot" aria-hidden="true" />
      {info.label}
    </span>
  );
}

/** Color used for a given outcome (for charts). */
export function outcomeColor(outcome: string): string {
  return (OUTCOMES[outcome] ?? FALLBACK).dot;
}
