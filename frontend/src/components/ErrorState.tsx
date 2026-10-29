import { ApiError } from "../api/client";

interface ErrorStateProps {
  error: unknown;
  /** Retry handler — shown as a button if present. */
  onRetry?: () => void;
}

function messageOf(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 0) return error.message;
    return error.message || `Request failed (${error.status} ${error.code})`;
  }
  if (error instanceof Error) return error.message;
  return "Something went wrong.";
}

export function ErrorState({ error, onRetry }: ErrorStateProps) {
  return (
    <div className="error-state" role="alert">
      <div className="error-state__icon" aria-hidden="true">!</div>
      <p className="error-state__msg">{messageOf(error)}</p>
      {onRetry && (
        <button type="button" className="btn btn--default" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}
