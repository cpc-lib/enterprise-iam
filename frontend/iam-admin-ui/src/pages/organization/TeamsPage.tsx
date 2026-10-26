import {
  DragOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyOutlined,
  SearchOutlined,
  UserAddOutlined,
  UserDeleteOutlined,
  UserSwitchOutlined,
} from '@ant-design/icons'
import {
  App,
  Button,
  Card,
  DatePicker,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import type { FormInstance, TableColumnsType } from 'antd'
import type { Dayjs } from 'dayjs'
import { useEffect, useMemo, useState } from 'react'
import { resolveErrorMessage } from '@/api/client/errors'
import {
  addTeamMember,
  bindTeamMemberRole,
  createTeam,
  createTeamRole,
  moveTeam,
  removeTeamMember,
  replaceTeamRolePermissions,
} from '@/api/organization/teams'
import { dayjs } from '@/utils/dayjs'
import { loadSessionList, saveSessionList } from '@/pages/identity/sessionCache'

const TEAMS_KEY = 'iam-session-teams'
const TEAM_ROLES_KEY = 'iam-session-team-roles'

const CODE_PATTERN = /^[A-Z][A-Z0-9_]{2,63}$/
const ID_PATTERN = /^[1-9][0-9]{0,18}$/

const STATUS_TAG: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '启用' },
  DISABLED: { color: 'red', label: '停用' },
}

interface SessionTeam {
  id: string
  teamCode: string
  teamName: string
  organizationId: string
  parentTeamId: string | null
  status: string
  version: number
  createdAt: string
}

interface SessionTeamRole {
  id: string
  teamId: string
  roleCode: string
  roleName: string
  description?: string | null
  status: string
  version: number
  createdAt: string
}

interface CreateTeamFormValues {
  organizationId: string
  parentTeamId?: string
  teamCode: string
  teamName: string
}

interface MoveTeamFormValues {
  teamId: string
  newParentTeamId?: string
  version: number
}

interface MemberFormValues {
  teamId: string
  userId: string
  version: number
}

interface RemoveMemberFormValues {
  teamId: string
  userId: string
  ifMatchVersion: number
}

interface CreateTeamRoleFormValues {
  teamId: string
  roleCode: string
  roleName: string
  description?: string
}

interface BindRoleFormValues {
  teamId: string
  userId: string
  teamRoleId: string
  version: number
}

interface RolePermsFormValues {
  teamRoleId: string
  version: number
  permissionIds?: string[]
}

type ActiveModal =
  | 'createTeam'
  | 'move'
  | 'addMember'
  | 'removeMember'
  | 'createRole'
  | 'bindRole'
  | 'rolePerms'
  | null

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

function renderParentId(value: string | null) {
  return value ? renderId(value) : <span className="text-slate-300">顶级</span>
}

function useSessionList<T>(key: string) {
  const [items, setItems] = useState<T[]>(() => loadSessionList<T>(key))
  useEffect(() => {
    saveSessionList(key, items)
  }, [key, items])
  return [items, setItems] as const
}

export default function TeamsPage() {
  const { message } = App.useApp()

  const [teams, setTeams] = useSessionList<SessionTeam>(TEAMS_KEY)
  const [teamRoles, setTeamRoles] = useSessionList<SessionTeamRole>(TEAM_ROLES_KEY)

  const [activeModal, setActiveModal] = useState<ActiveModal>(null)
  const [submitting, setSubmitting] = useState(false)

  const [draftKeyword, setDraftKeyword] = useState('')
  const [draftStatus, setDraftStatus] = useState<string | undefined>(undefined)
  const [draftRange, setDraftRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)

  const [appliedKeyword, setAppliedKeyword] = useState('')
  const [appliedStatus, setAppliedStatus] = useState<string | undefined>(undefined)
  const [appliedRange, setAppliedRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)

  const [teamForm] = Form.useForm<CreateTeamFormValues>()
  const [moveForm] = Form.useForm<MoveTeamFormValues>()
  const [memberForm] = Form.useForm<MemberFormValues>()
  const [removeMemberForm] = Form.useForm<RemoveMemberFormValues>()
  const [teamRoleForm] = Form.useForm<CreateTeamRoleFormValues>()
  const [bindRoleForm] = Form.useForm<BindRoleFormValues>()
  const [rolePermsForm] = Form.useForm<RolePermsFormValues>()

  const handleSearch = () => {
    setAppliedKeyword(draftKeyword.trim())
    setAppliedStatus(draftStatus)
    setAppliedRange(draftRange)
  }

  const handleReset = () => {
    setDraftKeyword('')
    setDraftStatus(undefined)
    setDraftRange(null)
    setAppliedKeyword('')
    setAppliedStatus(undefined)
    setAppliedRange(null)
  }

  const filteredTeams = useMemo(
    () =>
      filterByToolbar(teams, appliedKeyword, appliedStatus, appliedRange, (t) => [
        t.id,
        t.teamCode,
        t.teamName,
        t.organizationId,
      ]),
    [teams, appliedKeyword, appliedStatus, appliedRange],
  )
  const filteredTeamRoles = useMemo(
    () =>
      filterByToolbar(teamRoles, appliedKeyword, appliedStatus, appliedRange, (r) => [
        r.id,
        r.roleCode,
        r.roleName,
        r.teamId,
      ]),
    [teamRoles, appliedKeyword, appliedStatus, appliedRange],
  )

  const runCommand = async (action: () => Promise<string>) => {
    setSubmitting(true)
    try {
      const tip = await action()
      message.success(tip)
      setActiveModal(null)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const openModal = <F,>(form: FormInstance<F>, fields?: Partial<F>) => {
    form.resetFields()
    // Partial<F> 与 antd 递归部分类型在泛型下不可直接互赋，此处经由 never 断言收窄
    if (fields) form.setFieldsValue(fields as never)
  }

  const handleCreateTeam = async () => {
    try {
      await teamForm.validateFields()
    } catch {
      return
    }
    const { organizationId, parentTeamId, teamCode, teamName } = teamForm.getFieldsValue()
    const parent = parentTeamId?.trim() ? parentTeamId.trim() : null
    await runCommand(async () => {
      const result = await createTeam({
        organizationId,
        parentTeamId: parent,
        teamCode,
        teamName,
      })
      setTeams((prev) => [
        {
          id: result.id,
          teamCode,
          teamName,
          organizationId,
          parentTeamId: parent,
          status: result.status,
          version: result.version,
          createdAt: new Date().toISOString(),
        },
        ...prev,
      ])
      return `团队创建成功（ID：${result.id}）`
    })
  }

  const handleMoveTeam = async () => {
    try {
      await moveForm.validateFields()
    } catch {
      return
    }
    const { teamId, newParentTeamId, version } = moveForm.getFieldsValue()
    const parent = newParentTeamId?.trim() ? newParentTeamId.trim() : null
    await runCommand(async () => {
      const result = await moveTeam(teamId, { newParentTeamId: parent, version })
      setTeams((prev) =>
        prev.map((item) =>
          item.id === teamId
            ? { ...item, parentTeamId: parent, version: result.version, status: result.status }
            : item,
        ),
      )
      return `团队 ${teamId} 已移动`
    })
  }

  const handleAddMember = async () => {
    try {
      await memberForm.validateFields()
    } catch {
      return
    }
    const { teamId, userId, version } = memberForm.getFieldsValue()
    await runCommand(async () => {
      await addTeamMember(teamId, userId, version)
      return `用户 ${userId} 已加入团队`
    })
  }

  const handleRemoveMember = async () => {
    try {
      await removeMemberForm.validateFields()
    } catch {
      return
    }
    const { teamId, userId, ifMatchVersion } = removeMemberForm.getFieldsValue()
    await runCommand(async () => {
      await removeTeamMember(teamId, userId, ifMatchVersion)
      return `用户 ${userId} 已移出团队`
    })
  }

  const handleCreateTeamRole = async () => {
    try {
      await teamRoleForm.validateFields()
    } catch {
      return
    }
    const { teamId, roleCode, roleName, description } = teamRoleForm.getFieldsValue()
    await runCommand(async () => {
      const result = await createTeamRole(teamId, {
        roleCode,
        roleName,
        description: description?.trim() ? description.trim() : null,
      })
      setTeamRoles((prev) => [
        {
          id: result.id,
          teamId,
          roleCode,
          roleName,
          description: description?.trim() ?? null,
          status: result.status,
          version: result.version,
          createdAt: new Date().toISOString(),
        },
        ...prev,
      ])
      return `团队角色创建成功（ID：${result.id}）`
    })
  }

  const handleBindRole = async () => {
    try {
      await bindRoleForm.validateFields()
    } catch {
      return
    }
    const { teamId, userId, teamRoleId, version } = bindRoleForm.getFieldsValue()
    await runCommand(async () => {
      await bindTeamMemberRole(teamId, userId, teamRoleId, version)
      return `团队角色 ${teamRoleId} 已绑定到用户 ${userId}`
    })
  }

  const handleRolePerms = async () => {
    try {
      await rolePermsForm.validateFields()
    } catch {
      return
    }
    const { teamRoleId, version, permissionIds } = rolePermsForm.getFieldsValue()
    await runCommand(async () => {
      const result = await replaceTeamRolePermissions(teamRoleId, {
        version,
        permissionIds: (permissionIds ?? []).map((id) => id.trim()),
      })
      setTeamRoles((prev) =>
        prev.map((item) =>
          item.id === teamRoleId ? { ...item, version: result.bindingVersion } : item,
        ),
      )
      return `权限集已替换（权限版本：${result.permissionVersion}）`
    })
  }

  const teamColumns: TableColumnsType<SessionTeam> = [
    { title: '团队ID', dataIndex: 'id', width: 230, render: renderId },
    { title: '编码', dataIndex: 'teamCode', width: 140, render: (value: string) => <Tag>{value}</Tag> },
    { title: '名称', dataIndex: 'teamName', ellipsis: true },
    { title: '组织ID', dataIndex: 'organizationId', width: 220, render: renderId },
    {
      title: '上级团队',
      dataIndex: 'parentTeamId',
      width: 220,
      render: renderParentId,
    },
    { title: '状态', dataIndex: 'status', width: 90, render: renderStatus },
    { title: '版本', dataIndex: 'version', width: 70 },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm:ss'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      render: (_, record) => (
        <Button
          type="link"
          size="small"
          onClick={() => {
            openModal(moveForm, { teamId: record.id, version: record.version })
            setActiveModal('move')
          }}
        >
          移动
        </Button>
      ),
    },
  ]

  const teamRoleColumns: TableColumnsType<SessionTeamRole> = [
    { title: '团队角色ID', dataIndex: 'id', width: 230, render: renderId },
    { title: '所属团队ID', dataIndex: 'teamId', width: 220, render: renderId },
    { title: '编码', dataIndex: 'roleCode', width: 140, render: (value: string) => <Tag>{value}</Tag> },
    { title: '名称', dataIndex: 'roleName', ellipsis: true },
    { title: '状态', dataIndex: 'status', width: 90, render: renderStatus },
    { title: '版本', dataIndex: 'version', width: 70 },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm:ss'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 110,
      render: (_, record) => (
        <Button
          type="link"
          size="small"
          onClick={() => {
            openModal(rolePermsForm, { teamRoleId: record.id, version: record.version })
            setActiveModal('rolePerms')
          }}
        >
          权限集替换
        </Button>
      ),
    },
  ]

  const toolbar = (
    <div className="mb-4 flex flex-wrap items-center gap-3">
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
  )

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-semibold text-slate-800">团队管理</h1>
        <p className="mt-1 text-sm text-slate-400">
          创建团队、移动层级、管理成员与团队角色（当前版本开放命令接口，列表展示本次会话的记录）
        </p>
      </div>

      <Tabs
        defaultActiveKey="teams"
        items={[
          {
            key: 'teams',
            label: '团队',
            children: (
              <Card
                className="rounded-2xl"
                styles={{ body: { paddingTop: 16 } }}
                extra={
                  <Space wrap>
                    <Button
                      icon={<DragOutlined />}
                      onClick={() => {
                        openModal(moveForm)
                        setActiveModal('move')
                      }}
                    >
                      移动团队
                    </Button>
                    <Button
                      icon={<UserAddOutlined />}
                      onClick={() => {
                        openModal(memberForm)
                        setActiveModal('addMember')
                      }}
                    >
                      添加成员
                    </Button>
                    <Button
                      icon={<UserDeleteOutlined />}
                      onClick={() => {
                        openModal(removeMemberForm)
                        setActiveModal('removeMember')
                      }}
                    >
                      移除成员
                    </Button>
                    <Button
                      type="primary"
                      icon={<PlusOutlined />}
                      onClick={() => {
                        openModal(teamForm)
                        setActiveModal('createTeam')
                      }}
                    >
                      创建团队
                    </Button>
                  </Space>
                }
              >
                {toolbar}
                <Table<SessionTeam>
                  rowKey="id"
                  size="middle"
                  columns={teamColumns}
                  dataSource={filteredTeams}
                  pagination={{
                    defaultPageSize: 10,
                    pageSizeOptions: ['10', '20', '50', '100'],
                    showSizeChanger: true,
                    showTotal: (total) => `共 ${total} 条数据`,
                  }}
                  locale={{
                    emptyText: (
                      <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description="本次会话还没有创建团队"
                        className="py-8"
                      >
                        <Button
                          type="primary"
                          onClick={() => {
                            openModal(teamForm)
                            setActiveModal('createTeam')
                          }}
                        >
                          去创建
                        </Button>
                      </Empty>
                    ),
                  }}
                />
              </Card>
            ),
          },
          {
            key: 'teamRoles',
            label: '团队角色',
            children: (
              <Card
                className="rounded-2xl"
                styles={{ body: { paddingTop: 16 } }}
                extra={
                  <Space wrap>
                    <Button
                      icon={<UserSwitchOutlined />}
                      onClick={() => {
                        openModal(bindRoleForm)
                        setActiveModal('bindRole')
                      }}
                    >
                      绑定成员角色
                    </Button>
                    <Button
                      type="primary"
                      icon={<SafetyOutlined />}
                      onClick={() => {
                        openModal(teamRoleForm)
                        setActiveModal('createRole')
                      }}
                    >
                      创建团队角色
                    </Button>
                  </Space>
                }
              >
                {toolbar}
                <Table<SessionTeamRole>
                  rowKey="id"
                  size="middle"
                  columns={teamRoleColumns}
                  dataSource={filteredTeamRoles}
                  pagination={{
                    defaultPageSize: 10,
                    pageSizeOptions: ['10', '20', '50', '100'],
                    showSizeChanger: true,
                    showTotal: (total) => `共 ${total} 条数据`,
                  }}
                  locale={{
                    emptyText: (
                      <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description="本次会话还没有创建团队角色"
                        className="py-8"
                      >
                        <Button
                          type="primary"
                          onClick={() => {
                            openModal(teamRoleForm)
                            setActiveModal('createRole')
                          }}
                        >
                          去创建
                        </Button>
                      </Empty>
                    ),
                  }}
                />
              </Card>
            ),
          },
        ]}
      />

      <Drawer
        title="创建团队"
        open={activeModal === 'createTeam'}
        onClose={() => setActiveModal(null)}
        width={520}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setActiveModal(null)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={handleCreateTeam}>
              创建
            </Button>
          </Space>
        }
        footer={
          <div className="flex justify-end gap-2">
            <Button onClick={() => setActiveModal(null)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={handleCreateTeam}>
              创建
            </Button>
          </div>
        }
      >
        <Form form={teamForm} layout="vertical" className="pt-2" requiredMark={false}>
          <Form.Item
            name="organizationId"
            label="所属组织ID"
            rules={[
              { required: true, message: '请输入所属组织ID' },
              { pattern: ID_PATTERN, message: 'ID 需为 1-19 位正整数' },
            ]}
          >
            <Input placeholder="可在「组织管理」页创建后复制" />
          </Form.Item>
          <Form.Item
            name="parentTeamId"
            label="上级团队ID（可选）"
            extra="留空则创建为顶级团队"
            rules={[{ pattern: ID_PATTERN, message: 'ID 需为 1-19 位正整数' }]}
          >
            <Input placeholder="父团队ID" />
          </Form.Item>
          <Form.Item
            name="teamCode"
            label="团队编码"
            extra="大写字母开头，仅含大写字母 / 数字 / 下划线，3-64 位"
            rules={[
              { required: true, message: '请输入团队编码' },
              { pattern: CODE_PATTERN, message: '格式：大写字母开头，3-64 位大写字母 / 数字 / 下划线' },
            ]}
          >
            <Input placeholder="例如：OPS_FRONTLINE" maxLength={64} />
          </Form.Item>
          <Form.Item
            name="teamName"
            label="团队名称"
            rules={[{ required: true, message: '请输入团队名称' }]}
          >
            <Input placeholder="例如：一线运维组" maxLength={128} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title="移动团队"
        open={activeModal === 'move'}
        onClose={() => setActiveModal(null)}
        width={520}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setActiveModal(null)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={handleMoveTeam}>
              确认移动
            </Button>
          </Space>
        }
      >
        <Form form={moveForm} layout="vertical" className="pt-2" requiredMark={false}>
          <Form.Item
            name="teamId"
            label="团队ID"
            rules={[
              { required: true, message: '请输入团队ID' },
              { pattern: ID_PATTERN, message: 'ID 需为 1-19 位正整数' },
            ]}
          >
            <Input placeholder="要移动的团队ID" />
          </Form.Item>
          <Form.Item
            name="newParentTeamId"
            label="新上级团队ID（可选）"
            extra="留空表示移动为顶级团队"
            rules={[{ pattern: ID_PATTERN, message: 'ID 需为 1-19 位正整数' }]}
          >
            <Input placeholder="目标父团队ID" />
          </Form.Item>
          <Form.Item
            name="version"
            label="当前版本号"
            rules={[{ required: true, message: '请输入当前版本号' }]}
          >
            <InputNumber min={0} precision={0} className="w-full" placeholder="例如：0" />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title="添加成员"
        open={activeModal === 'addMember'}
        onClose={() => setActiveModal(null)}
        width={520}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setActiveModal(null)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={handleAddMember}>
              确认添加
            </Button>
          </Space>
        }
      >
        <Form form={memberForm} layout="vertical" className="pt-2" requiredMark={false}>
          <Form.Item
            name="teamId"
            label="团队ID"
            rules={[
              { required: true, message: '请输入团队ID' },
              { pattern: ID_PATTERN, message: 'ID 需为 1-19 位正整数' },
            ]}
          >
            <Input placeholder="目标团队ID" />
          </Form.Item>
          <Form.Item
            name="userId"
            label="用户ID"
            rules={[
              { required: true, message: '请输入用户ID' },
              { pattern: ID_PATTERN, message: 'ID 需为 1-19 位正整数' },
            ]}
          >
            <Input placeholder="可在「用户管理」页创建后复制" />
          </Form.Item>
          <Form.Item
            name="version"
            label="版本号"
            extra="乐观锁校验，重复提交幂等"
            rules={[{ required: true, message: '请输入版本号' }]}
          >
            <InputNumber min={0} precision={0} className="w-full" placeholder="例如：0" />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title="移除成员"
        open={activeModal === 'removeMember'}
        onClose={() => setActiveModal(null)}
        width={520}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setActiveModal(null)}>取消</Button>
            <Button type="primary" danger loading={submitting} onClick={handleRemoveMember}>
              确认移除
            </Button>
          </Space>
        }
      >
        <Form form={removeMemberForm} layout="vertical" className="pt-2" requiredMark={false}>
          <Form.Item
            name="teamId"
            label="团队ID"
            rules={[
              { required: true, message: '请输入团队ID' },
              { pattern: ID_PATTERN, message: 'ID 需为 1-19 位正整数' },
            ]}
          >
            <Input placeholder="目标团队ID" />
          </Form.Item>
          <Form.Item
            name="userId"
            label="用户ID"
            rules={[
              { required: true, message: '请输入用户ID' },
              { pattern: ID_PATTERN, message: 'ID 需为 1-19 位正整数' },
            ]}
          >
            <Input placeholder="要移除的用户ID" />
          </Form.Item>
          <Form.Item
            name="ifMatchVersion"
            label="成员版本号（If-Match-Version）"
            extra="以 If-Match-Version 请求头携带，同时吊销该成员的团队角色绑定"
            rules={[{ required: true, message: '请输入成员版本号' }]}
          >
            <InputNumber min={0} precision={0} className="w-full" placeholder="例如：0" />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title="创建团队角色"
        open={activeModal === 'createRole'}
        onClose={() => setActiveModal(null)}
        width={520}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setActiveModal(null)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={handleCreateTeamRole}>
              创建
            </Button>
          </Space>
        }
      >
        <Form form={teamRoleForm} layout="vertical" className="pt-2" requiredMark={false}>
          <Form.Item
            name="teamId"
            label="所属团队ID"
            rules={[
              { required: true, message: '请输入所属团队ID' },
              { pattern: ID_PATTERN, message: 'ID 需为 1-19 位正整数' },
            ]}
          >
            <Input placeholder="目标团队ID" />
          </Form.Item>
          <Form.Item
            name="roleCode"
            label="角色编码"
            extra="大写字母开头，仅含大写字母 / 数字 / 下划线，3-64 位"
            rules={[
              { required: true, message: '请输入角色编码' },
              { pattern: CODE_PATTERN, message: '格式：大写字母开头，3-64 位大写字母 / 数字 / 下划线' },
            ]}
          >
            <Input placeholder="例如：TEAM_DEPLOYER" maxLength={64} />
          </Form.Item>
          <Form.Item
            name="roleName"
            label="角色名称"
            rules={[{ required: true, message: '请输入角色名称' }]}
          >
            <Input placeholder="例如：发布操作员" maxLength={128} />
          </Form.Item>
          <Form.Item name="description" label="描述（可选）">
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title="绑定成员角色"
        open={activeModal === 'bindRole'}
        onClose={() => setActiveModal(null)}
        width={520}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setActiveModal(null)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={handleBindRole}>
              确认绑定
            </Button>
          </Space>
        }
      >
        <Form form={bindRoleForm} layout="vertical" className="pt-2" requiredMark={false}>
          <Form.Item
            name="teamId"
            label="团队ID"
            rules={[
              { required: true, message: '请输入团队ID' },
              { pattern: ID_PATTERN, message: 'ID 需为 1-19 位正整数' },
            ]}
          >
            <Input placeholder="成员所在团队ID" />
          </Form.Item>
          <Form.Item
            name="userId"
            label="用户ID"
            rules={[
              { required: true, message: '请输入用户ID' },
              { pattern: ID_PATTERN, message: 'ID 需为 1-19 位正整数' },
            ]}
          >
            <Input placeholder="团队成员用户ID" />
          </Form.Item>
          <Form.Item
            name="teamRoleId"
            label="团队角色ID"
            rules={[
              { required: true, message: '请输入团队角色ID' },
              { pattern: ID_PATTERN, message: 'ID 需为 1-19 位正整数' },
            ]}
          >
            <Input placeholder="在「团队角色」列表中复制" />
          </Form.Item>
          <Form.Item
            name="version"
            label="版本号"
            extra="乐观锁校验，需成员已在团队内"
            rules={[{ required: true, message: '请输入版本号' }]}
          >
            <InputNumber min={0} precision={0} className="w-full" placeholder="例如：0" />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title="团队角色权限集替换"
        open={activeModal === 'rolePerms'}
        onClose={() => setActiveModal(null)}
        width={560}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setActiveModal(null)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={handleRolePerms}>
              确认替换
            </Button>
          </Space>
        }
      >
        <Form form={rolePermsForm} layout="vertical" className="pt-2" requiredMark={false}>
          <Form.Item
            name="teamRoleId"
            label="团队角色ID"
            rules={[
              { required: true, message: '请输入团队角色ID' },
              { pattern: ID_PATTERN, message: 'ID 需为 1-19 位正整数' },
            ]}
          >
            <Input placeholder="目标团队角色ID" />
          </Form.Item>
          <Form.Item
            name="version"
            label="绑定版本号"
            rules={[{ required: true, message: '请输入绑定版本号' }]}
          >
            <InputNumber min={0} precision={0} className="w-full" placeholder="例如：0" />
          </Form.Item>
          <Form.Item
            name="permissionIds"
            label="权限ID列表"
            extra="输入权限ID后回车生成标签，整表替换该团队角色的权限集"
            rules={[
              {
                validator: (_, value: string[] | undefined) =>
                  !value || value.every((item) => ID_PATTERN.test(item.trim()))
                    ? Promise.resolve()
                    : Promise.reject(new Error('权限ID需为 1-19 位正整数')),
              },
            ]}
          >
            <Select mode="tags" open={false} tokenSeparators={[',', ' ']} placeholder="例如：2001，回车确认" />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}

/** 会话列表通用过滤：状态 + 关键字 + 创建时间范围 */
function filterByToolbar<T extends { status: string; createdAt: string }>(
  items: T[],
  keyword: string,
  statusFilter: string | undefined,
  range: [Dayjs | null, Dayjs | null] | null,
  textOf: (item: T) => string[],
): T[] {
  const text = keyword.trim()
  const start = range?.[0] ?? null
  const end = range?.[1] ?? null
  return items.filter((item) => {
    if (statusFilter && item.status !== statusFilter) return false
    if (text && !textOf(item).some((field) => field.includes(text))) return false
    if (start && dayjs(item.createdAt).isBefore(start.startOf('day'))) return false
    if (end && dayjs(item.createdAt).isAfter(end.endOf('day'))) return false
    return true
  })
}
