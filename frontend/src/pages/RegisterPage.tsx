import { useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { ApiError } from "../api/client";
import { authApi } from "../api/auth";
import { useAuth } from "../auth/AuthContext";
import { Spinner } from "../components/Spinner";
import { AuthAside } from "../components/AuthAside";

export function RegisterPage() {
  const { login, token, ready } = useAuth();
  const navigate = useNavigate();

  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>(null);

  if (ready && token) return <Navigate to="/overview" replace />;

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
      const res = await authApi.register({
        email: email.trim(),
        password,
        displayName: displayName.trim(),
      });
      await login(res.token);
      navigate("/overview", { replace: true });
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
        <h1 className="auth-card__title">Create an account</h1>
        <p className="auth-card__subtitle">Takes a few seconds. Passwords need at least 8 characters.</p>

        {banner && (
          <div className="banner banner--danger" role="alert">
            {banner}
          </div>
        )}

        <label className="field">
          <span className="field__label">Your name</span>
          <input
            className="input"
            type="text"
            autoComplete="name"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            required
            autoFocus
            maxLength={80}
          />
          {fieldError("displayName") && (
            <span className="field__error">{fieldError("displayName")}</span>
          )}
        </label>

        <label className="field">
          <span className="field__label">Email</span>
          <input
            className="input"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
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
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
          />
          {fieldError("password") && (
            <span className="field__error">{fieldError("password")}</span>
          )}
        </label>

        <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
          {submitting ? <Spinner label="Creating account" /> : "Create account"}
        </button>

        <p className="auth-card__switch">
          Already registered? <Link to="/login">Sign in</Link>
        </p>
      </form>
      </div>
    </div>
  );
}
