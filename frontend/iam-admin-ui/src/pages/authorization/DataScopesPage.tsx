import {
  ApartmentOutlined,
  DeleteOutlined,
  PartitionOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import {
  App,
  Button,
  Card,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Radio,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { useState } from 'react'
import { createDataScope, replaceRolePermissionDataScopes } from '@/api/authorization/dataScopes'
import { resolveErrorMessage } from '@/api/client/errors'
import type { DataScopeMergeMode, DataScopeType } from '@/types/authorization'
import { dayjs } from '@/utils/dayjs'

interface DataScopeRow {
  id: string
  scopeCode: string
  scopeName: string
  scopeType: DataScopeType
  version: number
  status: string
  createdAt: string
}

interface BindingRow {
  rolePermissionId: string
  bindingCount: number
  bindingVersion: number
  permissionVersion: number
  createdAt: string
}

interface CreateDataScopeFormValues {
  scopeCode: string
  scopeName: string
  scopeType: DataScopeType
  customPolicyId?: string
}

interface BindingFormValues {
  rolePermissionId: string
  bindingVersion: number
  bindings: {
    dataScopeId: string
    mergeMode?: DataScopeMergeMode
    teamIds?: string[]
    includeChildren?: boolean
  }[]
}

const SCOPE_CODE_PATTERN = /^[A-Z][A-Z0-9_]{2,63}$/
const ID_PATTERN = /^[1-9][0-9]{0,18}$/

const SCOPE_TYPE_OPTIONS: { value: DataScopeType; label: string }[] = [
  { value: 'ALL', label: 'ALL · 全部数据' },
  { value: 'SELF', label: 'SELF · 仅本人' },
  { value: 'TEAM', label: 'TEAM · 本团队' },
  { value: 'TEAM_AND_CHILDREN', label: 'TEAM_AND_CHILDREN · 本团队及子团队' },
  { value: 'SPECIFIED_TEAM', label: 'SPECIFIED_TEAM · 指定团队' },
  { value: 'SHARED', label: 'SHARED · 共享给我' },
  { value: 'CUSTOM_POLICY', label: 'CUSTOM_POLICY · 自定义策略' },
]

const SCOPE_TYPE_HINTS: Record<DataScopeType, string> = {
  ALL: '不限制行级范围，可访问租户内全部数据',
  SELF: '仅能访问本人创建的数据',
  TEAM: '仅能访问所属团队的数据',
  TEAM_AND_CHILDREN: '可访问所属团队及其子团队的数据',
  SPECIFIED_TEAM: '仅能访问指定团队（通过绑定中的 teamIds 指定）的数据',
  SHARED: '仅能访问他人主动共享给本人的数据',
  CUSTOM_POLICY: '访问范围由自定义策略（customPolicyId）决定',
}

const ID_RULE = { pattern: ID_PATTERN, message: 'ID 必须为正整数' }

export default function DataScopesPage() {
  const { message } = App.useApp()
  const [scopeRows, setScopeRows] = useState<DataScopeRow[]>([])
  const [bindingRows, setBindingRows] = useState<BindingRow[]>([])
  const [createOpen, setCreateOpen] = useState(false)
  const [bindOpen, setBindOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [createForm] = Form.useForm<CreateDataScopeFormValues>()
  const [bindForm] = Form.useForm<BindingFormValues>()
  const scopeType = Form.useWatch('scopeType', createForm) as DataScopeType | undefined

  const now = () => dayjs().format('YYYY-MM-DD HH:mm:ss')

  const handleCreate = async (values: CreateDataScopeFormValues) => {
    setSubmitting(true)
    try {
      const res = await createDataScope({
        scopeCode: values.scopeCode,
        scopeName: values.scopeName,
        scopeType: values.scopeType,
        customPolicyId: values.customPolicyId || null,
      })
      message.success(`数据范围创建成功（v${res.version}）`)
      setScopeRows((prev) => [
        {
          id: res.id,
          scopeCode: values.scopeCode,
          scopeName: values.scopeName,
          scopeType: values.scopeType,
          version: res.version,
          status: res.status,
          createdAt: now(),
        },
        ...prev,
      ])
      setCreateOpen(false)
      createForm.resetFields()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const handleBind = async (values: BindingFormValues) => {
    setSubmitting(true)
    try {
      const res = await replaceRolePermissionDataScopes(values.rolePermissionId, {
        bindingVersion: values.bindingVersion,
        bindings: (values.bindings ?? []).map((b) => ({
          dataScopeId: b.dataScopeId,
          mergeMode: b.mergeMode ?? 'UNION',
          teamIds: b.teamIds ?? [],
          includeChildren: b.includeChildren ?? false,
        })),
      })
      message.success(
        `绑定已替换：bindingVersion v${res.bindingVersion}，权限版本升级至 v${res.permissionVersion}`,
      )
      setBindingRows((prev) => [
        {
          rolePermissionId: values.rolePermissionId,
          bindingCount: (values.bindings ?? []).length,
          bindingVersion: res.bindingVersion,
          permissionVersion: res.permissionVersion,
          createdAt: now(),
        },
        ...prev,
      ])
      setBindOpen(false)
      bindForm.resetFields()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const scopeColumns: TableColumnsType<DataScopeRow> = [
    {
      title: 'ID',
      dataIndex: 'id',
      render: (v: string) => <Typography.Text copyable={{ text: v }}>{v}</Typography.Text>,
    },
    { title: '范围编码', dataIndex: 'scopeCode', render: (v: string) => <Tag color="blue">{v}</Tag> },
    { title: '名称', dataIndex: 'scopeName' },
    { title: '类型', dataIndex: 'scopeType' },
    { title: '版本', dataIndex: 'version', render: (v: number) => `v${v}` },
    { title: '状态', dataIndex: 'status', render: (v: string) => <Tag color="green">{v}</Tag> },
    { title: '创建时间', dataIndex: 'createdAt' },
  ]

  const bindingColumns: TableColumnsType<BindingRow> = [
    {
      title: '角色权限 ID',
      dataIndex: 'rolePermissionId',
      render: (v: string) => <Typography.Text copyable={{ text: v }}>{v}</Typography.Text>,
    },
    { title: '绑定数', dataIndex: 'bindingCount' },
    { title: 'bindingVersion', dataIndex: 'bindingVersion', render: (v: number) => `v${v}` },
    { title: 'permissionVersion', dataIndex: 'permissionVersion', render: (v: number) => `v${v}` },
    { title: '操作时间', dataIndex: 'createdAt' },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl bg-white px-6 py-5 shadow-sm">
        <div className="flex items-center gap-4">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-indigo-500 text-white">
            <PartitionOutlined className="text-xl" />
          </span>
          <div>
            <h1 className="text-xl font-semibold text-slate-800">数据范围</h1>
            <p className="mt-0.5 text-sm text-slate-400">
              创建行级数据范围，并整体替换角色权限上的范围绑定
            </p>
          </div>
        </div>
        <Space>
          <Button icon={<ApartmentOutlined />} onClick={() => setBindOpen(true)}>
            数据范围绑定替换
          </Button>
          <Button
            type="primary"
            className="font-medium"
            icon={<PlusOutlined />}
            onClick={() => setCreateOpen(true)}
          >
            创建数据范围
          </Button>
        </Space>
      </div>

      <Card title="本次会话创建的数据范围" className="rounded-2xl">
        <Table
          columns={scopeColumns}
          dataSource={scopeRows}
          rowKey="id"
          pagination={{
            defaultPageSize: 10,
            pageSizeOptions: ['10', '20', '50', '100'],
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条记录`,
          }}
          scroll={{ x: true }}
          locale={{
            emptyText: (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未创建数据范围">
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => setCreateOpen(true)}
                >
                  创建数据范围
                </Button>
              </Empty>
            ),
          }}
        />
      </Card>

      <Card title="本次会话的绑定替换记录" className="rounded-2xl">
        <Table
          columns={bindingColumns}
          dataSource={bindingRows}
          rowKey={(record) => `${record.rolePermissionId}-${record.createdAt}`}
          pagination={{
            defaultPageSize: 10,
            pageSizeOptions: ['10', '20', '50', '100'],
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条记录`,
          }}
          scroll={{ x: true }}
          locale={{
            emptyText: (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未执行绑定替换">
                <Button icon={<ApartmentOutlined />} onClick={() => setBindOpen(true)}>
                  数据范围绑定替换
                </Button>
              </Empty>
            ),
          }}
        />
      </Card>

      <Drawer
        title="创建数据范围"
        open={createOpen}
        onClose={() => {
          setCreateOpen(false)
          createForm.resetFields()
        }}
        destroyOnClose
        width={560}
        extra={
          <Space>
            <Button
              onClick={() => {
                setCreateOpen(false)
                createForm.resetFields()
              }}
            >
              取消
            </Button>
            <Button type="primary" loading={submitting} onClick={() => createForm.submit()}>
              创建
            </Button>
          </Space>
        }
      >
        <Form
          form={createForm}
          layout="vertical"
          className="pt-2"
          initialValues={{ scopeType: 'ALL' }}
          onFinish={handleCreate}
        >
          <Form.Item
            name="scopeCode"
            label="范围编码"
            rules={[
              { required: true, message: '请输入范围编码' },
              {
                pattern: SCOPE_CODE_PATTERN,
                message: '3-64 位，大写字母开头，仅含大写字母、数字与下划线',
              },
            ]}
          >
            <Input placeholder="例如：TEAM_DOC_READ" maxLength={64} />
          </Form.Item>
          <Form.Item
            name="scopeName"
            label="范围名称"
            rules={[{ required: true, message: '请输入范围名称' }]}
          >
            <Input placeholder="例如：团队文档只读范围" maxLength={128} />
          </Form.Item>
          <Form.Item
            name="scopeType"
            label="范围类型"
            extra={scopeType ? SCOPE_TYPE_HINTS[scopeType] : undefined}
          >
            <Select options={SCOPE_TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item
            name="customPolicyId"
            label="自定义策略 ID"
            hidden={scopeType !== 'CUSTOM_POLICY'}
            rules={
              scopeType === 'CUSTOM_POLICY'
                ? [{ required: true, message: '请输入自定义策略 ID' }, ID_RULE]
                : []
            }
          >
            <Input placeholder="自定义策略 ID" />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title="数据范围绑定替换"
        open={bindOpen}
        onClose={() => {
          setBindOpen(false)
          bindForm.resetFields()
        }}
        destroyOnClose
        width={640}
        extra={
          <Space>
            <Button
              onClick={() => {
                setBindOpen(false)
                bindForm.resetFields()
              }}
            >
              取消
            </Button>
            <Button type="primary" loading={submitting} onClick={() => bindForm.submit()}>
              替换绑定
            </Button>
          </Space>
        }
      >
        <Form
          form={bindForm}
          layout="vertical"
          className="pt-2"
          initialValues={{ bindings: [{}] }}
          onFinish={handleBind}
        >
          <Form.Item
            name="rolePermissionId"
            label="角色权限 ID"
            rules={[{ required: true, message: '请输入角色权限 ID' }, ID_RULE]}
          >
            <Input placeholder="将被整体替换绑定的角色权限 ID" />
          </Form.Item>
          <Form.Item
            name="bindingVersion"
            label="当前绑定版本（bindingVersion）"
            rules={[{ required: true, message: '请输入当前绑定版本' }]}
          >
            <InputNumber min={0} precision={0} style={{ width: '100%' }} placeholder="0" />
          </Form.Item>
          <div className="mb-2 text-sm font-medium text-slate-600">绑定列表（整体替换）</div>
          <Form.List
            name="bindings"
            rules={[
              {
                validator: async (_, value) => {
                  if (!value || value.length === 0) {
                    return Promise.reject(new Error('请至少配置一条绑定'))
                  }
                },
              },
            ]}
          >
            {(fields, { add, remove }, { errors }) => (
              <>
                <div className="space-y-3">
                  {fields.map((field) => (
                    <div
                      key={field.key}
                      className="rounded-xl border border-slate-100 bg-slate-50/60 p-3"
                    >
                      <div className="flex items-start gap-2">
                        <Form.Item
                          name={[field.name, 'dataScopeId']}
                          rules={[{ required: true, message: '请输入数据范围 ID' }, ID_RULE]}
                          style={{ marginBottom: 0 }}
                          className="flex-1"
                        >
                          <Input placeholder="数据范围 ID" />
                        </Form.Item>
                        <Button
                          type="text"
                          danger
                          icon={<DeleteOutlined />}
                          onClick={() => remove(field.name)}
                          aria-label="移除绑定"
                        />
                      </div>
                      <div className="mt-3 flex flex-wrap items-center gap-x-6 gap-y-2">
                        <Form.Item
                          name={[field.name, 'mergeMode']}
                          label="合并模式"
                          style={{ marginBottom: 0 }}
                        >
                          <Radio.Group
                            options={[
                              { label: 'UNION · 并集', value: 'UNION' },
                              { label: 'INTERSECT · 交集', value: 'INTERSECT' },
                            ]}
                          />
                        </Form.Item>
                        <Form.Item
                          name={[field.name, 'includeChildren']}
                          label="含子团队"
                          valuePropName="checked"
                          style={{ marginBottom: 0 }}
                        >
                          <Switch />
                        </Form.Item>
                      </div>
                      <Form.Item
                        name={[field.name, 'teamIds']}
                        style={{ marginBottom: 0 }}
                        className="mt-3"
                      >
                        <Select
                          mode="tags"
                          open={false}
                          tokenSeparators={[',']}
                          placeholder="限定团队 ID（输入后回车，可多个）"
                        />
                      </Form.Item>
                    </div>
                  ))}
                </div>
                <Button
                  type="dashed"
                  block
                  className="mt-3"
                  icon={<PlusOutlined />}
                  onClick={() =>
                    add({ dataScopeId: '', mergeMode: 'UNION', teamIds: [], includeChildren: false })
                  }
                >
                  添加绑定
                </Button>
                <Form.ErrorList errors={errors} />
              </>
            )}
          </Form.List>
        </Form>
      </Drawer>
    </div>
  )
}
