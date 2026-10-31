import { Link } from "react-router-dom";

export function NotFoundPage() {
  return (
    <div className="empty-state notfound">
      <h1 className="page__title">That page doesn’t exist</h1>
      <p className="empty-state__desc">The address may be mistyped, or the page may have moved.</p>
      <Link to="/" className="btn btn--primary">Go to the home page</Link>
    </div>
  );
}
