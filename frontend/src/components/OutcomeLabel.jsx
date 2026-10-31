/** Outcome label mapping. */
const OUTCOMES = {
  SUCCESS: { label: "Success", cls: "outcome--success", dot: "#2b7a4b" },
  RATE_LIMITED: {
    label: "Rate limited",
    cls: "outcome--warning",
    dot: "#d9a13b",
  },
  UNAUTHORIZED: {
    label: "Unauthorized",
    cls: "outcome--danger",
    dot: "#b0352a",
  },
  UPSTREAM_ERROR: {
    label: "Upstream error",
    cls: "outcome--danger",
    dot: "#b0352a",
  },
  UPSTREAM_TIMEOUT: {
    label: "Upstream timeout",
    cls: "outcome--danger",
    dot: "#b0352a",
  },
  REJECTED: { label: "Rejected", cls: "outcome--muted", dot: "#5b656d" },
};
const FALLBACK = { label: "Unknown", cls: "outcome--muted", dot: "#5b656d" };
export function OutcomeLabel({ outcome }) {
  const info = OUTCOMES[outcome] ?? FALLBACK;
  const style = { "--dot": info.dot };
  return (
    <span className={`outcome ${info.cls}`} style={style}>
      <span className="outcome__dot" aria-hidden="true" />
      {info.label}
    </span>
  );
}
/** Color used for a given outcome (for charts). */
export function outcomeColor(outcome) {
  return (OUTCOMES[outcome] ?? FALLBACK).dot;
}
