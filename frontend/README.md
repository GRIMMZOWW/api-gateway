# API Gateway Dashboard (Phase 8)

React 19 + Vite + TypeScript front-end for the API Gateway. Talks to the
Spring Boot backend (phases 0–7) on `http://localhost:8080`.

## Stack

- **React 19** + **Vite 6** (TypeScript, strict)
- **React Router 7** in library mode (`createBrowserRouter`, plain components +
  `useEffect`/`useState` — no framework-mode data loaders)
- **Recharts** for the request-volume chart
- Plain CSS with CSS custom properties (no Tailwind, no UI kit). One accent
  color (teal) and one font pair (`system-ui` + `ui-monospace`)

## Run it

```bash
npm install
npm run dev      # serves http://localhost:5173
npm run build    # type-check (tsc -b) + production build to dist/
npm run preview  # preview the production build
```

Point the dashboard at a backend other than `http://localhost:8080` by setting
`VITE_API_BASE` (create a `.env.local`):

```
VITE_API_BASE=https://my-gateway.example.com
```

The backend's CORS config already allows `http://localhost:5173`.

## What's here

```
frontend/
  index.html
  package.json
  tsconfig.json / tsconfig.app.json / tsconfig.node.json
  vite.config.ts
  src/
    main.tsx               mounts <App/> with RouterProvider
    App.tsx                createBrowserRouter + layout route
    styles.css             CSS variables, components, responsive
    api/
      client.ts            the ONE fetch wrapper (Bearer, 401 -> /login, typed ApiError)
      auth.ts / apis.ts / keys.ts / analytics.ts
    auth/AuthContext.tsx   token in memory + localStorage; useAuth()
    hooks/useAsync.ts      loading/empty/error state for any promise
    utils/format.ts        date / number / BigDecimal helpers
    components/
      Layout.tsx           top nav (APIs link, user, logout)
      RequireAuth.tsx      route guard (splash while /me resolves)
      Spinner.tsx          spinner + skeleton rows
      EmptyState.tsx       empty list/chart state with a next step
      ErrorState.tsx       error state with a retry button
      OutcomeLabel.tsx     text + color (never color alone)
      DateRangePicker.tsx  1h / 24h / 7d / 30d
      SummaryCards.tsx     5 overview cards
      RequestsChart.tsx    stacked bar chart (success / rate-limited / errors)
      TopPathsTable.tsx
      KeysTable.tsx
      CreateKeyModal.tsx   form -> reveal full key once with copy + warning
      EditKeyModal.tsx     edit name / capacity / refill
      ConfirmDialog.tsx    generic confirm (used by revoke)
      RecentRequestsTable.tsx
      SettingsTab.tsx      edit + enable/disable + typed-name delete + curl
    pages/
      LoginPage.tsx
      RegisterPage.tsx
      ApisPage.tsx         list with 24h request count + new-API modal
      ApiDetailPage.tsx    Overview / Keys / Requests / Settings tabs
```

## Notes

- The JWT lives in memory **and** `localStorage` (never a cookie). Every request
  is sent through `src/api/client.ts`, which adds `Authorization: Bearer …` and
  redirects to `/login` on `401`.
- The gateway endpoint is `GET/POST/… /gw/{slug}/**` with an `X-API-Key`
  header — the Settings tab shows a copy-ready `curl` example.
