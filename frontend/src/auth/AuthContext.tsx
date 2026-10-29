import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { configureTokenSink } from "../api/client";
import { authApi, type UserResponse } from "../api/auth";

/**
 * Auth state.
 *
 * The JWT is kept in BOTH memory (`tokenRef`) and `localStorage`. The
 * in-memory copy is the source of truth for the API client; localStorage
 * survives a page reload. The token never goes into a cookie.
 */

const STORAGE_KEY = "api-gateway.token";

interface AuthContextValue {
  token: string | null;
  user: UserResponse | null;
  ready: boolean; // true once the initial /me check has resolved
  login: (token: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function readStoredToken(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function writeStoredToken(token: string | null): void {
  try {
    if (token) localStorage.setItem(STORAGE_KEY, token);
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    /* ignore quota / privacy-mode errors */
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  // The in-memory token: source of truth for the API client.
  const tokenRef = useRef<string | null>(readStoredToken());
  const [token, setToken] = useState<string | null>(tokenRef.current);
  const [user, setUser] = useState<UserResponse | null>(null);
  const [ready, setReady] = useState(false);

  // Wire the in-memory token store into the API client exactly once.
  // useMemo keeps the sink stable across renders so the module-level
  // singleton never holds a stale closure.
  const sink = useMemo(
    () => ({
      get: () => tokenRef.current,
      set: (t: string | null) => {
        tokenRef.current = t;
        writeStoredToken(t);
        setToken(t);
      },
      clear: () => {
        tokenRef.current = null;
        writeStoredToken(null);
        setToken(null);
        setUser(null);
      },
    }),
    []
  );
  useEffect(() => {
    configureTokenSink(sink);
  }, [sink]);

  // On mount: if we have a stored token, validate it against /me so a stale
  // or expired token is dropped immediately. Either way, mark ready.
  useEffect(() => {
    let cancelled = false;
    const stored = tokenRef.current;
    if (!stored) {
      setReady(true);
      return;
    }
    authApi
      .me()
      .then((u) => {
        if (!cancelled) {
          setUser(u);
          setReady(true);
        }
      })
      .catch(() => {
        // 401 already cleared the token via the client; just mark ready.
        if (!cancelled) setReady(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (newToken: string) => {
    sink.set(newToken);
    const me = await authApi.me();
    setUser(me);
  }, [sink]);

  const logout = useCallback(() => {
    sink.clear();
  }, [sink]);

  const value = useMemo<AuthContextValue>(
    () => ({ token, user, ready, login, logout }),
    [token, user, ready, login, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside an <AuthProvider>");
  return ctx;
}
