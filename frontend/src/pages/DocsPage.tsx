import { Link } from "react-router-dom";
import { API_BASE_URL } from "../api/client";

const ERRORS: [string, string, string][] = [
  ["401", "MISSING_API_KEY", "The request has no X-API-Key header."],
  ["401", "INVALID_API_KEY", "The key is unknown, revoked or expired. All three get the same answer on purpose."],
  ["403", "KEY_NOT_FOR_API", "The key belongs to a different API, or the address doesn’t exist."],
  ["413", "BODY_TOO_LARGE", "The request body is bigger than 1 MB."],
  ["429", "RATE_LIMITED", "The key has no tokens left. Wait for the number of seconds in Retry-After."],
  ["502", "UPSTREAM_ERROR", "Gatecraft could not connect to your server."],
  ["503", "API_DISABLED", "The API is switched off in its Settings tab."],
  ["504", "UPSTREAM_TIMEOUT", "Your server took longer than the API’s timeout."],
];

const HEADERS: [string, string][] = [
  ["X-RateLimit-Limit", "The key’s burst size."],
  ["X-RateLimit-Remaining", "How many requests the key can still make right now."],
  ["Retry-After", "Only on a 429. Seconds to wait before trying again."],
  ["X-Request-Id", "A unique id for the request, useful when reporting a problem."],
];

export function DocsPage() {
  return (
    <div className="docs">
      <div className="docs__head">
        <h1 className="page__title">Docs</h1>
        <p className="page__subtitle">How to put an API behind Gatecraft, and how to call it once it is there.</p>
        <nav className="docs__toc" aria-label="On this page">
          <a href="#setup">Set up</a>
          <a href="#call">Call the gateway</a>
          <a href="#limits">Rate limits</a>
          <a href="#errors">Errors</a>
          <a href="#caps">Limits on your account</a>
        </nav>
      </div>

      <section id="setup" className="docs__section">
        <h2>Set up</h2>
        <p>
          <Link to="/register">Create an account</Link>, then add your API from the APIs page. You give it
          a name, a short slug such as <code className="mono">weather</code>, and the address of your server.
          Its gateway address becomes <code className="mono">/gw/weather</code>.
        </p>
        <p>
          Open the API, go to the Keys tab and create a key. Choose a burst size and a refill rate, then
          copy the key straight away. Gatecraft keeps only a hash of it, so it can’t show it to you again.
        </p>
      </section>

      <section id="call" className="docs__section">
        <h2>Call the gateway</h2>
        <p>
          Send your request to the gateway address instead of your server, and add the key in an
          <code className="mono"> X-API-Key</code> header. Everything after the slug is passed on to your server.
        </p>
        <pre className="code-block">{`curl -H "X-API-Key: YOUR_KEY" \\\n  ${API_BASE_URL}/gw/weather/v1/today`}</pre>
        <p>
          GET, POST, PUT, PATCH, DELETE, HEAD and OPTIONS all work. Whatever your server answers, including
          its own errors, is passed back unchanged.
        </p>
      </section>

      <section id="limits" className="docs__section">
        <h2>Rate limits</h2>
        <p>
          Every key has a bucket of tokens. <strong>Burst size</strong> is how many tokens the bucket holds,
          which is how many requests the key can make at once. <strong>Requests per second</strong> is how fast
          the bucket refills. Each request uses one token, and when the bucket is empty the key gets a 429
          until a token comes back.
        </p>
        <p>These headers come back on every response from the gateway:</p>
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Header</th><th>Meaning</th></tr></thead>
            <tbody>
              {HEADERS.map(([h, m]) => (
                <tr key={h}><td className="mono">{h}</td><td>{m}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section id="errors" className="docs__section">
        <h2>Errors</h2>
        <p>The gateway answers with these when it can’t pass a request on.</p>
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Status</th><th>Code</th><th>When it happens</th></tr></thead>
            <tbody>
              {ERRORS.map(([s, c, w]) => (
                <tr key={c}><td className="mono">{s}</td><td className="mono">{c}</td><td>{w}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section id="caps" className="docs__section">
        <h2>Limits on your account</h2>
        <ul className="docs__list">
          <li>Up to 10 APIs per account.</li>
          <li>Up to 20 active keys per API.</li>
          <li>Request bodies up to 1 MB.</li>
          <li>Charts and request history can look back up to 90 days.</li>
          <li>Sign-in attempts are limited to 5 a minute for each address.</li>
        </ul>
      </section>
    </div>
  );
}
