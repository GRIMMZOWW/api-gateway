/**
 * The ONE fetch wrapper for the dashboard.
 *
 * - Reads the base URL from VITE_API_BASE (default http://localhost:8080).
 * - Adds `Authorization: Bearer <token>` from the in-memory token store.
 * - On 401: clears the token, redirects to /login.
 * - Parses the spec error envelope `{"error":{code,message,requestId,fields}}`
 *   and throws a typed {@link ApiError}.
 */

export interface ApiErrorJson {
  code: string;
  message: string;
  requestId?: string;
  fields?: Record<string, string>;
}

/**
 * Error thrown for any non-2xx response. Carries the parsed spec error body.
 * The `fields` map is populated only for 400 validation responses.
 */
export class ApiError extends Error implements ApiErrorJson {
  readonly status: number;
  readonly code: string;
  readonly requestId?: string;
  readonly fields?: Record<string, string>;

  constructor(status: number, body: ApiErrorJson) {
    super(body.message ?? `${status} ${body.code ?? "ERROR"}`);
    this.name = "ApiError";
    this.status = status;
    this.code = body.code ?? "ERROR";
    this.requestId = body.requestId;
    this.fields = body.fields;
  }
}

/** A small token store kept in memory; the AuthContext writes here. */
interface TokenSink {
  get(): string | null;
  set(token: string | null): void;
  clear(): void;
}

let tokenSink: TokenSink = {
  get: () => null,
  set: () => {},
  clear: () => {},
};

/** Called once from AuthContext at startup to wire the in-memory token store. */
export function configureTokenSink(sink: TokenSink): void {
  tokenSink = sink;
}

const BASE_URL =
  import.meta.env.VITE_API_BASE ?? "http://localhost:8080";

function buildUrl(path: string, query?: Record<string, string | undefined>): string {
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

async function parseError(resp: Response): Promise<ApiError> {
  let body: ApiErrorJson;
  try {
    const json = await resp.json();
    if (json && typeof json === "object" && json.error) {
      body = json.error as ApiErrorJson;
    } else if (json && typeof json === "object") {
      body = json as ApiErrorJson;
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
  if (!body.message) body.message = resp.statusText || `Request failed (${resp.status})`;
  return new ApiError(resp.status, body);
}

async function request<T>(
  method: "GET" | "POST" | "PATCH" | "DELETE",
  path: string,
  opts: {
    body?: unknown;
    query?: Record<string, string | undefined>;
    signal?: AbortSignal;
  } = {}
): Promise<T> {
  const token = tokenSink.get();
  const headers: Record<string, string> = { Accept: "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  let bodyStr: string | undefined;
  if (opts.body !== undefined) {
    headers["Content-Type"] = "application/json";
    bodyStr = JSON.stringify(opts.body);
  }

  let resp: Response;
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
    if (typeof window !== "undefined" && !window.location.pathname.startsWith("/login")) {
      window.location.assign("/login");
    }
    throw await parseError(resp);
  }

  if (resp.status === 204) {
    return undefined as T;
  }

  if (!resp.ok) {
    throw await parseError(resp);
  }

  if (resp.status === 201 || resp.status === 200) {
    const ct = resp.headers.get("content-type") ?? "";
    if (ct.includes("application/json")) {
      return (await resp.json()) as T;
    }
    return (await resp.text()) as unknown as T;
  }

  // Other 2xx with no body.
  return undefined as T;
}

export const api = {
  get: <T>(path: string, query?: Record<string, string | undefined>, signal?: AbortSignal) =>
    request<T>("GET", path, { query, signal }),
  post: <T>(path: string, body?: unknown, query?: Record<string, string | undefined>) =>
    request<T>("POST", path, { body, query }),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, { body }),
  del: <T>(path: string) => request<T>("DELETE", path),
};

export const API_BASE_URL = BASE_URL;
