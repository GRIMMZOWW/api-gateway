import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { configureTokenSink } from "../api/client";
import { authApi } from "../api/auth";
/** Auth state. */
const STORAGE_KEY = "api-gateway.token";
const AuthContext = createContext(undefined);
function readStoredToken() {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}
function writeStoredToken(token) {
  try {
    if (token) localStorage.setItem(STORAGE_KEY, token);
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    /* ignore quota / privacy-mode errors */
  }
}
export function AuthProvider({ children }) {
  // The in-memory token: source of truth for the API client.
  const tokenRef = useRef(readStoredToken());
  const [token, setToken] = useState(tokenRef.current);
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);
  // Wire the in-memory token store into the API client exactly once.
  // useMemo keeps the sink stable across renders so the module-level
  // singleton never holds a stale closure.
  const sink = useMemo(
    () => ({
      get: () => tokenRef.current,
      set: (t) => {
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
    [],
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
  const login = useCallback(
    async (newToken) => {
      sink.set(newToken);
      const me = await authApi.me();
      setUser(me);
    },
    [sink],
  );
  const logout = useCallback(() => {
    sink.clear();
  }, [sink]);
  const value = useMemo(
    () => ({ token, user, ready, login, logout }),
    [token, user, ready, login, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside an <AuthProvider>");
  return ctx;
}
