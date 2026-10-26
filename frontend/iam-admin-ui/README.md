# IAM Admin UI

企业 IAM 管理控制台（React + TypeScript + Ant Design 5 + Zustand + Axios + TailwindCSS 4）。

## 开发

```bash
corepack enable
pnpm install
pnpm dev        # http://localhost:5173（/api 与 /admin 代理到 http://localhost:8080）
```

## 常用脚本

| 命令 | 说明 |
| --- | --- |
| `pnpm typecheck` | TypeScript 项目引用检查 |
| `pnpm lint` | ESLint（--max-warnings 0） |
| `pnpm test -- --run` | Vitest 单元测试 |
| `pnpm build` | 类型检查 + 生产构建 |

## 架构约定

- Feature-Based Architecture：业务功能放 `src/features/*`，`src/pages/*` 组装路由页面。
- Server-Driven Navigation：导航统一走 `src/api/client/navigation.ts` 的 `fetchNavigationSchema()`（当前为本地 schema 兜底），schema 类型见 `src/constants/navigation.ts`；菜单、面包屑、activeKey 均由 schema 推导。
- Server-Driven Page Authorization：前端禁止 `role === 'ADMIN'` 或业务 permission string 硬编码。
- 统一响应 envelope `{ success, code, data, traceId }` 在 `src/api/client/http.ts` 解包；失败抛出 `ApiError`（`src/api/client/errors.ts`，含常见错误码中文文案映射）。
- accessToken 仅存内存；用户上下文（非敏感）持久化于 localStorage；会话恢复依赖 HttpOnly refresh cookie（`POST /api/v1/auth/refresh`，401 时单飞静默刷新并重试一次）。

## 共享模式（后续功能页接入）

- HTTP 客户端：`import http from '@/api/client/http'`
- 错误处理：`import { ApiError, resolveErrorMessage } from '@/api/client/errors'`
- 状态：`import { useAuthStore } from '@/stores/auth'` / `@/stores/ui` / `@/stores/navigation`
- 导航注册：在 `src/constants/navigation.ts` 的 `NAVIGATION_SCHEMA` 增加条目，并在 `src/app/router/index.tsx` 挂路由
- 占位页：`import PlaceholderPage from '@/components/PlaceholderPage'`（`<PlaceholderPage title="xxx" />`，后续直接替换实现）
