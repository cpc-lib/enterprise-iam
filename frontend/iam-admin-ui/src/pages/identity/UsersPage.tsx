import { useCallback, useEffect, useState } from 'react'
import {
  CheckCircleOutlined,
  DisconnectOutlined,
  EditOutlined,
  EyeOutlined,
  KeyOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  StopOutlined,
  UsergroupAddOutlined,
} from '@ant-design/icons'
import {
  App,
  Button,
  Card,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { resolveErrorMessage } from '@/api/client/errors'
import { bindUserRole, getRoles, getUserRoles } from '@/api/identity/roles'
import {
  disableUser,
  enableUser,
  kickUserSessions,
  listUsers,
  resetUserPassword,
} from '@/api/identity/users'
import { getTenants } from '@/api/system/tenants'
import type { TenantItem } from '@/api/system/tenants'
import { UserDrawer, type UserDrawerMode } from '@/components/UserDrawer'
import { usePageSchema } from '@/hooks/useServerMetadata'
import { useAuthStore } from '@/stores/auth'
import { dayjs } from '@/utils/dayjs'
import type {
  DisableUserCommand,
  RoleItem,
  UserDetail,
} from '@/types/identity'

const STATUS_TAG: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '启用' },
  DISABLED: { color: 'red', label: '停用' },
  INVITED: { color: 'blue', label: '已邀请' },
}

interface DisableFormValues {
  userId: string
  reason: string
}

interface BindFormValues {
  userId: string
  roleId: string
}

interface ResetPasswordFormValues {
  newPassword?: string
  reason?: string
}

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

export default function UsersPage() {
  const { message } = App.useApp()
  const user = useAuthStore((s) => s.user)
  const isSuperAdmin = user?.tenantId === '10001'
  const [draftTenantId, setDraftTenantId] = useState<string | undefined>(undefined)
  const [draftKeyword, setDraftKeyword] = useState('')
  const [draftStatus, setDraftStatus] = useState<string | undefined>(undefined)

  const [appliedTenantId, setAppliedTenantId] = useState<string | undefined>(undefined)
  const [appliedKeyword, setAppliedKeyword] = useState<string | undefined>(undefined)
  const [appliedStatus, setAppliedStatus] = useState<string | undefined>(undefined)

  const [tenantList, setTenantList] = useState<TenantItem[]>([])
  const { hasOperation } = usePageSchema('identity-users')

  const [users, setUsers] = useState<UserDetail[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [loading, setLoading] = useState(false)

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [drawerMode, setDrawerMode] = useState<UserDrawerMode>('detail')
  const [selectedUser, setSelectedUser] = useState<UserDetail | null>(null)
  const [disableOpen, setDisableOpen] = useState(false)
  const [disablingUser, setDisablingUser] = useState<UserDetail | null>(null)
  const [bindOpen, setBindOpen] = useState(false)
  const [bindingUserId, setBindingUserId] = useState<string | null>(null)
  const [bindingUser, setBindingUser] = useState<UserDetail | null>(null)
  const [availableRoles, setAvailableRoles] = useState<RoleItem[]>([])
  const [userAssignedRoles, setUserAssignedRoles] = useState<RoleItem[]>([])
  const [rolesLoading, setRolesLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const [resetOpen, setResetOpen] = useState(false)
  const [resettingUser, setResettingUser] = useState<UserDetail | null>(null)
  const [resetSuccessModalOpen, setResetSuccessModalOpen] = useState(false)
  const [resetResultData, setResetResultData] = useState<{
    userId: string
    username: string
    password: string
    isTemporary: boolean
  } | null>(null)

  const [disableForm] = Form.useForm<DisableFormValues>()
  const [bindForm] = Form.useForm<BindFormValues>()
  const [resetForm] = Form.useForm<ResetPasswordFormValues>()

  const isSuperAdminUser = (record?: UserDetail | null) =>
    !record
      ? false
      : record.userId === '100001' ||
        record.username === 'superadmin' ||
        record.email === 'superadmin@enterprise.com' ||
        record.tenantId === '10001'

  const isTenantAdminUser = (record?: UserDetail | null) =>
    !record
      ? false
      : !isSuperAdminUser(record) &&
        (record.isTenantAdmin === true ||
          record.username === 'tenant_admin' ||
          record.userId === '200001' ||
          record.email === 'admin@acme.com' ||
          record.username.endsWith('_admin'))

  const isReadOnlyNonAdminTenantUser = (record?: UserDetail | null) =>
    !record
      ? false
      : isSuperAdmin && String(record.tenantId ?? '') !== '10001' && !isTenantAdminUser(record)

  const handleOpenCreate = () => {
    setSelectedUser(null)
    setDrawerMode('create')
    setDrawerOpen(true)
  }

  const handleOpenDetail = (record: UserDetail) => {
    setSelectedUser(record)
    setDrawerMode('detail')
    setDrawerOpen(true)
  }

  const handleOpenEdit = (record: UserDetail) => {
    if (isReadOnlyNonAdminTenantUser(record)) {
      message.warning('超级系统管理员只能管理租户管理员，不能修改租户下的其他普通用户数据')
      return
    }
    setSelectedUser(record)
    setDrawerMode('edit')
    setDrawerOpen(true)
  }

  useEffect(() => {
    if (isSuperAdmin) {
      getTenants()
        .then((data) => setTenantList(data))
        .catch(() => {})
    }
  }, [isSuperAdmin])

  const fetchUsers = useCallback(async () => {
    setLoading(true)
    try {
      const targetTenant = isSuperAdmin ? appliedTenantId : user?.tenantId
      const res = await listUsers({
        tenantId: targetTenant,
        page: page - 1,
        size: pageSize,
        keyword: appliedKeyword,
        status: appliedStatus,
      })
      setUsers(res.records ?? [])
      setTotal(res.total ?? 0)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }, [isSuperAdmin, appliedTenantId, user?.tenantId, page, pageSize, appliedKeyword, appliedStatus, message])

  const handleSearch = () => {
    setAppliedTenantId(draftTenantId)
    setAppliedKeyword(draftKeyword.trim() || undefined)
    setAppliedStatus(draftStatus)
    setPage(1)
  }

  const handleReset = () => {
    setDraftTenantId(undefined)
    setDraftKeyword('')
    setDraftStatus(undefined)
    setAppliedTenantId(undefined)
    setAppliedKeyword(undefined)
    setAppliedStatus(undefined)
    setPage(1)
  }

  useEffect(() => {
    void fetchUsers()
  }, [fetchUsers])

  const handleEnable = async (record: UserDetail) => {
    if (isReadOnlyNonAdminTenantUser(record)) {
      message.warning('超级系统管理员只能管理租户管理员，不能操作租户下的其他普通用户')
      return
    }
    try {
      const targetTenant = record.tenantId || (isSuperAdmin ? (appliedTenantId || '10001') : (user?.tenantId || '10001'))
      await enableUser(record.userId, targetTenant, {
        version: Number(record.version ?? 0),
        reason: '管理员启用账号',
      })
      message.success(`用户「${record.username}」已启用`)
      void fetchUsers()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const handleDisable = async () => {
    if (!disablingUser) return
    if (String(disablingUser.userId) === String(user?.userId)) {
      message.warning(isSuperAdmin ? '系统管理员不能停用自己' : '租户管理员不能停用自己')
      return
    }
    if (isReadOnlyNonAdminTenantUser(disablingUser)) {
      message.warning('超级系统管理员只能管理租户管理员，不能操作租户下的其他普通用户')
      return
    }
    try {
      const values = await disableForm.validateFields()
      setSubmitting(true)
      const targetTenant = disablingUser.tenantId || (isSuperAdmin ? (appliedTenantId || '10001') : (user?.tenantId || '10001'))
      const cmd: DisableUserCommand = {
        version: Number(disablingUser.version ?? 0),
        reason: values.reason.trim(),
      }
      await disableUser(disablingUser.userId, cmd, targetTenant)
      message.success(`用户「${disablingUser.username}」已停用，会话已同步强制失效`)
      setDisableOpen(false)
      setDisablingUser(null)
      void fetchUsers()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const handleKickSessions = async (record: UserDetail) => {
    if (String(record.userId) === String(user?.userId)) {
      message.warning('不能强退当前登录账号')
      return
    }
    if (isReadOnlyNonAdminTenantUser(record)) {
      message.warning('超级系统管理员只能管理租户管理员，不能操作租户下的其他普通用户')
      return
    }
    try {
      const targetTenant = record.tenantId || (isSuperAdmin ? (appliedTenantId || '10001') : (user?.tenantId || '10001'))
      await kickUserSessions(record.userId, targetTenant)
      message.success(`用户「${record.username}」所有活跃会话已强制下线`)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const handleResetPassword = async () => {
    if (!resettingUser) return
    if (isReadOnlyNonAdminTenantUser(resettingUser)) {
      message.warning('超级系统管理员只能管理租户管理员，不能操作租户下的其他普通用户')
      return
    }
    try {
      const values = await resetForm.validateFields()
      setSubmitting(true)
      const targetTenant = resettingUser.tenantId || (isSuperAdmin ? (appliedTenantId || '10001') : (user?.tenantId || '10001'))
      const res = await resetUserPassword(resettingUser.userId, targetTenant, {
        newPassword: values.newPassword?.trim() || undefined,
        reason: values.reason?.trim() || undefined,
      })
      setResetOpen(false)
      resetForm.resetFields()
      setResetResultData({
        userId: resettingUser.userId,
        username: resettingUser.username,
        password: res.newPassword,
        isTemporary: res.isTemporary,
      })
      setResetSuccessModalOpen(true)
      message.success('密码重置成功')
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const handleOpenBindRole = async (record: UserDetail) => {
    if (isReadOnlyNonAdminTenantUser(record)) {
      message.warning('超级系统管理员只能管理租户管理员，不能操作租户下的其他普通用户')
      return
    }
    setBindingUser(record)
    setBindingUserId(record.userId)
    setUserAssignedRoles([])
    bindForm.resetFields()
    setBindOpen(true)
    setRolesLoading(true)
    try {
      const targetTenantId = record.tenantId || (isSuperAdmin ? (appliedTenantId || '10001') : (user?.tenantId || '10001'))
      const [roles, assigned] = await Promise.all([
        getRoles(targetTenantId),
        getUserRoles(record.userId, targetTenantId).catch(() => []),
      ])
      const activeRoles = roles.filter((r) => r.status === 'ACTIVE')
      setAvailableRoles(activeRoles)
      setUserAssignedRoles(assigned)
      if (assigned.length > 0) {
        bindForm.setFieldsValue({ roleId: String(assigned[0].id) })
      }
    } catch (err) {
      message.error('获取角色列表失败：' + resolveErrorMessage(err))
    } finally {
      setRolesLoading(false)
    }
  }

  const handleBindRole = async () => {
    if (!bindingUserId) return
    if (bindingUser && isReadOnlyNonAdminTenantUser(bindingUser)) {
      message.warning('超级系统管理员只能管理租户管理员，不能操作租户下的其他普通用户')
      return
    }
    try {
      const values = await bindForm.validateFields()
      setSubmitting(true)
      const targetTenant = bindingUser?.tenantId || (isSuperAdmin ? (appliedTenantId || '10001') : (user?.tenantId || '10001'))
      await bindUserRole(bindingUserId, String(values.roleId).trim(), { version: 0 }, targetTenant)
      message.success('角色绑定成功')
      setBindOpen(false)
      setBindingUserId(null)
      setBindingUser(null)
      void fetchUsers()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const columns: TableColumnsType<UserDetail> = [
    { title: '用户ID', dataIndex: 'userId', width: 140, render: renderId },
    ...(isSuperAdmin
      ? [
          {
            title: '所属租户',
            dataIndex: 'tenantId',
            width: 140,
            render: (v: string) => {
              const tenant = tenantList.find((t) => t.id === v)
              return (
                <Tag color={v === '10001' ? 'gold' : 'geekblue'}>
                  {tenant ? `${tenant.tenantName} (${v})` : `租户 ${v || '-'}`}
                </Tag>
              )
            },
          },
        ]
      : []),
    {
      title: '用户名',
      dataIndex: 'username',
      width: 140,
      render: (v: string, record: UserDetail) => (
        <Button
          type="link"
          className="p-0 font-medium text-blue-600 hover:text-blue-500"
          onClick={() => handleOpenDetail(record)}
        >
          {v}
        </Button>
      ),
    },
    { title: '姓名', dataIndex: 'realName', width: 120, render: (v) => v || '-' },
    { title: '邮箱', dataIndex: 'email', ellipsis: true, render: (v) => v || '-' },
    { title: '手机号', dataIndex: 'phone', width: 130, render: (v) => v || '-' },
    { title: '状态', dataIndex: 'status', width: 85, render: renderStatus },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 380,
      fixed: 'right',
      render: (_, record) => {
        const isTargetSuper = isSuperAdminUser(record)
        const isTargetAdmin = isTenantAdminUser(record)
        const isReadOnlyNonAdmin = isReadOnlyNonAdminTenantUser(record)
        const isSelf = String(record.userId) === String(user?.userId)
        return (
          <Space size={4}>
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => handleOpenDetail(record)}
            >
              详情
            </Button>
            {hasOperation('user:update') &&
              (isReadOnlyNonAdmin ? (
                <Tooltip title="超级系统管理员只能管理租户管理员，不能操作租户下的其他普通用户">
                  <span>
                    <Button type="link" size="small" icon={<EditOutlined />} disabled>
                      编辑
                    </Button>
                  </span>
                </Tooltip>
              ) : (
                <Button
                  type="link"
                  size="small"
                  icon={<EditOutlined />}
                  onClick={() => handleOpenEdit(record)}
                >
                  编辑
                </Button>
              ))}
            {hasOperation('user:bind-role') &&
              (isReadOnlyNonAdmin ? (
                <Tooltip title="超级系统管理员只能管理租户管理员，不能操作租户下的其他普通用户">
                  <span>
                    <Button type="link" size="small" icon={<UsergroupAddOutlined />} disabled>
                      绑角色
                    </Button>
                  </span>
                </Tooltip>
              ) : (
                <Button
                  type="link"
                  size="small"
                  icon={<UsergroupAddOutlined />}
                  onClick={() => void handleOpenBindRole(record)}
                >
                  绑角色
                </Button>
              ))}
            {(() => {
              // 是否允许重置密码判断：
              // 1. 系统管理员：可重置系统超级管理员自身和各租户的租户管理员，不能重置租户下的普通用户
              // 2. 租户管理员：只能重置本租户下的普通用户，不能重置任何租户管理员与超管
              let canReset = false
              let tip = ''

              if (isSuperAdmin) {
                if (isTargetAdmin || isTargetSuper) {
                  canReset = true
                } else {
                  canReset = false
                  tip = '系统管理员只能重置租户管理员密码，租户普通用户须由该租户管理员重置'
                }
              } else {
                if (isTargetSuper) {
                  canReset = false
                  tip = '租户管理员无权操作系统超级管理员账号'
                } else if (isTargetAdmin) {
                  canReset = false
                  tip = '租户管理员的密码只能由系统管理员重置'
                } else if (record.tenantId && user?.tenantId && record.tenantId !== user.tenantId) {
                  canReset = false
                  tip = '无权跨租户重置用户密码'
                } else {
                  canReset = true
                }
              }

              if (!canReset) {
                return (
                  <Tooltip title={tip}>
                    <span>
                      <Button type="link" size="small" icon={<KeyOutlined />} disabled>
                        重置密码
                      </Button>
                    </span>
                  </Tooltip>
                )
              }

              return (
                <Button
                  type="link"
                  size="small"
                  icon={<KeyOutlined />}
                  onClick={() => {
                    setResettingUser(record)
                    resetForm.resetFields()
                    setResetOpen(true)
                  }}
                >
                  重置密码
                </Button>
              )
            })()}
            {hasOperation('user:toggle-status') &&
              (isTargetSuper ? (
                <Tag color="gold" className="text-xs">
                  系统根账号
                </Tag>
              ) : isSelf ? (
                <Tooltip title={isSuperAdmin ? '系统管理员不能停用自己' : '租户管理员不能停用自己'}>
                  <span>
                    <Button type="link" size="small" danger icon={<StopOutlined />} disabled>
                      停用
                    </Button>
                  </span>
                </Tooltip>
              ) : isReadOnlyNonAdmin ? (
                <Tooltip
                  title="超级系统管理员只能管理租户管理员，不能操作租户下的其他普通用户"
                >
                  <span>
                    <Button
                      type="link"
                      size="small"
                      danger={record.status === 'ACTIVE'}
                      icon={record.status === 'ACTIVE' ? <StopOutlined /> : <CheckCircleOutlined />}
                      disabled
                    >
                      {record.status === 'ACTIVE' ? '停用' : '启用'}
                    </Button>
                  </span>
                </Tooltip>
              ) : record.status === 'ACTIVE' ? (
                <Button
                  type="link"
                  size="small"
                  danger
                  icon={<StopOutlined />}
                  onClick={() => {
                    setDisablingUser(record)
                    disableForm.resetFields()
                    setDisableOpen(true)
                  }}
                >
                  停用
                </Button>
              ) : (
                <Button
                  type="link"
                  size="small"
                  icon={<CheckCircleOutlined />}
                  onClick={() => void handleEnable(record)}
                >
                  启用
                </Button>
              ))}
            {hasOperation('user:kick') &&
              record.status === 'ACTIVE' &&
              (isTargetSuper ? null : isSelf ? (
                <Tooltip title="不能强退当前登录账号">
                  <span>
                    <Button type="link" size="small" danger icon={<DisconnectOutlined />} disabled>
                      强退
                    </Button>
                  </span>
                </Tooltip>
              ) : isReadOnlyNonAdmin ? (
                <Tooltip title="超级系统管理员只能管理租户管理员，不能操作租户下的其他普通用户">
                  <span>
                    <Button type="link" size="small" danger icon={<DisconnectOutlined />} disabled>
                      强退
                    </Button>
                  </span>
                </Tooltip>
              ) : (
                <Popconfirm
                  title="强制下线该用户？"
                  description="该用户的所有设备当前登录会话将立即失效并退出系统。"
                  onConfirm={() => void handleKickSessions(record)}
                  okText="强退"
                  cancelText="取消"
                  okButtonProps={{ danger: true }}
                >
                  <Button type="link" size="small" danger icon={<DisconnectOutlined />}>
                    强退
                  </Button>
                </Popconfirm>
              ))}
          </Space>
        )
      },
    },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-slate-800">用户管理</h1>
          <p className="mt-1 text-sm text-slate-400">
            租户用户全生命周期管理，支持角色绑定、密码重置、账号启停及会话强制失效
          </p>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void fetchUsers()} loading={loading}>
            刷新
          </Button>
          {hasOperation('user:create') && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={handleOpenCreate}
            >
              新建用户
            </Button>
          )}
        </Space>
      </div>

      <Card className="rounded-2xl" styles={{ body: { paddingTop: 16 } }}>
        <div className="mb-4 flex flex-wrap items-center gap-3">
          {isSuperAdmin && (
            <Select
              allowClear
              placeholder="跨租户筛选：全部租户"
              style={{ width: 220 }}
              value={draftTenantId}
              onChange={(val) => setDraftTenantId(val || undefined)}
              options={[
                { value: undefined, label: '全部租户 (跨租户)' },
                ...tenantList.map((t) => ({
                  value: t.id,
                  label: `${t.tenantName} (${t.id})`,
                })),
              ]}
            />
          )}
          <Input
            allowClear
            placeholder="搜索用户名 / 姓名 / 邮箱"
            style={{ width: 260 }}
            prefix={<SearchOutlined className="text-slate-400" />}
            value={draftKeyword}
            onChange={(e) => setDraftKeyword(e.target.value)}
            onPressEnter={handleSearch}
          />
          <Select
            allowClear
            placeholder="账号状态"
            style={{ width: 130 }}
            value={draftStatus}
            onChange={(val) => setDraftStatus(val)}
            options={[
              { value: 'ACTIVE', label: '启用' },
              { value: 'DISABLED', label: '停用' },
            ]}
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

        <Table<UserDetail>
          rowKey="userId"
          size="middle"
          loading={loading}
          columns={columns}
          dataSource={users}
          scroll={{ x: 1200 }}
          pagination={{
            current: page,
            pageSize,
            total,
            defaultPageSize: 10,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100'],
            onChange: (p, s) => {
              setPage(p)
              setPageSize(s)
            },
            showTotal: (t) => `共 ${t} 名用户`,
          }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无用户数据"
                className="py-8"
              >
                <Button type="primary" onClick={handleOpenCreate}>
                  新建用户
                </Button>
              </Empty>
            ),
          }}
        />
      </Card>

      <UserDrawer
        open={drawerOpen}
        mode={drawerMode}
        user={selectedUser}
        tenantId={selectedUser?.tenantId || (isSuperAdmin ? appliedTenantId : user?.tenantId)}
        isSuperAdmin={isSuperAdmin}
        tenantList={tenantList}
        canEdit={hasOperation('user:update') && !isReadOnlyNonAdminTenantUser(selectedUser)}
        onClose={() => {
          setDrawerOpen(false)
          setSelectedUser(null)
        }}
        onSuccess={() => {
          void fetchUsers()
        }}
      />

      <Modal
        title="停用用户"
        open={disableOpen}
        onOk={handleDisable}
        onCancel={() => {
          setDisableOpen(false)
          setDisablingUser(null)
        }}
        confirmLoading={submitting}
        okText="确认停用"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        destroyOnClose
      >
        <Form form={disableForm} layout="vertical" className="pt-2" requiredMark={false}>
          <p className="text-sm text-slate-500 mb-3">
            停用用户「{disablingUser?.username}」后，该用户的所有登录会话将立即强制失效，无法再访问受保护资源。
          </p>
          <Form.Item
            name="reason"
            label="停用原因"
            rules={[{ required: true, message: '请输入停用原因' }]}
          >
            <Input.TextArea rows={3} placeholder="例如：员工离职/岗位变动/安全风险" maxLength={256} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={
          <div className="flex items-center gap-2">
            <span>绑定用户角色</span>
            {bindingUser && (
              <Tag color="geekblue">{bindingUser.realName || bindingUser.username}</Tag>
            )}
          </div>
        }
        open={bindOpen}
        onClose={() => {
          setBindOpen(false)
          setBindingUserId(null)
          setBindingUser(null)
          setUserAssignedRoles([])
        }}
        width={520}
        destroyOnClose
        extra={
          <Space>
            <Button
              onClick={() => {
                setBindOpen(false)
                setBindingUserId(null)
                setBindingUser(null)
                setUserAssignedRoles([])
              }}
            >
              取消
            </Button>
            <Button type="primary" loading={submitting} onClick={handleBindRole}>
              确认绑定
            </Button>
          </Space>
        }
      >
        <Form form={bindForm} layout="vertical" className="pt-2" requiredMark={false}>
          <div className="mb-4 rounded-xl bg-slate-50 p-3 text-xs border border-slate-100">
            <div className="text-slate-500 mb-1">
              当前用户：<span className="font-semibold text-slate-700">{bindingUser?.username}</span>（租户 {bindingUser?.tenantId}）
            </div>
            <div className="flex items-center gap-2 mt-2">
              <span className="text-slate-500">已分配角色：</span>
              {userAssignedRoles.length > 0 ? (
                <Space size={[4, 4]} wrap>
                  {userAssignedRoles.map((r) => (
                    <Tag key={r.id} color="geekblue">{r.roleName} ({r.roleCode})</Tag>
                  ))}
                </Space>
              ) : (
                <span className="text-slate-400">暂无已分配角色</span>
              )}
            </div>
          </div>
          <Form.Item
            name="roleId"
            label="选择角色"
            rules={[{ required: true, message: '请从角色列表中选择要绑定的角色' }]}
            help="选择新角色进行绑定。若已有角色，将赋予该用户新角色的所有操作与菜单权限。"
          >
            <Select
              placeholder={rolesLoading ? '加载角色列表中...' : '请从角色列表中选择'}
              loading={rolesLoading}
              showSearch
              filterOption={(input, option) =>
                String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
              options={availableRoles.map((r) => {
                const isAssigned = userAssignedRoles.some((ar) => String(ar.id) === String(r.id))
                return {
                  value: String(r.id),
                  label: `${r.roleName} (${r.roleCode})${isAssigned ? ' [当前已分配]' : ''}`,
                }
              })}
            />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={
          <div className="flex items-center gap-2">
            <KeyOutlined className="text-blue-500" />
            <span>重置用户密码</span>
            {resettingUser && (
              <Tag color="blue">{resettingUser.realName || resettingUser.username}</Tag>
            )}
          </div>
        }
        open={resetOpen}
        onClose={() => {
          setResetOpen(false)
          setResettingUser(null)
          resetForm.resetFields()
        }}
        width={520}
        destroyOnClose
        extra={
          <Space>
            <Button
              onClick={() => {
                setResetOpen(false)
                setResettingUser(null)
                resetForm.resetFields()
              }}
            >
              取消
            </Button>
            <Button type="primary" loading={submitting} onClick={handleResetPassword}>
              确认重置
            </Button>
          </Space>
        }
      >
        <Form form={resetForm} layout="vertical" className="pt-2" requiredMark={false}>
          <div className="mb-4 rounded-lg bg-slate-50 p-3 text-xs text-slate-500">
            <div>账号：<span className="font-semibold text-slate-700">{resettingUser?.username}</span></div>
            <div>归属租户：<span className="font-semibold text-slate-700">{resettingUser?.tenantId}</span></div>
            <div className="mt-1 text-slate-400">重置后该用户的所有在线会话将被立即强制下线。</div>
          </div>
          <Form.Item
            name="newPassword"
            label="新密码（留空则系统自动生成高强度临时密码）"
            rules={[
              {
                validator(_, value: string | undefined) {
                  if (!value) return Promise.resolve()
                  if (value.length < 8 || value.length > 128) {
                    return Promise.reject(new Error('新密码长度须为 8–128 个字符'))
                  }
                },
              },
            ]}
          >
            <Input.Password placeholder="可指定新密码（8–128字符），或留空自动生成" autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="reason" label="重置原因（可选）">
            <Input placeholder="例如：用户遗忘密码申请重置" maxLength={128} />
          </Form.Item>
        </Form>
      </Drawer>

      <Modal
        title="密码重置成功"
        open={resetSuccessModalOpen}
        onOk={() => {
          setResetSuccessModalOpen(false)
          setResetResultData(null)
        }}
        onCancel={() => {
          setResetSuccessModalOpen(false)
          setResetResultData(null)
        }}
        footer={[
          <Button
            key="ok"
            type="primary"
            onClick={() => {
              setResetSuccessModalOpen(false)
              setResetResultData(null)
            }}
          >
            我已知晓并已妥善传达
          </Button>,
        ]}
        destroyOnClose
      >
        <div className="py-4 space-y-3">
          <p className="text-sm text-slate-600">
            用户 <Tag color="blue">{resetResultData?.username}</Tag> 的登录密码已重置成功。请妥善传达给该用户：
          </p>
          <div className="flex items-center justify-between rounded-xl bg-slate-100 p-4 border border-slate-200">
            <Typography.Text copyable className="font-mono text-base font-bold text-blue-600">
              {resetResultData?.password}
            </Typography.Text>
            {resetResultData?.isTemporary && (
              <Tag color="orange">系统生成临时密码</Tag>
            )}
          </div>
          <p className="text-xs text-slate-400">
            提示：用户使用新密码登录后，可通过右上角「修改密码」菜单自行更新。
          </p>
        </div>
      </Modal>
    </div>
  )
}
