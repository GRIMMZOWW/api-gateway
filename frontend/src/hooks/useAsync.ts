import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Generic async-data hook that captures the THREE spec states: loading,
 * error, and data. Re-runs whenever `deps` change. Abortable so navigating
 * away mid-flight doesn't surface a stale result.
 *
 * Usage:
 *   const { data, loading, error, reload } = useAsync(
 *     () => analyticsApi.summary({ apiId, ... }),
 *     [apiId, from, to]
 *   );
 */
export function useAsync<T>(
  fn: (signal: AbortSignal) => Promise<T>,
  deps: unknown[]
): {
  data: T | null;
  loading: boolean;
  error: unknown;
  reload: () => void;
} {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [nonce, setNonce] = useState(0);
  // Keep the latest fn in a ref so the effect closure is stable per deps+nonce.
  const fnRef = useRef(fn);
  fnRef.current = fn;

  const reload = useCallback(() => setNonce((n) => n + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    fnRef.current(controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return;
        setData(result);
      })
      .catch((err) => {
        if (controller.signal.aborted) return;
        if (err instanceof DOMException && err.name === "AbortError") return;
        setError(err);
      })
      .finally(() => {
        if (controller.signal.aborted) return;
        setLoading(false);
      });
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce]);

  return { data, loading, error, reload };
}
