import { Outlet, createBrowserRouter, RouterProvider } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { RequireAuth } from "./components/RequireAuth";
import { Layout } from "./components/Layout";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { ApisPage } from "./pages/ApisPage";
import { ApiDetailPage } from "./pages/ApiDetailPage";
import { LandingPage } from "./pages/LandingPage";
import { DocsPage } from "./pages/DocsPage";
import { OverviewPage } from "./pages/OverviewPage";
import { NotFoundPage } from "./pages/NotFoundPage";
/** Router (library mode). */
const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/register", element: <RegisterPage /> },
  {
    element: (
      <Layout>
        <Outlet />
      </Layout>
    ),
    children: [
      { path: "/", element: <LandingPage /> },
      { path: "/docs", element: <DocsPage /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
  {
    element: (
      <RequireAuth>
        <Layout>
          <Outlet />
        </Layout>
      </RequireAuth>
    ),
    children: [
      { path: "/overview", element: <OverviewPage /> },
      { path: "/apis", element: <ApisPage /> },
      { path: "/apis/:id", element: <ApiDetailPage /> },
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
