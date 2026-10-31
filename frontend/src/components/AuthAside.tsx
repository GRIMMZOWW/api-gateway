import { Link } from "react-router-dom";
import { GateDiagram } from "./GateDiagram";
import { Logo } from "./Logo";

/** Left panel on the sign-in and register pages. */
export function AuthAside() {
  return (
    <aside className="auth-aside">
      <Link to="/" className="auth-aside__brand" aria-label="Gatecraft home">
        <Logo />
      </Link>
      <p className="auth-aside__text">
        Put your API behind a key. Limit how fast each key can call it. See what got through.
      </p>
      <GateDiagram />
    </aside>
  );
}
