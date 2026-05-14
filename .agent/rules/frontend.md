# Frontend Rules

## Tech Stack

| Layer | Choice |
|---|---|
| Framework | Vue 3.5 (Composition API, `<script setup>` syntax, TypeScript) |
| Build tool | Vite 8 |
| Language | TypeScript 6.0 |
| UI framework | Naive UI 2.44 (tree-shakeable, globally registered) |
| Icons | `@vicons/ionicons5` (Ionicons 5) |
| Router | Vue Router 5 (HTML5 history mode) |
| HTTP client | Axios 1.16 |
| State management | None (local state only; no Pinia/Vuex) |
| CSS | None (rely on Naive UI built-in theming) |

## Project Structure

```
frontend/
  index.html
  vite.config.ts
  tsconfig.json              (references only)
  tsconfig.app.json           (app TS config)
  tsconfig.node.json
  src/
    main.ts                   (app entry: mounts Vue, installs router + Naive UI)
    App.vue                   (bare <router-view />)
    router/index.ts            (route definitions + auth guard)
    api/
      request.ts               (Axios instance + auth interceptor + 401 redirect)
      auth.ts, providers.ts, endpoints.ts, models.ts,
      credentials.ts, apiKeys.ts, requestLogs.ts
    types/index.ts              (all TS interfaces: VO, Form, ApiResponse, PageResult, enums)
    layouts/AdminLayout.vue     (sidebar + header + <router-view />)
    components/AppSidebar.vue   (collapsible sidebar, n-menu)
    views/
      LoginView.vue
      DashboardView.vue
      providers/ProviderList.vue, endpoints/EndpointList.vue,
      models/ModelList.vue, credentials/CredentialList.vue,
      apiKeys/ApiKeyList.vue, requestLogs/RequestLogList.vue
```

## Code Conventions

### Component Pattern

- All components use `<script setup lang="ts">`.
- State is local (`ref`/`reactive`). No Pinia/Vuex stores.
- Auth state lives in `localStorage` under key `admin-token`.
- Navigation guards in `router/index.ts` enforce auth at the route level.

### API Layer

- Each domain has a dedicated file under `src/api/`.
- All functions return `request.get<ApiResponse<T>>(...)` — thin wrappers around the shared Axios instance.
- `ApiResponse<T>` shape: `{ code: number, message: string, data: T }`.
- `PageResult<T>` shape: `{ records: T[], total: number, size: number, current: number }`.

### Request Interceptor

- Reads `admin-token` from `localStorage`, attaches as `Authorization: Bearer <token>`.
- On 401 response, clears the token and redirects to `/login` via `window.location.href`.

### Auth Guard

- `router.beforeEach` checks `localStorage.getItem('admin-token')` for routes with `meta.requiresAuth: true`.
- Already-logged-in users hitting `/login` are redirected to `/`.

### View Pattern (CRUD)

Each list view follows the same pattern:
1. Naive UI `n-data-table` with typed `DataTableColumn[]` columns.
2. Render functions use Vue's `h()`.
3. Create/edit via `n-modal` + `n-form`.
4. CRUD functions call API modules then reload the list.
5. Error handling: `try/catch` with `useMessage()` toast.

### Type Conventions

- `*VO` — view objects (API response data shapes).
- `*Form` — create/update request payloads.
- `*SearchParam` — query/filter parameter objects.
- Enums exported as plain `const` arrays (e.g., `providerTypes`, `activeStatuses`).

### Layout

- `AdminLayout.vue`: `n-layout` > `n-layout-sider` + `n-layout` (header + content).
- `AppSidebar.vue`: wraps `n-menu`, collapse state emitted via `emit('update:collapsed')`.

### Routing

- Lazy-loaded routes via dynamic `import()`.
- All admin routes are children of the layout route.
- Named routes use PascalCase (e.g., `Dashboard`, `Providers`).

### Server Proxy

Vite dev server proxies `/admin` and `/health` to `http://localhost:8080`.

## Commands

```bash
# Install dependencies
cd frontend && npm install

# Dev server (with hot reload)
cd frontend && npm run dev

# Type check
cd frontend && npx vue-tsc --noEmit

# Production build
cd frontend && npm run build
```
