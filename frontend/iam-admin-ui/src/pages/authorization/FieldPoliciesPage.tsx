import {
  EyeInvisibleOutlined,
  PlusOutlined,
  TagsOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Card,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { useState } from 'react'
import { createFieldPolicy, replaceRolePermissionFieldPolicies } from '@/api/authorization/fieldPolicies'
import { resolveErrorMessage } from '@/api/client/errors'
import { dayjs } from '@/utils/dayjs'

interface FieldPolicyRow {
  id: string
  resourceId: string
  operationId: string
  fieldId: string
  readable: boolean
  writable: boolean
  hidden: boolean
  maskStrategyId: string | null
  priority: number
  version: number
  status: string
  createdAt: string
}

interface BindingRow {
  rolePermissionId: string
  policyCount: number
  bindingVersion: number
  permissionVersion: number
  createdAt: string
}

interface CreateFieldPolicyFormValues {
  resourceId: string
  operationId: string
  fieldId: string
  readable: boolean
  writable: boolean
  hidden: boolean
  maskStrategyId?: string
  priority: number
}

interface BindingFormValues {
  rolePermissionId: string
  bindingVersion: number
  fieldPolicyIds: string[]
}

const ID_PATTERN = /^[1-9][0-9]{0,18}$/
const ID_RULE = { pattern: ID_PATTERN, message: 'ID 必须为正整数' }

function BoolTag({ value, trueLabel, falseLabel }: { value: boolean; trueLabel: string; falseLabel: string }) {
  return <Tag color={value ? 'green' : 'default'}>{value ? trueLabel : falseLabel}</Tag>
}

export default function FieldPoliciesPage() {
  const { message } = App.useApp()
  const [policyRows, setPolicyRows] = useState<FieldPolicyRow[]>([])
  const [bindingRows, setBindingRows] = useState<BindingRow[]>([])
  const [createOpen, setCreateOpen] = useState(false)
  const [bindOpen, setBindOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [createForm] = Form.useForm<CreateFieldPolicyFormValues>()
  const [bindForm] = Form.useForm<BindingFormValues>()

  const readable = Form.useWatch('readable', createForm) as boolean | undefined
  const writable = Form.useWatch('writable', createForm) as boolean | undefined
  const hidden = Form.useWatch('hidden', createForm) as boolean | undefined
  // 领域不变式：hidden=true 必须同时关闭 readable 与 writable
  const hiddenInvariantViolated = !!hidden && (!!readable || !!writable)

  const now = () => dayjs().format('YYYY-MM-DD HH:mm:ss')

  const handleCreate = async (values: CreateFieldPolicyFormValues) => {
    if (values.hidden && (values.readable || values.writable)) {
      message.error('不变式约束：hidden=true 时必须同时关闭 readable 与 writable')
      return
    }
    setSubmitting(true)
    try {
      const res = await createFieldPolicy({
        resourceId: values.resourceId,
        operationId: values.operationId,
        fieldId: values.fieldId,
        readable: values.readable,
        writable: values.writable,
        hidden: values.hidden,
        maskStrategyId: values.maskStrategyId || null,
        priority: values.priority,
      })
      message.success(`字段策略创建成功（v${res.version}）`)
      setPolicyRows((prev) => [
        {
          id: res.id,
          resourceId: values.resourceId,
          operationId: values.operationId,
          fieldId: values.fieldId,
          readable: values.readable,
          writable: values.writable,
          hidden: values.hidden,
          maskStrategyId: values.maskStrategyId || null,
          priority: values.priority,
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
      const res = await replaceRolePermissionFieldPolicies(values.rolePermissionId, {
        bindingVersion: values.bindingVersion,
        fieldPolicyIds: values.fieldPolicyIds ?? [],
      })
      message.success(
        `绑定已替换：bindingVersion v${res.bindingVersion}，权限版本升级至 v${res.permissionVersion}`,
      )
      setBindingRows((prev) => [
        {
          rolePermissionId: values.rolePermissionId,
          policyCount: (values.fieldPolicyIds ?? []).length,
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

  const policyColumns: TableColumnsType<FieldPolicyRow> = [
    {
      title: '策略 ID',
      dataIndex: 'id',
      render: (v: string) => <Typography.Text copyable={{ text: v }}>{v}</Typography.Text>,
    },
    {
      title: '资源 / 操作 / 字段',
      dataIndex: 'resourceId',
      render: (_: string, record) => (
        <span className="text-sm text-slate-600">
          {record.resourceId} / {record.operationId} / {record.fieldId}
        </span>
      ),
    },
    {
      title: '可读',
      dataIndex: 'readable',
      render: (v: boolean) => <BoolTag value={v} trueLabel="可读" falseLabel="不可读" />,
    },
    {
      title: '可写',
      dataIndex: 'writable',
      render: (v: boolean) => <BoolTag value={v} trueLabel="可写" falseLabel="不可写" />,
    },
    {
      title: '隐藏',
      dataIndex: 'hidden',
      render: (v: boolean) => <BoolTag value={v} trueLabel="隐藏" falseLabel="可见" />,
    },
    {
      title: '脱敏策略',
      dataIndex: 'maskStrategyId',
      render: (v: string | null) => v ?? <span className="text-slate-300">—</span>,
    },
    { title: '优先级', dataIndex: 'priority' },
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
    { title: '策略数', dataIndex: 'policyCount' },
    { title: 'bindingVersion', dataIndex: 'bindingVersion', render: (v: number) => `v${v}` },
    { title: 'permissionVersion', dataIndex: 'permissionVersion', render: (v: number) => `v${v}` },
    { title: '操作时间', dataIndex: 'createdAt' },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl bg-white px-6 py-5 shadow-sm">
        <div className="flex items-center gap-4">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-fuchsia-500 text-white">
            <EyeInvisibleOutlined className="text-xl" />
          </span>
          <div>
            <h1 className="text-xl font-semibold text-slate-800">字段策略</h1>
            <p className="mt-0.5 text-sm text-slate-400">
              配置字段的读、写、隐藏与脱敏，并整体替换角色权限上的策略绑定
            </p>
          </div>
        </div>
        <Space>
          <Button icon={<TagsOutlined />} onClick={() => setBindOpen(true)}>
            字段策略绑定替换
          </Button>
          <Button
            type="primary"
            className="font-medium"
            icon={<PlusOutlined />}
            onClick={() => setCreateOpen(true)}
          >
            创建字段策略
          </Button>
        </Space>
      </div>

      <Card title="本次会话创建的字段策略" className="rounded-2xl">
        <Table
          columns={policyColumns}
          dataSource={policyRows}
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
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未创建字段策略">
                <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
                  创建字段策略
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
                <Button icon={<TagsOutlined />} onClick={() => setBindOpen(true)}>
                  字段策略绑定替换
                </Button>
              </Empty>
            ),
          }}
        />
      </Card>

      <Drawer
        title="创建字段策略"
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
          initialValues={{ readable: true, writable: false, hidden: false, priority: 0 }}
          onFinish={handleCreate}
        >
          <div className="grid grid-cols-1 gap-x-4 sm:grid-cols-3">
            <Form.Item
              name="resourceId"
              label="资源 ID"
              rules={[{ required: true, message: '请输入资源 ID' }, ID_RULE]}
            >
              <Input placeholder="资源 ID" />
            </Form.Item>
            <Form.Item
              name="operationId"
              label="操作 ID"
              rules={[{ required: true, message: '请输入操作 ID' }, ID_RULE]}
            >
              <Input placeholder="操作 ID" />
            </Form.Item>
            <Form.Item
              name="fieldId"
              label="字段 ID"
              rules={[{ required: true, message: '请输入字段 ID' }, ID_RULE]}
            >
              <Input placeholder="字段 ID" />
            </Form.Item>
          </div>
          <div className="grid grid-cols-3 gap-3 rounded-xl border border-slate-100 bg-slate-50/60 p-4">
            <Form.Item
              name="readable"
              label="可读"
              valuePropName="checked"
              style={{ marginBottom: 0 }}
            >
              <Switch checkedChildren="开" unCheckedChildren="关" />
            </Form.Item>
            <Form.Item
              name="writable"
              label="可写"
              valuePropName="checked"
              style={{ marginBottom: 0 }}
            >
              <Switch checkedChildren="开" unCheckedChildren="关" />
            </Form.Item>
            <Form.Item
              name="hidden"
              label="隐藏"
              valuePropName="checked"
              style={{ marginBottom: 0 }}
            >
              <Switch checkedChildren="开" unCheckedChildren="关" />
            </Form.Item>
          </div>
          {hiddenInvariantViolated && (
            <Alert
              type="warning"
              showIcon
              className="mt-3"
              message="不变式约束：hidden=true 时必须同时关闭 readable 与 writable"
            />
          )}
          <div className="mt-4 grid grid-cols-1 gap-x-4 sm:grid-cols-2">
            <Form.Item name="maskStrategyId" label="脱敏策略 ID（可选）" rules={[ID_RULE]}>
              <Input placeholder="留空表示不脱敏" />
            </Form.Item>
            <Form.Item
              name="priority"
              label="优先级（-10000 ~ 10000）"
              rules={[{ required: true, message: '请输入优先级' }]}
            >
              <InputNumber min={-10000} max={10000} precision={0} style={{ width: '100%' }} />
            </Form.Item>
          </div>
        </Form>
      </Drawer>

      <Drawer
        title="字段策略绑定替换"
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
          <Form.Item
            name="fieldPolicyIds"
            label="字段策略 ID 列表（整体替换）"
            rules={[{ required: true, message: '请至少输入一个字段策略 ID' }]}
          >
            <Select
              mode="tags"
              open={false}
              tokenSeparators={[',']}
              placeholder="输入字段策略 ID 后回车，可多个"
            />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
