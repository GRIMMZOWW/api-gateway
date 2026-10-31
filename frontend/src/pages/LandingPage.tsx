import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { REPO_URL } from "../site";
import { GateDiagram } from "../components/GateDiagram";

const FEATURES = [
  {
    title: "Keys you control",
    text: "Make as many keys as you need for each API. A key is shown once, stored as a hash, and can be revoked. A revoked key stops working on its very next request.",
  },
  {
    title: "A limit for every key",
    text: "Each key has a burst size and a refill rate. Callers see how many requests they have left in the response headers, and get a 429 when they run out.",
  },
  {
    title: "A record of every request",
    text: "Each call is logged with its path, status and how long it took. The dashboard turns that into a chart, your busiest paths and a list of the latest requests.",
  },
  {
    title: "Safer by default",
    text: "Your server’s address is checked so the gateway can’t be pointed at machines inside your network. You can only see your own APIs and keys.",
  },
];

const STEPS = [
  "Gatecraft hashes the key and looks it up. Unknown, revoked or expired keys get a 401.",
  "It checks that the key belongs to this API. If it doesn’t, the caller gets a 403.",
  "It takes a token from the key’s bucket. If the bucket is empty, the caller gets a 429 and a Retry-After header.",
  "It forwards the request to your server and passes the answer back. If your server is down the caller gets a 502, and a 504 if it is too slow.",
  "It writes a log line, which the dashboard reads.",
];

export function LandingPage() {
  const { token } = useAuth();
  return (
    <div className="landing">
      <section className="hero">
        <div className="hero__text">
          <h1 className="hero__title">Put a key in front of your API.</h1>
          <p className="hero__lead">
            Gatecraft is a small gateway. Callers send requests to it with a key. It checks the key,
            enforces a limit, passes the request to your server and records what happened.
          </p>
          <div className="hero__actions">
            {token ? (
              <Link to="/overview" className="btn btn--primary btn--lg">Open the dashboard</Link>
            ) : (
              <Link to="/register" className="btn btn--primary btn--lg">Create an account</Link>
            )}
            <Link to="/docs" className="btn btn--lg">Read the docs</Link>
          </div>
        </div>
        <div className="stage">
          <GateDiagram />
        </div>
      </section>

      <section className="block">
        <h2 className="block__title">What you get</h2>
        <div className="features">
          {FEATURES.map((f) => (
            <div key={f.title} className="feature">
              <h3 className="feature__title">{f.title}</h3>
              <p className="feature__text">{f.text}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="block block--split">
        <div>
          <h2 className="block__title">What happens to a request</h2>
          <p className="block__lead">
            Every call to <code className="mono">/gw/your-api</code> goes through the same five steps, in this order.
          </p>
          <p className="block__lead">
            <Link to="/docs#errors">See every error code in the docs.</Link>
          </p>
        </div>
        <ol className="flow">
          {STEPS.map((s) => (
            <li key={s}>{s}</li>
          ))}
        </ol>
      </section>

      <section className="block block--split">
        <div>
          <h2 className="block__title">What it doesn’t do yet</h2>
        </div>
        <div className="prose">
          <p>
            Gatecraft is a learning project, so it is honest about its limits. It runs as a single
            server, which means rate limits are counted in memory. Running two copies would give each
            key two limits. Sharing the counters between servers is the next thing to build.
          </p>
          <p>
            There are also no teams, no billing and no custom domains. The code is on{" "}
            <a href={REPO_URL} target="_blank" rel="noreferrer">GitHub</a> if you want to read how it works.
          </p>
        </div>
      </section>

      <section className="cta">
        <h2 className="cta__title">Try it with your own API</h2>
        <p className="cta__text">It takes about two minutes to add an API, make a key and send a request.</p>
        <Link to={token ? "/apis" : "/register"} className="btn btn--light btn--lg">
          {token ? "Go to your APIs" : "Create an account"}
        </Link>
      </section>
    </div>
  );
}
