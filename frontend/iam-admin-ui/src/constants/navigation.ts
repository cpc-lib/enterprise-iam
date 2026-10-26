import type { ComponentType } from 'react'
import {
  ApartmentOutlined,
  AuditOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  FileSearchOutlined,
  FilterOutlined,
  FolderOutlined,
  IdcardOutlined,
  MenuOutlined,
  PartitionOutlined,
  SafetyCertificateOutlined,
  ScheduleOutlined,
  SettingOutlined,
  ShareAltOutlined,
  TableOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'

/** 导航图标注册表（schema 中仅下发 icon key，运行时只走映射 key） */
export const NAV_ICON_MAP: Record<string, ComponentType<{ className?: string }>> = {
  dashboard: DashboardOutlined,
  user: UserOutlined,
  safety: SafetyCertificateOutlined,
  apartment: ApartmentOutlined,
  team: TeamOutlined,
  database: DatabaseOutlined,
  filter: FilterOutlined,
  partition: PartitionOutlined,
  'file-search': FileSearchOutlined,
  share: ShareAltOutlined,
  folder: FolderOutlined,
  audit: AuditOutlined,
  table: TableOutlined,
  schedule: ScheduleOutlined,
  setting: SettingOutlined,
  idcard: IdcardOutlined,
  menu: MenuOutlined,
  DashboardOutlined: DashboardOutlined,
  UserOutlined: UserOutlined,
  SafetyCertificateOutlined: SafetyCertificateOutlined,
  ApartmentOutlined: ApartmentOutlined,
  TeamOutlined: TeamOutlined,
  DatabaseOutlined: DatabaseOutlined,
  FilterOutlined: FilterOutlined,
  PartitionOutlined: PartitionOutlined,
  FileSearchOutlined: FileSearchOutlined,
  ShareAltOutlined: ShareAltOutlined,
  FolderOutlined: FolderOutlined,
  AuditOutlined: AuditOutlined,
  TableOutlined: TableOutlined,
  ScheduleOutlined: ScheduleOutlined,
  SettingOutlined: SettingOutlined,
}

export interface NavItem {
  key: string
  path: string
  title: string
  icon?: string
  order?: number
}

export interface NavGroup {
  key: string
  title: string
  order?: number
  items: NavItem[]
}

/** 导航 schema 节点：单页面（NavItem）或分组（NavGroup） */
export type NavSchemaNode = NavItem | NavGroup

export type NavSchema = NavSchemaNode[]

/**
 * 本地兜底 schema（在 Server-Driven Navigation 离线或加载中时的保底实现）。
 */
export const NAVIGATION_SCHEMA: NavSchema = [
  { key: 'dashboard', path: '/dashboard', title: '仪表盘', icon: 'dashboard' },
  {
    key: 'identity',
    title: '身份管理',
    items: [
      { key: 'identity-users', path: '/identity/users', title: '用户管理', icon: 'user' },
      { key: 'identity-roles', path: '/identity/roles', title: '角色管理', icon: 'safety' },
      { key: 'identity-menus', path: '/identity/menus', title: '菜单配置', icon: 'menu' },
      { key: 'identity-approvals', path: '/identity/approvals', title: '权限审批流', icon: 'audit' },
    ],
  },
  {
    key: 'organization',
    title: '组织团队',
    items: [
      { key: 'organization-orgs', path: '/organization/orgs', title: '组织机构', icon: 'apartment' },
      { key: 'organization-positions', path: '/organization/positions', title: '岗位管理', icon: 'idcard' },
      { key: 'organization-teams', path: '/organization/teams', title: '团队管理', icon: 'team' },
    ],
  },
  {
    key: 'authorization',
    title: '授权策略',
    items: [
      { key: 'authz-data-scopes', path: '/authorization/data-scopes', title: '数据范围', icon: 'database' },
      { key: 'authz-field-policies', path: '/authorization/field-policies', title: '字段策略', icon: 'filter' },
      { key: 'authz-resource-fields', path: '/authorization/resource-fields', title: '资源字段', icon: 'table' },
      { key: 'authz-abac-policies', path: '/authorization/abac-policies', title: 'ABAC条件策略', icon: 'partition' },
      { key: 'authz-sod-policies', path: '/authorization/sod-policies', title: '职责分离', icon: 'safety' },
      { key: 'authz-explain', path: '/authorization/explain', title: '决策解释', icon: 'file-search' },
    ],
  },
  { key: 'shares', path: '/sharing/shares', title: '资源分享', icon: 'share' },
  { key: 'files', path: '/files', title: '文件管理', icon: 'folder' },
  { key: 'audit-logs', path: '/audit/logs', title: '审计日志', icon: 'audit' },
  { key: 'system-sessions', path: '/system/sessions', title: '在线会话', icon: 'safety' },
  { key: 'system-dicts', path: '/system/dicts', title: '数据字典', icon: 'table' },
  { key: 'jobs', path: '/system/jobs', title: '调度中心', icon: 'schedule' },
  { key: 'tenant-management', path: '/system/tenants', title: '租户管理', icon: 'apartment' },
  { key: 'tenant-bootstrap', path: '/system/tenant-bootstrap', title: '租户初始化', icon: 'setting' },
]
