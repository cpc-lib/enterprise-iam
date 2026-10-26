import { useCallback, useEffect, useState } from 'react'
import {
  ArrowLeftOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  EditOutlined,
  IdcardOutlined,
  MailOutlined,
  PhoneOutlined,
  PlusCircleOutlined,
  SaveOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  App,
  Avatar,
  Button,
  Descriptions,
  Drawer,
  Form,
  Input,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd'
import type { IdentityType } from '@/api/client/auth'
import { resolveErrorMessage } from '@/api/client/errors'
import { getUserRoles } from '@/api/identity/roles'
import { createUser, getUserDetail, updateUser } from '@/api/identity/users'
import type { TenantItem } from '@/api/system/tenants'
import type { CreateUserRequest, RoleItem, UpdateUserRequest, UserDetail } from '@/types/identity'
import { dayjs } from '@/utils/dayjs'

export type UserDrawerMode = 'create' | 'edit' | 'detail'

const IDENTITY_TYPE_OPTIONS = [
  { value: 'USERNAME', label: '用户名' },
  { value: 'EMAIL', label: '邮箱' },
  { value: 'PHONE', label: '手机号' },
]

export interface UserDrawerProps {
  /** 控制抽屉显隐 */
  open: boolean
  /** 模式：'create' 新建用户 / 'edit' 编辑资料 / 'detail' 查看详情，默认为 'detail' */
  mode?: UserDrawerMode
  /** 用户对象（首屏渲染直接可用） */
  user?: UserDetail | null
  /** 用户 ID（用于异步加载最新详情） */
  userId?: string | null
  /** 租户 ID */
  tenantId?: string
  /** 是否系统超级管理员 */
  isSuperAdmin?: boolean
  /** 租户列表（用于超管新建用户时选择所属租户） */
  tenantList?: TenantItem[]
  /** 是否具备编辑权限，默认为 true */
  canEdit?: boolean
  /** 关闭回调 */
  onClose: () => void
  /** 创建或编辑保存成功回调 */
  onSuccess?: (user?: UserDetail) => void
}

interface CreateFormValues {
  formTenantId?: string
  displayName: string
  identityType: IdentityType
  identity: string
  initialPassword?: string
}

interface EditFormValues {
  realName?: string
  email?: string
  phone?: string
}

export function UserDrawer({
  open,
  mode = 'detail',
  user,
  userId,
  tenantId,
  isSuperAdmin = false,
  tenantList = [],
  canEdit = true,
  onClose,
  onSuccess,
}: UserDrawerProps) {
  const { message } = App.useApp()
  const [currentMode, setCurrentMode] = useState<UserDrawerMode>(mode)
  const [currentUser, setCurrentUser] = useState<UserDetail | null>(user ?? null)
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [userRoles, setUserRoles] = useState<RoleItem[]>([])
  const [rolesLoading, setRolesLoading] = useState(false)
  const [createForm] = Form.useForm<CreateFormValues>()
  const [editForm] = Form.useForm<EditFormValues>()

  // 同步外部 mode
  useEffect(() => {
    setCurrentMode(mode)
  }, [mode])

  // 当外部 user 变更时更新
  useEffect(() => {
    if (user) {
      setCurrentUser(user)
    }
  }, [user])

  // 抽屉打开时，如果进入 create 模式则重置新建表单
  useEffect(() => {
    if (open && currentMode === 'create') {
      createForm.resetFields()
      if (isSuperAdmin) {
        createForm.setFieldsValue({
          formTenantId: tenantId || '10001',
          identityType: 'USERNAME',
        })
      } else {
        createForm.setFieldsValue({
          identityType: 'USERNAME',
        })
      }
    }
  }, [open, currentMode, isSuperAdmin, tenantId, createForm])

  // 抽屉打开时，如果具备有效 ID 且处于 detail 或 edit 模式，则异步加载最新详情
  const refreshDetail = useCallback(async () => {
    const targetUserId = user?.userId || userId
    const targetTenantId = user?.tenantId || tenantId
    if (!targetUserId || !targetTenantId) return
    try {
      setLoading(true)
      const data = await getUserDetail(targetUserId, targetTenantId)
      if (data) {
        setCurrentUser(data)
      }
    } catch {
      // 保持现存 user，静默降级
    } finally {
      setLoading(false)
    }
  }, [user, userId, tenantId])

  useEffect(() => {
    if (open && (currentMode === 'detail' || currentMode === 'edit')) {
      void refreshDetail()
    }
  }, [open, currentMode, refreshDetail])

  // 抽屉在 detail 模式下异步加载已分配角色
  useEffect(() => {
    const targetUserId = currentUser?.userId || user?.userId || userId
    const targetTenantId = currentUser?.tenantId || user?.tenantId || tenantId
    if (open && currentMode === 'detail' && targetUserId && targetTenantId) {
      setRolesLoading(true)
      getUserRoles(targetUserId, targetTenantId)
        .then((roles) => setUserRoles(roles))
        .catch(() => setUserRoles([]))
        .finally(() => setRolesLoading(false))
    } else if (!open) {
      setUserRoles([])
    }
  }, [open, currentMode, currentUser?.userId, currentUser?.tenantId, user?.userId, user?.tenantId, userId, tenantId])

  // 模式切换为 edit 时回填表单
  useEffect(() => {
    if (open && currentMode === 'edit' && currentUser) {
      editForm.setFieldsValue({
        realName: currentUser.realName || undefined,
        email: currentUser.email || undefined,
        phone: currentUser.phone || undefined,
      })
    }
  }, [open, currentMode, currentUser, editForm])

  // 处理新建提交
  const handleCreateSubmit = async () => {
    try {
      const values = await createForm.validateFields()
      const targetTenant = isSuperAdmin
        ? (values.formTenantId || tenantId || '10001')
        : (tenantId || '10001')
      if (isSuperAdmin && String(targetTenant) !== '10001') {
        message.warning('超级系统管理员不能在业务租户下创建或修改用户数据，只能查看')
        return
      }
      setSubmitting(true)
      const req: CreateUserRequest = {
        displayName: values.displayName.trim(),
        identity: { type: values.identityType, value: values.identity.trim() },
        initialPassword: values.initialPassword || undefined,
      }
      await createUser(req, targetTenant)
      message.success('用户创建成功')
      createForm.resetFields()
      onSuccess?.()
      onClose()
    } catch (err) {
      message.error(resolveErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  // 处理编辑保存提交
  const handleEditSubmit = async () => {
    if (!currentUser) return
    const isTargetAdmin = currentUser.isTenantAdmin ?? (
      currentUser.userId === '200001' ||
      currentUser.username === 'tenant_admin' ||
      currentUser.username.endsWith('_admin')
    )
    if (isSuperAdmin && String(currentUser.tenantId ?? '') !== '10001' && !isTargetAdmin) {
      message.warning('超级系统管理员只能管理租户管理员，不能修改租户下的其他普通用户数据')
      return
    }
    try {
      const values = await editForm.validateFields()
      setSubmitting(true)
      const targetTenant = currentUser.tenantId || tenantId || '10001'
      const req: UpdateUserRequest = {
        realName: values.realName?.trim() || undefined,
        email: values.email?.trim() || undefined,
        phone: values.phone?.trim() || undefined,
      }
      await updateUser(currentUser.userId, targetTenant, req)
      message.success('用户资料更新成功')
      const updatedUser: UserDetail = {
        ...currentUser,
        realName: req.realName ?? null,
        email: req.email ?? null,
        phone: req.phone ?? null,
        updatedAt: new Date().toISOString(),
      }
      setCurrentUser(updatedUser)
      onSuccess?.(updatedUser)
      if (mode === 'edit') {
        onClose()
      } else {
        setCurrentMode('detail')
      }
    } catch (err) {
      message.error(resolveErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  const isSuper =
    currentUser?.userId === '100001' ||
    currentUser?.username === 'superadmin' ||
    currentUser?.tenantId === '10001'

  const isTenantAdmin =
    !isSuper &&
    (currentUser?.username === 'tenant_admin' ||
      currentUser?.userId === '200001' ||
      currentUser?.username?.endsWith('_admin'))

  const drawerTitle = (
    <div className="flex items-center gap-2">
      {currentMode === 'create' ? (
        <>
          <PlusCircleOutlined className="text-blue-500 text-lg" />
          <span className="font-semibold text-slate-800">新建用户</span>
        </>
      ) : currentMode === 'detail' ? (
        <>
          <IdcardOutlined className="text-blue-500 text-lg" />
          <span className="font-semibold text-slate-800">用户详情</span>
        </>
      ) : (
        <>
          <EditOutlined className="text-blue-500 text-lg" />
          <span className="font-semibold text-slate-800">编辑用户资料</span>
        </>
      )}
    </div>
  )

  const drawerExtra = (
    <Space size={8}>
      {currentMode === 'detail' && canEdit && (
        <Button
          type="link"
          size="small"
          icon={<EditOutlined />}
          onClick={() => setCurrentMode('edit')}
        >
          编辑资料
        </Button>
      )}
      {currentMode === 'edit' && mode === 'detail' && (
        <Button
          type="link"
          size="small"
          icon={<ArrowLeftOutlined />}
          onClick={() => setCurrentMode('detail')}
        >
          返回详情
        </Button>
      )}
    </Space>
  )

  const drawerFooter = (
    <div className="flex items-center justify-between">
      <span className="text-xs text-slate-400">
        {currentMode === 'create'
          ? '新建完成即可登录或绑定角色'
          : `ID: ${currentUser?.userId || '-'}`}
      </span>
      <Space>
        {currentMode === 'create' ? (
          <>
            <Button onClick={onClose}>取消</Button>
            <Button
              type="primary"
              loading={submitting}
              onClick={() => void handleCreateSubmit()}
            >
              创建用户
            </Button>
          </>
        ) : currentMode === 'detail' ? (
          <>
            <Button onClick={onClose}>关闭</Button>
            {canEdit && (
              <Button
                type="primary"
                icon={<EditOutlined />}
                onClick={() => setCurrentMode('edit')}
              >
                编辑资料
              </Button>
            )}
          </>
        ) : (
          <>
            <Button
              onClick={() => {
                if (mode === 'edit') {
                  onClose()
                } else {
                  setCurrentMode('detail')
                }
              }}
            >
              取消
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={submitting}
              onClick={() => void handleEditSubmit()}
            >
              保存修改
            </Button>
          </>
        )}
      </Space>
    </div>
  )

  return (
    <Drawer
      title={drawerTitle}
      extra={drawerExtra}
      footer={drawerFooter}
      open={open}
      onClose={onClose}
      width={560}
      destroyOnClose
      maskClosable
    >
      <Spin spinning={loading}>
        {currentMode === 'create' ? (
          /* ================= 新建用户抽屉 ================= */
          <div>
            <div className="mb-4 rounded-xl bg-blue-50/50 p-3 text-xs text-slate-600 border border-blue-100/60 leading-relaxed">
              在租户下录入新成员账号。账号创建完成后，可在列表点击「绑角色」分配权限，或点击详情查阅。
            </div>
            <Form form={createForm} layout="vertical" className="pt-1" requiredMark={false}>
              {isSuperAdmin && (
                <Form.Item
                  name="formTenantId"
                  label="所属租户"
                  rules={[{ required: true, message: '请选择所属租户' }]}
                >
                  <Select
                    placeholder="请选择所属租户"
                    options={tenantList.map((t) => ({
                      value: t.id,
                      label: `${t.tenantName} (${t.id})${t.id !== '10001' ? ' [业务租户-超管只读]' : ''}`,
                      disabled: t.id !== '10001',
                    }))}
                  />
                </Form.Item>
              )}
              <Form.Item
                name="displayName"
                label="显示名称 / 用户名"
                rules={[{ required: true, message: '请输入用户名' }]}
              >
                <Input placeholder="例如：zhangsan" maxLength={64} />
              </Form.Item>
              <Form.Item
                name="identityType"
                label="凭据类型"
                initialValue="USERNAME"
                rules={[{ required: true }]}
              >
                <Select options={IDENTITY_TYPE_OPTIONS} />
              </Form.Item>
              <Form.Item
                name="identity"
                label="登录凭据值"
                rules={[{ required: true, message: '请输入登录账号/凭据值' }]}
              >
                <Input placeholder="与凭据类型对应，如用户名/邮箱/手机号" maxLength={128} />
              </Form.Item>
              <Form.Item name="initialPassword" label="初始密码（可选）">
                <Input.Password placeholder="留空则由系统生成临时初始密码" />
              </Form.Item>
            </Form>
          </div>
        ) : currentUser ? (
          <div>
            {currentMode === 'detail' ? (
              /* ================= 查看详情抽屉 ================= */
              <div className="space-y-6">
                {/* 顶部身份卡片 */}
                <div className="rounded-2xl bg-gradient-to-r from-blue-50/70 via-indigo-50/40 to-slate-50 p-5 border border-blue-100/60 shadow-sm">
                  <div className="flex items-center gap-4">
                    <Avatar
                      size={54}
                      className="bg-blue-600 font-semibold text-xl shadow-md"
                      icon={<UserOutlined />}
                    >
                      {(currentUser.realName || currentUser.username || 'U').slice(0, 1).toUpperCase()}
                    </Avatar>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <h3 className="text-lg font-bold text-slate-800 truncate m-0">
                          {currentUser.realName || currentUser.username}
                        </h3>
                        {currentUser.status === 'ACTIVE' ? (
                          <Tag color="green" icon={<CheckCircleOutlined />}>启用</Tag>
                        ) : currentUser.status === 'DISABLED' ? (
                          <Tag color="red" icon={<CloseCircleOutlined />}>停用</Tag>
                        ) : (
                          <Tag color="blue">{currentUser.status || '正常'}</Tag>
                        )}
                        {isSuper ? (
                          <Tag color="gold">系统超管</Tag>
                        ) : isTenantAdmin ? (
                          <Tag color="geekblue">租户管理员</Tag>
                        ) : (
                          <Tag color="default">企业普通成员</Tag>
                        )}
                      </div>
                      <p className="mt-1 mb-0 text-xs text-slate-500 font-mono">
                        登录账号：{currentUser.username}
                      </p>
                    </div>
                  </div>
                </div>

                {/* 账号基础信息 */}
                <Descriptions
                  title={<span className="text-sm font-semibold text-slate-700">账号基础信息</span>}
                  column={1}
                  bordered
                  size="small"
                >
                  <Descriptions.Item label="用户 ID">
                    <Typography.Text copyable className="font-mono text-xs">
                      {currentUser.userId}
                    </Typography.Text>
                  </Descriptions.Item>
                  <Descriptions.Item label="登录用户名">
                    <span className="font-medium text-slate-700">{currentUser.username}</span>
                  </Descriptions.Item>
                  <Descriptions.Item label="真实姓名">
                    {currentUser.realName ? (
                      <span className="text-slate-800 font-medium">{currentUser.realName}</span>
                    ) : (
                      <span className="text-slate-400">未设置</span>
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label="所属租户">
                    <Tag color={currentUser.tenantId === '10001' ? 'gold' : 'geekblue'}>
                      租户 {currentUser.tenantId}
                    </Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="账号状态">
                    {currentUser.status === 'ACTIVE' ? (
                      <Tag color="green">启用 (ACTIVE)</Tag>
                    ) : currentUser.status === 'DISABLED' ? (
                      <Tag color="red">停用 (DISABLED)</Tag>
                    ) : (
                      <Tag color="default">{currentUser.status || '-'}</Tag>
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label="已分配角色">
                    {rolesLoading ? (
                      <span className="text-xs text-slate-400">加载角色中...</span>
                    ) : userRoles.length > 0 ? (
                      <Space size={[4, 6]} wrap>
                        {userRoles.map((r) => (
                          <Tag key={r.id} color="geekblue">
                            {r.roleName} ({r.roleCode})
                          </Tag>
                        ))}
                      </Space>
                    ) : (
                      <span className="text-slate-400">暂无已分配角色</span>
                    )}
                  </Descriptions.Item>
                </Descriptions>

                {/* 通讯联系方式 */}
                <Descriptions
                  title={<span className="text-sm font-semibold text-slate-700">通讯联系方式</span>}
                  column={1}
                  bordered
                  size="small"
                >
                  <Descriptions.Item label="电子邮箱">
                    {currentUser.email ? (
                      <Space size={6}>
                        <MailOutlined className="text-slate-400" />
                        <span className="font-mono text-slate-700">{currentUser.email}</span>
                      </Space>
                    ) : (
                      <span className="text-slate-400">未绑定邮箱</span>
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label="联系电话">
                    {currentUser.phone ? (
                      <Space size={6}>
                        <PhoneOutlined className="text-slate-400" />
                        <span className="font-mono text-slate-700">{currentUser.phone}</span>
                      </Space>
                    ) : (
                      <span className="text-slate-400">未绑定手机号</span>
                    )}
                  </Descriptions.Item>
                </Descriptions>

                {/* 安全与审计信息 */}
                <Descriptions
                  title={<span className="text-sm font-semibold text-slate-700">安全与审计信息</span>}
                  column={1}
                  bordered
                  size="small"
                >
                  <Descriptions.Item label="创建时间">
                    <Space size={6}>
                      <CalendarOutlined className="text-slate-400" />
                      <span>
                        {currentUser.createdAt
                          ? dayjs(currentUser.createdAt).format('YYYY-MM-DD HH:mm:ss')
                          : '-'}
                      </span>
                    </Space>
                  </Descriptions.Item>
                  <Descriptions.Item label="最近更新">
                    <span>
                      {currentUser.updatedAt
                        ? dayjs(currentUser.updatedAt).format('YYYY-MM-DD HH:mm:ss')
                        : '暂无更新记录'}
                    </span>
                  </Descriptions.Item>
                </Descriptions>

                <div className="rounded-xl bg-slate-50 p-3 text-xs text-slate-400 leading-relaxed border border-slate-100">
                  💡 提示：用户可同时归属多个部门与岗位，权限统一通过角色授权生效。修改资料可点击右上角「编辑资料」按钮。
                </div>
              </div>
            ) : (
              /* ================= 编辑资料抽屉 ================= */
              <div>
                <div className="mb-4 rounded-xl bg-blue-50/50 p-3 text-xs text-slate-600 border border-blue-100/60">
                  修改真实姓名与通讯联系方式将即时生效。用户名与所属租户为账号核心标识，不可直接变更。
                </div>
                <Form form={editForm} layout="vertical" className="pt-1" requiredMark={false}>
                  <Form.Item label="用户 ID">
                    <Input value={currentUser.userId} disabled prefix={<IdcardOutlined className="text-slate-400" />} />
                  </Form.Item>
                  <Form.Item label="登录用户名">
                    <Input value={currentUser.username} disabled prefix={<UserOutlined className="text-slate-400" />} />
                  </Form.Item>
                  <Form.Item
                    name="realName"
                    label="真实姓名"
                    rules={[{ max: 64, message: '真实姓名长度不能超过 64 个字符' }]}
                  >
                    <Input placeholder="请输入真实姓名" maxLength={64} allowClear />
                  </Form.Item>
                  <Form.Item
                    name="email"
                    label="电子邮箱"
                    rules={[
                      { type: 'email', message: '请输入格式合法的电子邮箱地址' },
                      { max: 128, message: '邮箱长度不能超过 128 个字符' },
                    ]}
                  >
                    <Input
                      placeholder="例如：name@example.com"
                      maxLength={128}
                      allowClear
                      prefix={<MailOutlined className="text-slate-400" />}
                    />
                  </Form.Item>
                  <Form.Item
                    name="phone"
                    label="手机号码"
                    rules={[
                      {
                        pattern: /^1[3-9]\d{9}$/,
                        message: '请输入正确的11位手机号码',
                      },
                    ]}
                  >
                    <Input
                      placeholder="例如：13800138000"
                      maxLength={32}
                      allowClear
                      prefix={<PhoneOutlined className="text-slate-400" />}
                    />
                  </Form.Item>
                </Form>
              </div>
            )}
          </div>
        ) : (
          <div className="py-12 text-center text-slate-400">
            暂无用户数据
          </div>
        )}
      </Spin>
    </Drawer>
  )
}

export default UserDrawer
