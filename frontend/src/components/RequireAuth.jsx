import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
/** Route guard. */
export function RequireAuth({ children }) {
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
