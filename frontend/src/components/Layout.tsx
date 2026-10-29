import { NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

interface LayoutProps {
  children: React.ReactNode;
}

/** App shell: top nav with the APIs link, user menu, and logout. */
export function Layout({ children }: LayoutProps) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate("/login", { replace: true });
  }

  return (
    <div className="app">
      <header className="topbar">
        <div className="topbar__inner">
          <NavLink to="/apis" className="topbar__brand">
            <span className="topbar__mark" aria-hidden="true">◆</span>
            API Gateway
          </NavLink>
          <nav className="topbar__nav">
            <NavLink
              to="/apis"
              className={({ isActive }) =>
                `topbar__link${isActive ? " is-active" : ""}`
              }
            >
              APIs
            </NavLink>
          </nav>
          <div className="topbar__user">
            {user && (
              <span className="topbar__user-name" title={user.email}>
                {user.displayName}
              </span>
            )}
            <button
              type="button"
              className="btn btn--ghost"
              onClick={handleLogout}
            >
              Log out
            </button>
          </div>
        </div>
      </header>
      <main className="content">{children}</main>
    </div>
  );
}
