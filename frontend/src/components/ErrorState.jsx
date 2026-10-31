import { ApiError } from "../api/client";
function messageOf(error) {
  if (error instanceof ApiError) {
    if (error.status === 0) return error.message;
    return error.message || `Request failed (${error.status} ${error.code})`;
  }
  if (error instanceof Error) return error.message;
  return "Something went wrong. Try again in a moment.";
}
export function ErrorState({ error, onRetry }) {
  return (
    <div className="error-state" role="alert">
      <p className="error-state__msg">{messageOf(error)}</p>
      {onRetry && (
        <button type="button" className="btn btn--default" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}
