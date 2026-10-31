/** The ONE fetch wrapper for the dashboard. */
/** Error thrown for any non-2xx response. */
export class ApiError extends Error {
  constructor(status, body) {
    super(body.message ?? `${status} ${body.code ?? "ERROR"}`);
    this.name = "ApiError";
    this.status = status;
    this.code = body.code ?? "ERROR";
    this.requestId = body.requestId;
    this.fields = body.fields;
  }
}
let tokenSink = {
  get: () => null,
  set: () => {},
  clear: () => {},
};
/** Called once from AuthContext at startup to wire the in-memory token store. */
export function configureTokenSink(sink) {
  tokenSink = sink;
}
const BASE_URL = import.meta.env.VITE_API_BASE ?? "http://localhost:8080";
function buildUrl(path, query) {
  const url = new URL(path, BASE_URL);
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined && v !== null && v !== "") {
        url.searchParams.set(k, v);
      }
    }
  }
  return url.toString();
}
async function parseError(resp) {
  let body;
  try {
    const json = await resp.json();
    if (json && typeof json === "object" && json.error) {
      body = json.error;
    } else if (json && typeof json === "object") {
      body = json;
    } else {
      body = { code: "ERROR", message: resp.statusText };
    }
  } catch {
    body = {
      code: "ERROR",
      message: resp.statusText || `Request failed (${resp.status})`,
    };
  }
  if (!body.code) body.code = "ERROR";
  if (!body.message)
    body.message = resp.statusText || `Request failed (${resp.status})`;
  return new ApiError(resp.status, body);
}
async function request(method, path, opts = {}) {
  const token = tokenSink.get();
  const headers = { Accept: "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  let bodyStr;
  if (opts.body !== undefined) {
    headers["Content-Type"] = "application/json";
    bodyStr = JSON.stringify(opts.body);
  }
  let resp;
  try {
    resp = await fetch(buildUrl(path, opts.query), {
      method,
      headers,
      body: bodyStr,
      signal: opts.signal,
    });
  } catch (err) {
    // Network error / aborted. Don't touch the token.
    if (err instanceof DOMException && err.name === "AbortError") throw err;
    throw new ApiError(0, {
      code: "NETWORK_ERROR",
      message: "Could not reach the server. Check your connection.",
    });
  }
  if (resp.status === 401) {
    tokenSink.clear();
    if (
      typeof window !== "undefined" &&
      !window.location.pathname.startsWith("/login")
    ) {
      window.location.assign("/login");
    }
    throw await parseError(resp);
  }
  if (resp.status === 204) {
    return undefined;
  }
  if (!resp.ok) {
    throw await parseError(resp);
  }
  if (resp.status === 201 || resp.status === 200) {
    const ct = resp.headers.get("content-type") ?? "";
    if (ct.includes("application/json")) {
      return await resp.json();
    }
    return await resp.text();
  }
  // Other 2xx with no body.
  return undefined;
}
export const api = {
  get: (path, query, signal) => request("GET", path, { query, signal }),
  post: (path, body, query) => request("POST", path, { body, query }),
  patch: (path, body) => request("PATCH", path, { body }),
  del: (path) => request("DELETE", path),
};
export const API_BASE_URL = BASE_URL;
