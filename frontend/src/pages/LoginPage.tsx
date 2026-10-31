import { useState } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { ApiError } from "../api/client";
import { authApi } from "../api/auth";
import { useAuth } from "../auth/AuthContext";
import { Spinner } from "../components/Spinner";
import { AuthAside } from "../components/AuthAside";

export function LoginPage() {
  const { login, token, ready } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? "/overview";

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>(null);

  // If already logged in, skip the form.
  if (ready && token) return <Navigate to={from} replace />;

  function fieldError(name: string): string | undefined {
    if (error instanceof ApiError && error.fields) return error.fields[name];
    return undefined;
  }
  const banner =
    error instanceof ApiError && !error.fields ? error.message : null;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const res = await authApi.login({ email: email.trim(), password });
      await login(res.token);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <AuthAside />
      <div className="auth-main">
      <form className="auth-card" onSubmit={submit} noValidate>
        <h1 className="auth-card__title">Sign in</h1>
        <p className="auth-card__subtitle">Use the email and password you registered with.</p>

        {banner && (
          <div className="banner banner--danger" role="alert">
            {banner}
          </div>
        )}

        <label className="field">
          <span className="field__label">Email</span>
          <input
            className="input"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoFocus
          />
          {fieldError("email") && (
            <span className="field__error">{fieldError("email")}</span>
          )}
        </label>

        <label className="field">
          <span className="field__label">Password</span>
          <input
            className="input"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          {fieldError("password") && (
            <span className="field__error">{fieldError("password")}</span>
          )}
        </label>

        <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
          {submitting ? <Spinner label="Signing in" /> : "Sign in"}
        </button>

        <p className="auth-card__switch">
          New here? <Link to="/register">Create an account</Link>
        </p>
      </form>
      </div>
    </div>
  );
}
