import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  PlusOutlined,
  ReloadOutlined,
  RightOutlined,
  SearchOutlined,
  TeamOutlined,
  UserAddOutlined,
  UserDeleteOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Card,
  DatePicker,
  Drawer,
  Empty,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  Tree,
  Typography,
} from 'antd'
import type { TableColumnsType, TreeDataNode, TreeProps } from 'antd'
import type { Dayjs } from 'dayjs'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { resolveErrorMessage } from '@/api/client/errors'
import { getMenuTree } from '@/api/identity/menus'
import {
  bindUserRole,
  createRole,
  deleteRole,
  disableRole,
  enableRole,
  getRoleMenus,
  getRoles,
  getRoleUsers,
  unbindUserRole,
  updateRoleMenus,
} from '@/api/identity/roles'
import { listUsers } from '@/api/identity/users'
import { getTenants } from '@/api/system/tenants'
import type { TenantItem } from '@/api/system/tenants'
import { UserDrawer } from '@/components/UserDrawer'
import { usePageSchema } from '@/hooks/useServerMetadata'
import { useAuthStore } from '@/stores/auth'
import type { MenuNode, RoleItem, UserDetail } from '@/types/identity'
import { dayjs } from '@/utils/dayjs'

const ROLE_CODE_PATTERN = /^[A-Z][A-Z0-9_]{2,63}$/

const STATUS_TAG: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '启用' },
  DISABLED: { color: 'red', label: '停用' },
}

interface CreateFormValues {
  roleCode: string
  roleName: string
  description?: string
  formTenantId?: string
}

type ActiveModal = 'create' | 'perms' | null

function renderStatus(status: string) {
  const meta = STATUS_TAG[status] ?? { color: 'default', label: status }
  return <Tag color={meta.color}>{meta.label}</Tag>
}

function renderId(value: string) {
  return (
    <Typography.Text copyable={{ text: value }} className="font-mono text-xs">
      {value}
    </Typography.Text>
  )
}

function buildTreeData(nodes: MenuNode[]): TreeDataNode[] {
  return nodes.map((node) => {
    const hasChildren = Boolean(node.children && node.children.length > 0)
    return {
      key: String(node.id),
      title: (
        <span className="inline-flex items-center gap-2 py-0.5">
          <span className="font-medium text-slate-800">{node.title}</span>
          {node.code && (
            <Tag color={hasChildren ? 'purple' : 'blue'} className="text-[11px] leading-tight px-1.5 py-0.5 m-0">
              {node.code}
            </Tag>
          )}
          {node.path && (
            <span className="text-xs text-slate-400 font-mono hidden sm:inline">
              {node.path}
            </span>
          )}
        </span>
      ),
      children: hasChildren ? buildTreeData(node.children) : undefined,
    }
  })
}

function getAllKeys(nodes: MenuNode[]): string[] {
  const keys: string[] = []
  function traverse(list: MenuNode[]) {
    for (const item of list) {
      keys.push(String(item.id))
      if (item.children && item.children.length > 0) {
        traverse(item.children)
      }
    }
  }
  traverse(nodes)
  return keys
}

function getLeafKeys(nodes: MenuNode[]): Set<string> {
  const leaves = new Set<string>()
  function traverse(list: MenuNode[]) {
    for (const item of list) {
      if (!item.children || item.children.length === 0) {
        leaves.add(String(item.id))
      } else {
        traverse(item.children)
      }
    }
  }
  traverse(nodes)
  return leaves
}

function getParentKeyMap(nodes: MenuNode[]): Map<string, string> {
  const map = new Map<string, string>()
  function traverse(list: MenuNode[]) {
    for (const node of list) {
      if (node.children && node.children.length > 0) {
        for (const child of node.children) {
          map.set(String(child.id), String(node.id))
        }
        traverse(node.children)
      }
    }
  }
  traverse(nodes)
  return map
}

export default function RolesPage() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const { hasOperation } = usePageSchema('identity-roles')

  const currentUser = useAuthStore((s) => s.user)
  const isSuperAdmin = currentUser?.tenantId === '10001'
  const urlTenantId = searchParams.get('tenantId') || undefined

  const [selectedTenantId, setSelectedTenantId] = useState<string | undefined>(
    isSuperAdmin ? urlTenantId : currentUser?.tenantId,
  )
  const [tenantList, setTenantList] = useState<TenantItem[]>([])

  const [items, setItems] = useState<RoleItem[]>([])
  const [loading, setLoading] = useState(false)
  const [activeModal, setActiveModal] = useState<ActiveModal>(null)
  const [submitting, setSubmitting] = useState(false)

  const [draftTenantId, setDraftTenantId] = useState<string | undefined>(selectedTenantId)
  const [draftKeyword, setDraftKeyword] = useState('')
  const [draftStatus, setDraftStatus] = useState<string | undefined>(undefined)
  const [draftRange, setDraftRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)

  const [appliedKeyword, setAppliedKeyword] = useState('')
  const [appliedStatus, setAppliedStatus] = useState<string | undefined>(undefined)
  const [appliedRange, setAppliedRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)

  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const [createForm] = Form.useForm<CreateFormValues>()

  const handleSearch = () => {
    setAppliedKeyword(draftKeyword)
    setAppliedStatus(draftStatus)
    setAppliedRange(draftRange)
    if (isSuperAdmin && draftTenantId !== selectedTenantId) {
      setSelectedTenantId(draftTenantId)
      if (draftTenantId) {
        setSearchParams({ tenantId: draftTenantId })
      } else {
        setSearchParams({})
      }
    }
    setPage(1)
  }

  const handleReset = () => {
    setDraftKeyword('')
    setDraftStatus(undefined)
    setDraftRange(null)
    setAppliedKeyword('')
    setAppliedStatus(undefined)
    setAppliedRange(null)
    if (isSuperAdmin && selectedTenantId !== undefined) {
      setDraftTenantId(undefined)
      setSelectedTenantId(undefined)
      setSearchParams({})
    }
    setPage(1)
  }

  // 加载租户字典（仅平台超管）
  useEffect(() => {
    if (isSuperAdmin) {
      getTenants()
        .then((data) => setTenantList(data))
        .catch(() => {})
    }
  }, [isSuperAdmin])

  // 角色菜单权限树状态
  const [selectedRole, setSelectedRole] = useState<RoleItem | null>(null)
  const [menuTree, setMenuTree] = useState<MenuNode[]>([])
  const [treeLoading, setTreeLoading] = useState(false)
  const [checkedMenuKeys, setCheckedMenuKeys] = useState<React.Key[]>([])
  const [halfCheckedKeys, setHalfCheckedKeys] = useState<React.Key[]>([])
  const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([])
  const [autoExpandParent, setAutoExpandParent] = useState(true)

  const loadRoles = useCallback(async () => {
    setLoading(true)
    try {
      const targetTenant = isSuperAdmin ? selectedTenantId : currentUser?.tenantId
      const data = await getRoles(targetTenant)
      setItems(data)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }, [isSuperAdmin, selectedTenantId, currentUser?.tenantId, message])

  useEffect(() => {
    void loadRoles()
  }, [loadRoles])

  const filtered = useMemo(() => {
    const text = appliedKeyword.trim()
    const start = appliedRange?.[0] ?? null
    const end = appliedRange?.[1] ?? null
    return items.filter((item) => {
      if (appliedStatus && item.status !== appliedStatus) return false
      if (
        text &&
        !item.id.includes(text) &&
        !item.roleCode.includes(text) &&
        !item.roleName.includes(text)
      ) {
        return false
      }
      if (start && item.createdAt && dayjs(item.createdAt).isBefore(start.startOf('day'))) return false
      if (end && item.createdAt && dayjs(item.createdAt).isAfter(end.endOf('day'))) return false
      return true
    })
  }, [items, appliedKeyword, appliedStatus, appliedRange])

  const openCreate = () => {
    createForm.resetFields()
    if (isSuperAdmin) {
      createForm.setFieldsValue({
        formTenantId: selectedTenantId || '10001',
      })
    }
    setActiveModal('create')
  }

  const openPerms = async (record: RoleItem) => {
    setSelectedRole(record)
    setActiveModal('perms')
    setTreeLoading(true)
    try {
      const targetTenantId = record.tenantId || (isSuperAdmin ? (selectedTenantId || '10001') : currentUser?.tenantId) || '10001'
      const tree = await getMenuTree(targetTenantId)
      setMenuTree(tree)
      const allKeys = getAllKeys(tree)
      setExpandedKeys(allKeys)

      const leafSet = getLeafKeys(tree)
      const assignedIds = await getRoleMenus(record.id, targetTenantId)
      const initialChecked = assignedIds.filter((id) => leafSet.has(id))
      setCheckedMenuKeys(initialChecked)
      setHalfCheckedKeys([])
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setTreeLoading(false)
    }
  }

  const handleCreate = async () => {
    try {
      await createForm.validateFields()
    } catch {
      return
    }
    const { roleCode, roleName, description, formTenantId } = createForm.getFieldsValue()
    setSubmitting(true)
    try {
      const targetTenant = isSuperAdmin
        ? (formTenantId || selectedTenantId || '10001')
        : (currentUser?.tenantId || '20001')
      const result = await createRole(
        {
          roleCode,
          roleName,
          description: description?.trim() ? description.trim() : null,
        },
        targetTenant,
      )
      message.success(`角色创建成功（ID：${result.roleId}）`)
      setActiveModal(null)
      await loadRoles()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const parentMap = useMemo(() => getParentKeyMap(menuTree), [menuTree])

  const handleSaveRoleMenus = async () => {
    if (!selectedRole) return
    setSubmitting(true)
    try {
      const allKeysSet = new Set<string>()
      for (const k of checkedMenuKeys) {
        allKeysSet.add(String(k))
      }
      for (const k of halfCheckedKeys) {
        allKeysSet.add(String(k))
      }
      // 自动向上递归补全所有父级目录ID，确保目录不会在侧边栏中缺失
      for (const k of Array.from(allKeysSet)) {
        let current = k
        while (parentMap.has(current)) {
          const parentId = parentMap.get(current)!
          allKeysSet.add(parentId)
          current = parentId
        }
      }

      const allIds = Array.from(allKeysSet)
      const targetTenant = selectedRole.tenantId || (isSuperAdmin ? (selectedTenantId || '10001') : currentUser?.tenantId) || '10001'
      await updateRoleMenus(selectedRole.id, allIds, targetTenant)
      message.success(`角色【${selectedRole.roleName}】菜单权限配置成功，已同步至 iam_role_menu`)
      setActiveModal(null)
      await loadRoles()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const treeData = useMemo(() => buildTreeData(menuTree), [menuTree])
  const allMenuKeys = useMemo(() => getAllKeys(menuTree), [menuTree])

  const onTreeCheck: TreeProps['onCheck'] = (checked, info) => {
    if (Array.isArray(checked)) {
      setCheckedMenuKeys(checked)
      setHalfCheckedKeys(info.halfCheckedKeys ?? [])
    } else {
      setCheckedMenuKeys(checked.checked)
      setHalfCheckedKeys(checked.halfChecked ?? [])
    }
  }

  const handleSelectAll = () => {
    setCheckedMenuKeys(allMenuKeys)
    setHalfCheckedKeys([])
  }

  const handleClearAll = () => {
    setCheckedMenuKeys([])
    setHalfCheckedKeys([])
  }

  const handleExpandAll = () => {
    setExpandedKeys(allMenuKeys)
  }

  const handleCollapseAll = () => {
    setExpandedKeys([])
  }

  const handleEnableRole = async (record: RoleItem) => {
    try {
      const targetTenant = currentUser?.tenantId || record.tenantId || '10001'
      await enableRole(record.id, record.version, targetTenant)
      message.success(`角色「${record.roleName}」已启用`)
      await loadRoles()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const handleDisableRole = async (record: RoleItem) => {
    try {
      const targetTenant = currentUser?.tenantId || record.tenantId || '10001'
      await disableRole(record.id, record.version, targetTenant)
      message.success(`角色「${record.roleName}」已禁用`)
      await loadRoles()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const handleDeleteRole = async (record: RoleItem) => {
    try {
      const targetTenant = currentUser?.tenantId || record.tenantId || '10001'
      await deleteRole(record.id, record.version, targetTenant)
      message.success(`角色「${record.roleName}」已删除`)
      await loadRoles()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  // 角色已分配用户抽屉与管理状态
  const [roleUsersDrawerOpen, setRoleUsersDrawerOpen] = useState(false)
  const [selectedRoleForUsers, setSelectedRoleForUsers] = useState<RoleItem | null>(null)
  const [roleUsers, setRoleUsers] = useState<UserDetail[]>([])
  const [roleUsersLoading, setRoleUsersLoading] = useState(false)
  const [roleUserKeyword, setRoleUserKeyword] = useState('')
  const [detailUser, setDetailUser] = useState<UserDetail | null>(null)
  const [detailUserDrawerOpen, setDetailUserDrawerOpen] = useState(false)

  // 为角色添加成员模态框状态
  const [addMemberModalOpen, setAddMemberModalOpen] = useState(false)
  const [selectedUserIdsToAdd, setSelectedUserIdsToAdd] = useState<string[]>([])
  const [availableCandidateUsers, setAvailableCandidateUsers] = useState<UserDetail[]>([])
  const [candidateLoading, setCandidateLoading] = useState(false)
  const [addMemberSubmitting, setAddMemberSubmitting] = useState(false)

  const loadRoleUsers = useCallback(async (role: RoleItem) => {
    setRoleUsersLoading(true)
    try {
      const targetTenant = isSuperAdmin
        ? (selectedTenantId && selectedTenantId !== '10001' ? selectedTenantId : undefined)
        : currentUser?.tenantId
      const users = await getRoleUsers(role.id, targetTenant)
      setRoleUsers(users)
    } catch (error) {
      message.error('加载角色分配用户失败：' + resolveErrorMessage(error))
    } finally {
      setRoleUsersLoading(false)
    }
  }, [isSuperAdmin, selectedTenantId, currentUser?.tenantId, message])

  const openRoleUsers = (record: RoleItem) => {
    setSelectedRoleForUsers(record)
    setRoleUserKeyword('')
    setRoleUsersDrawerOpen(true)
    void loadRoleUsers(record)
  }

  const handleUnbindUser = async (userRecord: UserDetail) => {
    if (!selectedRoleForUsers) return
    const isRoleOwner = String(selectedRoleForUsers.tenantId ?? '') === String(currentUser?.tenantId ?? '')
    if (!isRoleOwner) {
      message.error('无权移除未由本租户管理的角色成员')
      return
    }
    try {
      const targetTenant = currentUser?.tenantId || selectedRoleForUsers.tenantId
      await unbindUserRole(selectedRoleForUsers.id, userRecord.userId, targetTenant)
      message.success(`已成功将用户「${userRecord.realName || userRecord.username}」从该角色解绑`)
      void loadRoleUsers(selectedRoleForUsers)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const openAddMember = async () => {
    if (!selectedRoleForUsers) return
    setAddMemberModalOpen(true)
    setSelectedUserIdsToAdd([])
    setCandidateLoading(true)
    try {
      const targetTenant = isSuperAdmin
        ? (selectedTenantId && selectedTenantId !== '10001' ? selectedTenantId : undefined)
        : currentUser?.tenantId
      const allUsersRes = await listUsers({ tenantId: targetTenant, page: 0, size: 200 })
      const assignedSet = new Set(roleUsers.map((u) => u.userId))
      const candidates = (allUsersRes.records ?? []).filter(
        (u) => !assignedSet.has(u.userId) && u.status === 'ACTIVE',
      )
      setAvailableCandidateUsers(candidates)
    } catch (error) {
      message.error('加载可选用户失败：' + resolveErrorMessage(error))
    } finally {
      setCandidateLoading(false)
    }
  }

  const handleAddMembers = async () => {
    if (!selectedRoleForUsers || selectedUserIdsToAdd.length === 0) {
      message.warning('请选择至少一名用户')
      return
    }
    setAddMemberSubmitting(true)
    try {
      for (const uid of selectedUserIdsToAdd) {
        const candidate = availableCandidateUsers.find((u) => u.userId === uid)
        const targetTenant = candidate?.tenantId || (isSuperAdmin ? (selectedTenantId || '10001') : currentUser?.tenantId) || '10001'
        await bindUserRole(uid, selectedRoleForUsers.id, { version: 0 }, targetTenant)
      }
      message.success(`成功为角色分配 ${selectedUserIdsToAdd.length} 名用户`)
      setAddMemberModalOpen(false)
      setSelectedUserIdsToAdd([])
      void loadRoleUsers(selectedRoleForUsers)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setAddMemberSubmitting(false)
    }
  }

  const filteredRoleUsers = useMemo(() => {
    const kw = roleUserKeyword.trim().toLowerCase()
    if (!kw) return roleUsers
    return roleUsers.filter(
      (u) =>
        u.userId.toLowerCase().includes(kw) ||
        u.username.toLowerCase().includes(kw) ||
        (u.realName && u.realName.toLowerCase().includes(kw)) ||
        (u.email && u.email.toLowerCase().includes(kw)),
    )
  }, [roleUsers, roleUserKeyword])

  const columns: TableColumnsType<RoleItem> = [
    { title: '角色ID', dataIndex: 'id', width: 200, render: renderId },
    ...(currentUser?.tenantId === '10001'
      ? [
          {
            title: '所属租户',
            dataIndex: 'tenantId',
            width: 140,
            render: (value?: string) => {
              if (!value) return '—'
              const found = tenantList.find((t) => t.id === value)
              return (
                <Tag color={value === '10001' ? 'gold' : 'geekblue'}>
                  {found ? `${found.tenantName} (${value})` : `租户 ${value}`}
                </Tag>
              )
            },
          },
        ]
      : []),
    { title: '角色编码', dataIndex: 'roleCode', width: 160, render: (value: string) => <Tag color="blue">{value}</Tag> },
    { title: '角色名称', dataIndex: 'roleName', ellipsis: true },
    { title: '状态', dataIndex: 'status', width: 90, render: renderStatus },
    { title: '版本', dataIndex: 'version', width: 70 },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 300,
      render: (_, record) => {
        const isOwnTenantRole = String(record.tenantId ?? '') === String(currentUser?.tenantId ?? '')
        return (
          <Space size={4}>
            <Button
              type="link"
              size="small"
              icon={<TeamOutlined />}
              onClick={() => void openRoleUsers(record)}
            >
              已分配用户
            </Button>
            {hasOperation('role:edit-permissions') && (
              isOwnTenantRole ? (
                <Button type="link" size="small" onClick={() => void openPerms(record)}>
                  编辑权限
                </Button>
              ) : (
                <Tooltip title="非本租户创建的角色，无权修改权限">
                  <Button type="link" size="small" disabled>
                    编辑权限
                  </Button>
                </Tooltip>
              )
            )}
            {record.status === 'ACTIVE' ? (
              isOwnTenantRole ? (
                <Popconfirm
                  title="禁用角色"
                  description={`确定要禁用角色「${record.roleName}」吗？禁用后该角色的用户将失去对应权限`}
                  onConfirm={() => void handleDisableRole(record)}
                  okText="确定禁用"
                  cancelText="取消"
                  okButtonProps={{ danger: true }}
                >
                  <Button type="link" size="small" danger>
                    禁用
                  </Button>
                </Popconfirm>
              ) : (
                <Tooltip title="非本租户创建的角色，无权禁用">
                  <Button type="link" size="small" danger disabled>
                    禁用
                  </Button>
                </Tooltip>
              )
            ) : (
              isOwnTenantRole ? (
                <Popconfirm
                  title="启用角色"
                  description={`确定要重新启用角色「${record.roleName}」吗？`}
                  onConfirm={() => void handleEnableRole(record)}
                  okText="确定启用"
                  cancelText="取消"
                >
                  <Button type="link" size="small">
                    启用
                  </Button>
                </Popconfirm>
              ) : (
                <Tooltip title="非本租户创建的角色，无权启用">
                  <Button type="link" size="small" disabled>
                    启用
                  </Button>
                </Tooltip>
              )
            )}
            {isOwnTenantRole ? (
              <Popconfirm
                title="删除角色"
                description={`确定要永久删除角色「${record.roleName}」吗？相关用户绑定的该角色将被解绑`}
                onConfirm={() => void handleDeleteRole(record)}
                okText="确定删除"
                cancelText="取消"
                okButtonProps={{ danger: true }}
              >
                <Button type="link" size="small" danger>
                  删除
                </Button>
              </Popconfirm>
            ) : (
              <Tooltip title="非本租户创建的角色，无权删除">
                <Button type="link" size="small" danger disabled>
                  删除
                </Button>
              </Tooltip>
            )}
          </Space>
        )
      },
    },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-slate-800">角色管理</h1>
          <p className="mt-1 text-sm text-slate-400">
            创建租户角色并管理功能权限集
          </p>
        </div>
        <Space wrap>
          <Button icon={<RightOutlined />} onClick={() => navigate('/identity/users')}>
            绑定用户角色
          </Button>
          {hasOperation('role:create') && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建角色
            </Button>
          )}
        </Space>
      </div>

      <Card className="rounded-2xl" styles={{ body: { paddingTop: 16 } }}>
        <div className="mb-4 flex flex-wrap items-center gap-3">
          {isSuperAdmin && (
            <Select
              allowClear
              placeholder="按租户筛选（默认全部）"
              style={{ width: 220 }}
              value={draftTenantId}
              options={[
                { value: '', label: '全部租户' },
                ...tenantList.map((t) => ({
                  value: t.id,
                  label: `${t.tenantName} (${t.tenantCode} - ${t.id})`,
                })),
              ]}
              onChange={(val) => setDraftTenantId(val || undefined)}
            />
          )}
          <Input
            allowClear
            placeholder="按 ID / 编码 / 名称搜索"
            style={{ width: 240 }}
            prefix={<SearchOutlined className="text-slate-400" />}
            value={draftKeyword}
            onChange={(event) => setDraftKeyword(event.target.value)}
            onPressEnter={handleSearch}
          />
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 120 }}
            value={draftStatus}
            options={Object.entries(STATUS_TAG).map(([value, meta]) => ({ value, label: meta.label }))}
            onChange={(value) => setDraftStatus(value)}
          />
          <DatePicker.RangePicker
            placeholder={['创建开始日期', '创建结束日期']}
            style={{ width: 260 }}
            value={draftRange}
            onChange={(dates) => setDraftRange(dates)}
          />
          <Space>
            <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
              查询
            </Button>
            <Button icon={<ReloadOutlined />} onClick={handleReset}>
              重置
            </Button>
          </Space>
        </div>
        <Table<RoleItem>
          rowKey="id"
          size="middle"
          loading={loading}
          columns={columns}
          dataSource={filtered}
          pagination={{
            current: page,
            pageSize: pageSize,
            total: filtered.length,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100'],
            showTotal: (total) => `共 ${total} 条角色`,
            onChange: (p, s) => {
              setPage(p)
              setPageSize(s)
            },
          }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无角色数据"
                className="py-8"
              >
                <Button type="primary" onClick={openCreate}>
                  去创建
                </Button>
              </Empty>
            ),
          }}
        />
      </Card>

      <Drawer
        title="创建角色"
        open={activeModal === 'create'}
        onClose={() => setActiveModal(null)}
        width={520}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setActiveModal(null)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={handleCreate}>
              创建
            </Button>
          </Space>
        }
        footer={
          <div className="flex justify-end gap-2">
            <Button onClick={() => setActiveModal(null)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={handleCreate}>
              创建
            </Button>
          </div>
        }
      >
        <Form form={createForm} layout="vertical" className="pt-2" requiredMark={false}>
          {isSuperAdmin ? (
            <Form.Item
              name="formTenantId"
              label="归属租户"
              rules={[{ required: true, message: '请选择角色所属租户' }]}
            >
              <Select
                placeholder="选择角色归属的租户"
                options={tenantList.map((t) => ({
                  value: t.id,
                  label: `${t.tenantName} (${t.tenantCode} - ID: ${t.id})`,
                }))}
              />
            </Form.Item>
          ) : (
            <div className="mb-4 rounded-lg bg-slate-50 p-2.5 text-xs text-slate-600 border border-slate-200">
              角色归属租户：<span className="font-semibold text-blue-600">{currentUser?.tenantCode || `租户 ${currentUser?.tenantId}`}</span>（租户权限严格隔离，仅在当前租户生效）
            </div>
          )}
          <Form.Item
            name="roleCode"
            label="角色编码"
            extra="大写字母开头，仅含大写字母 / 数字 / 下划线，3-64 位"
            rules={[
              { required: true, message: '请输入角色编码' },
              { pattern: ROLE_CODE_PATTERN, message: '格式：大写字母开头，3-64 位大写字母 / 数字 / 下划线' },
            ]}
          >
            <Input placeholder="例如：PROJECT_ADMIN" maxLength={64} />
          </Form.Item>
          <Form.Item
            name="roleName"
            label="角色名称"
            rules={[{ required: true, message: '请输入角色名称' }]}
          >
            <Input placeholder="例如：项目管理员" maxLength={128} />
          </Form.Item>
          <Form.Item name="description" label="描述（可选）">
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={
          <div className="flex items-center gap-2">
            <span>配置角色菜单权限</span>
            {selectedRole && (
              <Tag color="geekblue">
                {selectedRole.roleName}（{selectedRole.roleCode}）
                {selectedRole.tenantId ? ` · 租户 ${selectedRole.tenantId}` : ''}
              </Tag>
            )}
          </div>
        }
        open={activeModal === 'perms'}
        onClose={() => setActiveModal(null)}
        width={680}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setActiveModal(null)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={() => void handleSaveRoleMenus()}>
              保存权限配置
            </Button>
          </Space>
        }
        footer={
          <div className="flex justify-end gap-2">
            <Button onClick={() => setActiveModal(null)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={() => void handleSaveRoleMenus()}>
              保存权限配置
            </Button>
          </div>
        }
      >
        <div className="space-y-3 pt-2">
          <Alert
            type="info"
            showIcon
            message="菜单权限说明"
            description="勾选该角色可访问的页面与功能菜单。系统在左侧侧边栏渲染及后端数据过滤（iam_role_menu 表）时将依据此配置直接生效。"
          />

          <div className="flex items-center justify-between border-b pb-2 pt-1 text-xs">
            <Space size="small">
              <Button size="small" onClick={handleSelectAll}>全选</Button>
              <Button size="small" onClick={handleClearAll}>全不选</Button>
              <Button size="small" onClick={handleExpandAll}>展开全部</Button>
              <Button size="small" onClick={handleCollapseAll}>折叠全部</Button>
            </Space>
            <span className="text-slate-500 font-medium">
              已选 <span className="text-blue-600 font-bold">{checkedMenuKeys.length}</span> / {allMenuKeys.length} 项
            </span>
          </div>

          <div className="max-h-[420px] overflow-y-auto rounded-lg border border-slate-200 bg-slate-50/50 p-3">
            {treeLoading ? (
              <div className="flex justify-center items-center py-12">
                <Spin tip="正在加载菜单权限树..." />
              </div>
            ) : menuTree.length === 0 ? (
              <Empty description="暂无菜单数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <Tree
                checkable
                checkStrictly={false}
                checkedKeys={checkedMenuKeys}
                onCheck={onTreeCheck}
                expandedKeys={expandedKeys}
                autoExpandParent={autoExpandParent}
                onExpand={(keys) => {
                  setExpandedKeys(keys)
                  setAutoExpandParent(false)
                }}
                treeData={treeData}
              />
            )}
          </div>
        </div>
      </Drawer>

      {/* 角色已分配用户抽屉 */}
      <Drawer
        title={
          <div className="flex items-center gap-2">
            <TeamOutlined className="text-blue-500" />
            <span>角色已分配用户列表</span>
            {selectedRoleForUsers && (
              <>
                <Tag color="blue">{selectedRoleForUsers.roleName}</Tag>
                <Tag>{selectedRoleForUsers.roleCode}</Tag>
              </>
            )}
          </div>
        }
        width={750}
        open={roleUsersDrawerOpen}
        onClose={() => setRoleUsersDrawerOpen(false)}
        extra={
          <Space>
            <Button
              icon={<ReloadOutlined />}
              size="small"
              onClick={() => selectedRoleForUsers && void loadRoleUsers(selectedRoleForUsers)}
              loading={roleUsersLoading}
            >
              刷新
            </Button>
            {String(selectedRoleForUsers?.tenantId ?? '') === String(currentUser?.tenantId ?? '') ? (
              <Button
                type="primary"
                size="small"
                icon={<UserAddOutlined />}
                onClick={() => void openAddMember()}
              >
                分配用户
              </Button>
            ) : (
              <Tooltip title="非本租户管理的角色，无权分配成员">
                <Button
                  type="primary"
                  size="small"
                  icon={<UserAddOutlined />}
                  disabled
                >
                  分配用户
                </Button>
              </Tooltip>
            )}
          </Space>
        }
      >
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <Input
              allowClear
              placeholder="搜索用户名 / 真实姓名"
              prefix={<SearchOutlined className="text-slate-400" />}
              className="max-w-xs"
              value={roleUserKeyword}
              onChange={(e) => setRoleUserKeyword(e.target.value)}
            />
            <div className="text-xs text-slate-500">
              共 <span className="font-semibold text-blue-600">{filteredRoleUsers.length}</span> 位用户分配该角色
            </div>
          </div>

          <Table<UserDetail>
            rowKey="userId"
            size="small"
            loading={roleUsersLoading}
            dataSource={filteredRoleUsers}
            pagination={{ pageSize: 10, showSizeChanger: false }}
            locale={{ emptyText: <Empty description="暂无分配该角色的用户" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
            columns={[
              { title: '用户ID', dataIndex: 'userId', width: 140, render: renderId },
              ...(currentUser?.tenantId === '10001'
                ? [
                    {
                      title: '所属租户',
                      dataIndex: 'tenantId',
                      width: 140,
                      render: (v?: string) => {
                        if (!v) return '—'
                        const found = tenantList.find((t) => t.id === v)
                        return (
                          <Tag color={v === '10001' ? 'gold' : 'geekblue'}>
                            {found ? `${found.tenantName} (${v})` : `租户 ${v}`}
                          </Tag>
                        )
                      },
                    },
                  ]
                : []),
              {
                title: '用户名',
                dataIndex: 'username',
                width: 120,
                render: (v: string, userRecord: UserDetail) => (
                  <Button
                    type="link"
                    className="p-0 font-medium text-blue-600 hover:text-blue-500"
                    onClick={() => {
                      setDetailUser(userRecord)
                      setDetailUserDrawerOpen(true)
                    }}
                  >
                    {v}
                  </Button>
                ),
              },
              { title: '真实姓名', dataIndex: 'realName', width: 120, render: (v) => v || '—' },
              { title: '状态', dataIndex: 'status', width: 80, render: renderStatus },
              { title: '邮箱', dataIndex: 'email', ellipsis: true, render: (v) => v || '—' },
              {
                title: '操作',
                key: 'action',
                width: 90,
                render: (_, userRecord) => {
                  const isRoleOwner = String(selectedRoleForUsers?.tenantId ?? '') === String(currentUser?.tenantId ?? '')
                  return isRoleOwner ? (
                    <Popconfirm
                      title="移除该角色成员？"
                      description={`确定要解除用户「${userRecord.realName || userRecord.username}」的该角色权限吗？`}
                      onConfirm={() => void handleUnbindUser(userRecord)}
                      okText="确定移除"
                      cancelText="取消"
                      okButtonProps={{ danger: true }}
                    >
                      <Button type="link" size="small" danger icon={<UserDeleteOutlined />}>
                        移除
                      </Button>
                    </Popconfirm>
                  ) : (
                    <Tooltip title="非本租户管理的角色成员，无权移除">
                      <Button type="link" size="small" danger icon={<UserDeleteOutlined />} disabled>
                        移除
                      </Button>
                    </Tooltip>
                  )
                },
              },
            ]}
          />
        </div>
      </Drawer>

      <UserDrawer
        open={detailUserDrawerOpen}
        mode="detail"
        user={detailUser}
        tenantId={detailUser?.tenantId || (currentUser?.tenantId === '10001' ? selectedTenantId : currentUser?.tenantId)}
        canEdit={false}
        onClose={() => {
          setDetailUserDrawerOpen(false)
          setDetailUser(null)
        }}
      />

      {/* 为角色分配用户抽屉 */}
      <Drawer
        title={`为角色【${selectedRoleForUsers?.roleName}】分配用户`}
        open={addMemberModalOpen}
        onClose={() => setAddMemberModalOpen(false)}
        width={520}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setAddMemberModalOpen(false)}>取消</Button>
            <Button type="primary" loading={addMemberSubmitting} onClick={handleAddMembers}>
              确认分配
            </Button>
          </Space>
        }
      >
        <div className="space-y-4 pt-2">
          <p className="text-xs text-slate-500">
            从当前租户中选择尚未分配该角色的有效用户进行快速分配绑定：
          </p>
          <Select
            mode="multiple"
            style={{ width: '100%' }}
            placeholder="请选择要分配的用户（可多选）"
            loading={candidateLoading}
            value={selectedUserIdsToAdd}
            onChange={(vals) => setSelectedUserIdsToAdd(vals)}
            filterOption={(input, option) =>
              String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())
            }
            options={availableCandidateUsers.map((u) => {
              const found = tenantList.find((t) => t.id === u.tenantId)
              const tenantSuffix = isSuperAdmin && u.tenantId ? ` · ${found ? found.tenantName : `租户 ${u.tenantId}`}` : ''
              return {
                value: u.userId,
                label: `${u.realName || u.username} (${u.username}${tenantSuffix}${u.email ? ` · ${u.email}` : ''})`,
              }
            })}
          />
        </div>
      </Drawer>
    </div>
  )
}
