-- Owner: iam-identity-service / iam_identity
-- V7: RBAC Role-Menu Table and Database-Driven Menus & Role Grants

CREATE TABLE IF NOT EXISTS iam_role_menu (
    id          BIGINT UNSIGNED NOT NULL,
    tenant_id   BIGINT UNSIGNED NOT NULL,
    role_id     BIGINT UNSIGNED NOT NULL,
    menu_id     BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_role_menu (tenant_id, role_id, menu_id),
    KEY idx_role_menu_lookup (tenant_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ==========================================
-- 1. Seed Menus for Tenant 20001 (tenant-corp)
-- ==========================================
INSERT INTO iam_menu (id, tenant_id, parent_id, menu_key, title, path, icon, sort_order, status, publish_status, permission_code, component, visible, delete_marker, version) VALUES
-- Root menus
(20100, 20001, NULL, 'dashboard', '仪表盘', '/dashboard', 'dashboard', 1, 'ACTIVE', 'PUBLISHED', 'dashboard:view', 'dashboard/DashboardPage', 1, 0, 1),
(20200, 20001, NULL, 'identity', '身份管理', NULL, 'safety', 2, 'ACTIVE', 'PUBLISHED', 'identity:view', NULL, 1, 0, 1),
(20300, 20001, NULL, 'organization', '组织团队', NULL, 'apartment', 3, 'ACTIVE', 'PUBLISHED', 'org:view', NULL, 1, 0, 1),
(20400, 20001, NULL, 'authorization', '授权策略', NULL, 'safety', 4, 'ACTIVE', 'PUBLISHED', 'authz:view', NULL, 1, 0, 1),
(20500, 20001, NULL, 'shares', '资源分享', '/sharing/shares', 'share', 5, 'ACTIVE', 'PUBLISHED', 'sharing:share:list', 'sharing/SharesPage', 1, 0, 1),
(20600, 20001, NULL, 'files', '文件管理', '/files', 'folder', 6, 'ACTIVE', 'PUBLISHED', 'file:manage:list', 'files/FileLibraryPage', 1, 0, 1),
(20700, 20001, NULL, 'audit-logs', '审计日志', '/audit/logs', 'audit', 7, 'ACTIVE', 'PUBLISHED', 'audit:log:list', 'audit/AuditLogsPage', 1, 0, 1),
(20800, 20001, NULL, 'system-sessions', '在线会话', '/system/sessions', 'safety', 8, 'ACTIVE', 'PUBLISHED', 'system:session:list', 'system/SessionsPage', 1, 0, 1),
(20900, 20001, NULL, 'system-dicts', '数据字典', '/system/dicts', 'table', 9, 'ACTIVE', 'PUBLISHED', 'system:dict:list', 'system/DictPage', 1, 0, 1),
(21000, 20001, NULL, 'jobs', '调度中心', '/system/jobs', 'schedule', 10, 'ACTIVE', 'PUBLISHED', 'job:manage:list', 'system/JobsPage', 1, 0, 1),

-- Identity sub-menus
(20201, 20001, 20200, 'identity-users', '用户管理', '/identity/users', 'user', 1, 'ACTIVE', 'PUBLISHED', 'identity:user:list', 'identity/UsersPage', 1, 0, 1),
(20202, 20001, 20200, 'identity-roles', '角色管理', '/identity/roles', 'safety', 2, 'ACTIVE', 'PUBLISHED', 'identity:role:list', 'identity/RolesPage', 1, 0, 1),
(20203, 20001, 20200, 'identity-menus', '菜单配置', '/identity/menus', 'menu', 3, 'ACTIVE', 'PUBLISHED', 'identity:menu:list', 'identity/MenusPage', 1, 0, 1),
(20204, 20001, 20200, 'identity-approvals', '权限审批流', '/identity/approvals', 'AuditOutlined', 4, 'ACTIVE', 'PUBLISHED', 'identity:approval:list', 'identity/ApprovalsPage', 1, 0, 1),

-- Organization sub-menus
(20301, 20001, 20300, 'organization-orgs', '组织机构', '/organization/orgs', 'apartment', 1, 'ACTIVE', 'PUBLISHED', 'org:organization:list', 'organization/OrganizationsPage', 1, 0, 1),
(20302, 20001, 20300, 'organization-positions', '岗位管理', '/organization/positions', 'idcard', 2, 'ACTIVE', 'PUBLISHED', 'org:position:list', 'organization/PositionsPage', 1, 0, 1),
(20303, 20001, 20300, 'organization-teams', '团队管理', '/organization/teams', 'team', 3, 'ACTIVE', 'PUBLISHED', 'org:team:list', 'organization/TeamsPage', 1, 0, 1),

-- Authorization sub-menus
(20401, 20001, 20400, 'authz-data-scopes', '数据范围', '/authorization/data-scopes', 'database', 1, 'ACTIVE', 'PUBLISHED', 'authz:data-scope:list', 'authorization/DataScopesPage', 1, 0, 1),
(20402, 20001, 20400, 'authz-field-policies', '字段策略', '/authorization/field-policies', 'filter', 2, 'ACTIVE', 'PUBLISHED', 'authz:field-policy:list', 'authorization/FieldPoliciesPage', 1, 0, 1),
(20403, 20001, 20400, 'authz-resource-fields', '资源字段', '/authorization/resource-fields', 'table', 3, 'ACTIVE', 'PUBLISHED', 'authz:resource-field:list', 'authorization/ResourceFieldsPage', 1, 0, 1),
(20404, 20001, 20400, 'authz-explain', '决策解释', '/authorization/explain', 'file-search', 4, 'ACTIVE', 'PUBLISHED', 'authz:explain:view', 'authorization/ExplainPage', 1, 0, 1),
(20405, 20001, 20400, 'authz-abac-policies', 'ABAC 条件策略', '/authorization/abac-policies', 'PartitionOutlined', 5, 'ACTIVE', 'PUBLISHED', 'authz:abac-policy:list', 'authorization/AbacPoliciesPage', 1, 0, 1),
(20406, 20001, 20400, 'authz-sod-policies', '职责分离 (SoD)', '/authorization/sod-policies', 'SafetyCertificateOutlined', 6, 'ACTIVE', 'PUBLISHED', 'authz:sod-policy:list', 'authorization/SodPoliciesPage', 1, 0, 1)
ON DUPLICATE KEY UPDATE title = VALUES(title), path = VALUES(path), icon = VALUES(icon), sort_order = VALUES(sort_order), status = VALUES(status), publish_status = VALUES(publish_status);

-- ==========================================
-- 2. Seed Menus for Tenant 10001 (system)
-- ==========================================
INSERT INTO iam_menu (id, tenant_id, parent_id, menu_key, title, path, icon, sort_order, status, publish_status, permission_code, component, visible, delete_marker, version) VALUES
(10100, 10001, NULL, 'dashboard', '仪表盘', '/dashboard', 'dashboard', 1, 'ACTIVE', 'PUBLISHED', 'dashboard:view', 'dashboard/DashboardPage', 1, 0, 1),
(10200, 10001, NULL, 'identity', '身份管理', NULL, 'safety', 2, 'ACTIVE', 'PUBLISHED', 'identity:view', NULL, 1, 0, 1),
(10300, 10001, NULL, 'organization', '组织团队', NULL, 'apartment', 3, 'ACTIVE', 'PUBLISHED', 'org:view', NULL, 1, 0, 1),
(10400, 10001, NULL, 'authorization', '授权策略', NULL, 'safety', 4, 'ACTIVE', 'PUBLISHED', 'authz:view', NULL, 1, 0, 1),
(10500, 10001, NULL, 'shares', '资源分享', '/sharing/shares', 'share', 5, 'ACTIVE', 'PUBLISHED', 'sharing:share:list', 'sharing/SharesPage', 1, 0, 1),
(10600, 10001, NULL, 'files', '文件管理', '/files', 'folder', 6, 'ACTIVE', 'PUBLISHED', 'file:manage:list', 'files/FileLibraryPage', 1, 0, 1),
(10700, 10001, NULL, 'audit-logs', '审计日志', '/audit/logs', 'audit', 7, 'ACTIVE', 'PUBLISHED', 'audit:log:list', 'audit/AuditLogsPage', 1, 0, 1),
(10800, 10001, NULL, 'system-sessions', '在线会话', '/system/sessions', 'safety', 8, 'ACTIVE', 'PUBLISHED', 'system:session:list', 'system/SessionsPage', 1, 0, 1),
(10900, 10001, NULL, 'system-dicts', '数据字典', '/system/dicts', 'table', 9, 'ACTIVE', 'PUBLISHED', 'system:dict:list', 'system/DictPage', 1, 0, 1),
(11000, 10001, NULL, 'jobs', '调度中心', '/system/jobs', 'schedule', 10, 'ACTIVE', 'PUBLISHED', 'job:manage:list', 'system/JobsPage', 1, 0, 1),
(11200, 10001, NULL, 'tenant-management', '租户管理', '/system/tenants', 'apartment', 11, 'ACTIVE', 'PUBLISHED', 'system:tenant:list', 'system/TenantsPage', 1, 0, 1),
(11100, 10001, NULL, 'tenant-bootstrap', '租户初始化', '/system/tenant-bootstrap', 'setting', 12, 'ACTIVE', 'PUBLISHED', 'system:tenant:bootstrap', 'system/TenantBootstrapPage', 1, 0, 1),
(10201, 10001, 10200, 'identity-users', '用户管理', '/identity/users', 'user', 1, 'ACTIVE', 'PUBLISHED', 'identity:user:list', 'identity/UsersPage', 1, 0, 1),
(10202, 10001, 10200, 'identity-roles', '角色管理', '/identity/roles', 'safety', 2, 'ACTIVE', 'PUBLISHED', 'identity:role:list', 'identity/RolesPage', 1, 0, 1),
(10203, 10001, 10200, 'identity-menus', '菜单配置', '/identity/menus', 'menu', 3, 'ACTIVE', 'PUBLISHED', 'identity:menu:list', 'identity/MenusPage', 1, 0, 1),
(10204, 10001, 10200, 'identity-approvals', '权限审批流', '/identity/approvals', 'AuditOutlined', 4, 'ACTIVE', 'PUBLISHED', 'identity:approval:list', 'identity/ApprovalsPage', 1, 0, 1),
(10301, 10001, 10300, 'organization-orgs', '组织机构', '/organization/orgs', 'apartment', 1, 'ACTIVE', 'PUBLISHED', 'org:organization:list', 'organization/OrganizationsPage', 1, 0, 1),
(10302, 10001, 10300, 'organization-positions', '岗位管理', '/organization/positions', 'idcard', 2, 'ACTIVE', 'PUBLISHED', 'org:position:list', 'organization/PositionsPage', 1, 0, 1),
(10303, 10001, 10300, 'organization-teams', '团队管理', '/organization/teams', 'team', 3, 'ACTIVE', 'PUBLISHED', 'org:team:list', 'organization/TeamsPage', 1, 0, 1),
(10401, 10001, 10400, 'authz-data-scopes', '数据范围', '/authorization/data-scopes', 'database', 1, 'ACTIVE', 'PUBLISHED', 'authz:data-scope:list', 'authorization/DataScopesPage', 1, 0, 1),
(10402, 10001, 10400, 'authz-field-policies', '字段策略', '/authorization/field-policies', 'filter', 2, 'ACTIVE', 'PUBLISHED', 'authz:field-policy:list', 'authorization/FieldPoliciesPage', 1, 0, 1),
(10403, 10001, 10400, 'authz-resource-fields', '资源字段', '/authorization/resource-fields', 'table', 3, 'ACTIVE', 'PUBLISHED', 'authz:resource-field:list', 'authorization/ResourceFieldsPage', 1, 0, 1),
(10404, 10001, 10400, 'authz-explain', '决策解释', '/authorization/explain', 'file-search', 4, 'ACTIVE', 'PUBLISHED', 'authz:explain:view', 'authorization/ExplainPage', 1, 0, 1),
(10405, 10001, 10400, 'authz-abac-policies', 'ABAC 条件策略', '/authorization/abac-policies', 'PartitionOutlined', 5, 'ACTIVE', 'PUBLISHED', 'authz:abac-policy:list', 'authorization/AbacPoliciesPage', 1, 0, 1),
(10406, 10001, 10400, 'authz-sod-policies', '职责分离 (SoD)', '/authorization/sod-policies', 'SafetyCertificateOutlined', 6, 'ACTIVE', 'PUBLISHED', 'authz:sod-policy:list', 'authorization/SodPoliciesPage', 1, 0, 1)
ON DUPLICATE KEY UPDATE title = VALUES(title), path = VALUES(path), icon = VALUES(icon), sort_order = VALUES(sort_order), status = VALUES(status), publish_status = VALUES(publish_status);

-- ==========================================
-- 3. Seed Role-Menu Grants in Database
-- ==========================================

-- Role 2001 (TENANT_ADMIN): Grant ALL menus of tenant 20001
INSERT INTO iam_role_menu (id, tenant_id, role_id, menu_id) VALUES
(200101, 20001, 2001, 20100),
(200102, 20001, 2001, 20200),
(200103, 20001, 2001, 20201),
(200104, 20001, 2001, 20202),
(200105, 20001, 2001, 20203),
(200106, 20001, 2001, 20204),
(200107, 20001, 2001, 20300),
(200108, 20001, 2001, 20301),
(200109, 20001, 2001, 20302),
(200110, 20001, 2001, 20303),
(200111, 20001, 2001, 20400),
(200112, 20001, 2001, 20401),
(200113, 20001, 2001, 20402),
(200114, 20001, 2001, 20403),
(200115, 20001, 2001, 20404),
(200116, 20001, 2001, 20405),
(200117, 20001, 2001, 20406),
(200118, 20001, 2001, 20500),
(200119, 20001, 2001, 20600),
(200120, 20001, 2001, 20700),
(200121, 20001, 2001, 20800),
(200122, 20001, 2001, 20900),
(200123, 20001, 2001, 21000)
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);

-- Role 2002 (REGULAR_USER - staff@corp.com): Grant ONLY the 5 sections (14 items: 2 dirs + 12 pages)
INSERT INTO iam_role_menu (id, tenant_id, role_id, menu_id) VALUES
(200201, 20001, 2002, 20300), -- 组织团队 (Directory)
(200202, 20001, 2002, 20301), -- 组织机构
(200203, 20001, 2002, 20302), -- 岗位管理
(200204, 20001, 2002, 20303), -- 团队管理
(200205, 20001, 2002, 20400), -- 授权策略 (Directory)
(200206, 20001, 2002, 20401), -- 数据范围
(200207, 20001, 2002, 20402), -- 字段策略
(200208, 20001, 2002, 20403), -- 资源字段
(200209, 20001, 2002, 20404), -- 决策解释
(200210, 20001, 2002, 20405), -- ABAC 条件策略
(200211, 20001, 2002, 20406), -- 职责分离 (SoD)
(200212, 20001, 2002, 20500), -- 资源分享
(200213, 20001, 2002, 20600), -- 文件管理
(200214, 20001, 2002, 20700)  -- 审计日志
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);
