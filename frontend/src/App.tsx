import { Navigate, Outlet, createBrowserRouter, RouterProvider } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { RequireAuth } from "./components/RequireAuth";
import { Layout } from "./components/Layout";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { ApisPage } from "./pages/ApisPage";
import { ApiDetailPage } from "./pages/ApiDetailPage";

/**
 * Router (library mode). Plain components with useEffect/useState — no
 * framework-mode data loaders. The protected routes are nested under a
 * layout route that wraps <RequireAuth> + <Layout><Outlet/></Layout>.
 */
const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/register", element: <RegisterPage /> },
  {
    path: "/",
    element: (
      <RequireAuth>
        <Layout>
          <Outlet />
        </Layout>
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/apis" replace /> },
      { path: "apis", element: <ApisPage /> },
      { path: "apis/:id", element: <ApiDetailPage /> },
      { path: "*", element: <Navigate to="/apis" replace /> },
    ],
  },
]);

export function App() {
  return (
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  );
}
