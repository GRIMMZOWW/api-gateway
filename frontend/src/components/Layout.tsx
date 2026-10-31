import { Link, NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Footer } from "./Footer";
import { Logo } from "./Logo";

interface LayoutProps {
  children: React.ReactNode;
}

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `topbar__link${isActive ? " is-active" : ""}`;

/** Shell for every page. The top bar changes depending on whether someone is signed in. */
export function Layout({ children }: LayoutProps) {
  const { token, user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate("/", { replace: true });
  }

  return (
    <div className="app">
      <a className="skip" href="#main">Skip to content</a>
      <header className="topbar">
        <div className="topbar__inner">
          <Link to={token ? "/overview" : "/"} className="topbar__brand" aria-label="Gatecraft home">
            <Logo />
          </Link>
          <nav className="topbar__nav" aria-label="Main">
            {token && <NavLink to="/overview" className={linkClass}>Overview</NavLink>}
            {token && <NavLink to="/apis" className={linkClass}>APIs</NavLink>}
            <NavLink to="/docs" className={linkClass}>Docs</NavLink>
          </nav>
          <div className="topbar__user">
            {token ? (
              <>
                {user && (
                  <span className="topbar__user-name" title={user.email}>
                    {user.displayName}
                  </span>
                )}
                <button type="button" className="btn btn--ghost" onClick={handleLogout}>
                  Sign out
                </button>
              </>
            ) : (
              <>
                <Link to="/login" className="topbar__link">Sign in</Link>
                <Link to="/register" className="btn btn--light topbar__cta">Create account</Link>
              </>
            )}
          </div>
        </div>
      </header>
      <main id="main" className="content">{children}</main>
      <Footer />
    </div>
  );
}
