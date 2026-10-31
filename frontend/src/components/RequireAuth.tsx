import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

/**
 * Route guard. Renders a splash while the initial /me check runs, so we don't
 * bounce a logged-in user to /login on a hard reload. Once ready, redirects
 * unauthenticated users to /login (preserving where they were headed).
 */
export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { token, ready } = useAuth();
  const location = useLocation();

  if (!ready) {
    return (
      <div className="splash" role="status" aria-live="polite">
        <span className="spinner__ring spinner__ring--lg" aria-hidden="true" />
        <p>Loading…</p>
      </div>
    );
  }

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <>{children}</>;
}
