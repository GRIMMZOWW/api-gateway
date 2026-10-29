import type { ReactNode } from "react";

interface SpinnerProps {
  label?: string;
  /** Use `block` for full-width centered spinners (e.g. inside a panel). */
  block?: boolean;
}

/** A simple, self-contained CSS spinner. */
export function Spinner({ label, block }: SpinnerProps) {
  const content: ReactNode = (
    <span className="spinner">
      <span className="spinner__ring" aria-hidden="true" />
      {label && <span className="spinner__label">{label}</span>}
    </span>
  );
  if (block) return <div className="spinner--block">{content}</div>;
  return content;
}

/** A row of N placeholder bars used by tables/lists while loading. */
export function SkeletonRows({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <div className="skeleton-rows" aria-hidden="true">
      {Array.from({ length: rows }).map((_, r) => (
        <div
          className="skeleton-rows__row"
          key={r}
          style={{ ["--cols" as string]: String(cols) }}
        >
          {Array.from({ length: cols }).map((_, c) => (
            <div className="skeleton-rows__cell" key={c} />
          ))}
        </div>
      ))}
    </div>
  );
}
