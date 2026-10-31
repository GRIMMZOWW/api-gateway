import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { REPO_URL } from "../site";
import { Logo } from "./Logo";

export function Footer() {
  const { token } = useAuth();
  return (
    <footer className="footer">
      <div className="footer__inner">
        <div>
          <Logo />
          <p className="footer__note">
            A learning project. Built with Spring Boot, MySQL and React.
          </p>
        </div>
        <nav className="footer__links" aria-label="Footer">
          <Link to="/docs">Docs</Link>
          {token ? <Link to="/overview">Overview</Link> : <Link to="/login">Sign in</Link>}
          <a href={REPO_URL} target="_blank" rel="noreferrer">Source on GitHub</a>
        </nav>
      </div>
    </footer>
  );
}
