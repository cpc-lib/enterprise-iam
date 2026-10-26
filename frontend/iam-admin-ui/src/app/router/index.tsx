import { Suspense, lazy, useEffect, useRef, useState } from 'react'
import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { Spin } from 'antd'
import { AuthLayout } from '@/layouts/AuthLayout'
import { BasicLayout } from '@/layouts/BasicLayout'
import { useAuthStore } from '@/stores/auth'

const LoginPage = lazy(() => import('@/pages/auth/LoginPage'))
const DashboardPage = lazy(() => import('@/pages/dashboard/DashboardPage'))
const UsersPage = lazy(() => import('@/pages/identity/UsersPage'))
const RolesPage = lazy(() => import('@/pages/identity/RolesPage'))
const MenusPage = lazy(() => import('@/pages/identity/MenusPage'))
const ApprovalsPage = lazy(() => import('@/pages/identity/ApprovalsPage'))
const OrganizationsPage = lazy(() => import('@/pages/organization/OrganizationsPage'))
const PositionsPage = lazy(() => import('@/pages/organization/PositionsPage'))
const TeamsPage = lazy(() => import('@/pages/organization/TeamsPage'))
const DataScopesPage = lazy(() => import('@/pages/authorization/DataScopesPage'))
const FieldPoliciesPage = lazy(() => import('@/pages/authorization/FieldPoliciesPage'))
const ResourceFieldsPage = lazy(() => import('@/pages/authorization/ResourceFieldsPage'))
const AbacPoliciesPage = lazy(() => import('@/pages/authorization/AbacPoliciesPage'))
const SodPoliciesPage = lazy(() => import('@/pages/authorization/SodPoliciesPage'))
const ExplainPage = lazy(() => import('@/pages/authorization/ExplainPage'))
const SharesPage = lazy(() => import('@/pages/sharing/SharesPage'))
const FileLibraryPage = lazy(() => import('@/pages/files/FileLibraryPage'))
const AuditLogsPage = lazy(() => import('@/pages/audit/AuditLogsPage'))
const JobsPage = lazy(() => import('@/pages/system/JobsPage'))
const SessionsPage = lazy(() => import('@/pages/system/SessionsPage'))
const DictPage = lazy(() => import('@/pages/system/DictPage'))
const TenantsPage = lazy(() => import('@/pages/system/TenantsPage'))
const TenantBootstrapPage = lazy(() => import('@/pages/system/TenantBootstrapPage'))

function PageLoading() {
  return (
    <div className="flex h-screen items-center justify-center">
      <Spin size="large" />
    </div>
  )
}

function RequireAuth() {
  const status = useAuthStore((s) => s.status)
  const refresh = useAuthStore((s) => s.refresh)
  const [restoreAttempted, setRestoreAttempted] = useState(false)
  const restoring = useRef(false)

  useEffect(() => {
    if (status === 'idle' && !restoring.current) {
      restoring.current = true
      refresh().finally(() => setRestoreAttempted(true))
    }
  }, [status, refresh])

  if (status === 'authenticated') return <Outlet />
  if (restoreAttempted) return <Navigate to="/login" replace />
  return <PageLoading />
}

export function AppRoutes() {
  return (
    <Suspense fallback={<PageLoading />}>
      <Routes>
        <Route element={<AuthLayout />}>
          <Route path="/login" element={<LoginPage />} />
        </Route>

        <Route element={<RequireAuth />}>
          <Route path="/" element={<BasicLayout />}>
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="dashboard" element={<DashboardPage />} />
            <Route path="identity/users" element={<UsersPage />} />
            <Route path="identity/roles" element={<RolesPage />} />
            <Route path="identity/menus" element={<MenusPage />} />
            <Route path="identity/approvals" element={<ApprovalsPage />} />
            <Route path="organization/orgs" element={<OrganizationsPage />} />
            <Route path="organization/positions" element={<PositionsPage />} />
            <Route path="organization/teams" element={<TeamsPage />} />
            <Route path="authorization/data-scopes" element={<DataScopesPage />} />
            <Route path="authorization/field-policies" element={<FieldPoliciesPage />} />
            <Route path="authorization/resource-fields" element={<ResourceFieldsPage />} />
            <Route path="authorization/abac-policies" element={<AbacPoliciesPage />} />
            <Route path="authorization/sod-policies" element={<SodPoliciesPage />} />
            <Route path="authorization/explain" element={<ExplainPage />} />
            <Route path="sharing/shares" element={<SharesPage />} />
            <Route path="files" element={<FileLibraryPage />} />
            <Route path="audit/logs" element={<AuditLogsPage />} />
            <Route path="system/sessions" element={<SessionsPage />} />
            <Route path="system/dicts" element={<DictPage />} />
            <Route path="system/jobs" element={<JobsPage />} />
            <Route path="system/menus" element={<MenusPage />} />
            <Route path="system/tenants" element={<TenantsPage />} />
            <Route path="system/tenant-bootstrap" element={<TenantBootstrapPage />} />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </Suspense>
  )
}
