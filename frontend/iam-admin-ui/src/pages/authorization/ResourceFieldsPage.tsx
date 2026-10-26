import { DeleteOutlined, PlusOutlined, ProfileOutlined } from '@ant-design/icons'
import {
  App,
  Button,
  Card,
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
import { replaceResourceFields } from '@/api/authorization/resourceFields'
import { resolveErrorMessage } from '@/api/client/errors'
import type { FieldDataType, SensitiveLevel } from '@/types/authorization'
import { dayjs } from '@/utils/dayjs'

interface FieldSetRow {
  resourceId: string
  replaceId: string
  fieldCount: number
  version: number
  status: string
  createdAt: string
}

interface FieldRowFormValues {
  fieldCode: string
  propertyPath: string
  columnName?: string
  dataType?: FieldDataType
  sensitiveLevel?: SensitiveLevel
  systemManaged?: boolean
}

interface ReplaceFieldsFormValues {
  resourceId: string
  resourceVersion: number
  fields: FieldRowFormValues[]
}

const ID_PATTERN = /^[1-9][0-9]{0,18}$/
const ID_RULE = { pattern: ID_PATTERN, message: 'ID 必须为正整数' }
const FIELD_CODE_PATTERN = /^[A-Za-z][A-Za-z0-9_]{0,63}$/
const PROPERTY_PATH_PATTERN = /^[A-Za-z][A-Za-z0-9_]*(\.([A-Za-z][A-Za-z0-9_]*|\*)){0,7}$/
const COLUMN_NAME_PATTERN = /^[A-Za-z][A-Za-z0-9_]{0,63}$/

const DATA_TYPE_OPTIONS: { value: FieldDataType; label: string }[] = [
  { value: 'STRING', label: 'STRING · 字符串' },
  { value: 'NUMBER', label: 'NUMBER · 数值' },
  { value: 'BOOLEAN', label: 'BOOLEAN · 布尔' },
  { value: 'DATE', label: 'DATE · 日期' },
  { value: 'DATETIME', label: 'DATETIME · 日期时间' },
  { value: 'OBJECT', label: 'OBJECT · 对象' },
  { value: 'ARRAY', label: 'ARRAY · 数组' },
  { value: 'MAP_FIELD', label: 'MAP_FIELD · 动态扩展映射字段' },
]

const SENSITIVE_LEVEL_OPTIONS: { value: SensitiveLevel; label: string }[] = [
  { value: 'PUBLIC', label: 'PUBLIC · 公开' },
  { value: 'INTERNAL', label: 'INTERNAL · 内部' },
  { value: 'CONFIDENTIAL', label: 'CONFIDENTIAL · 机密' },
  { value: 'RESTRICTED', label: 'RESTRICTED · 受限' },
]

export default function ResourceFieldsPage() {
  const { message } = App.useApp()
  const [rows, setRows] = useState<FieldSetRow[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<ReplaceFieldsFormValues>()

  const handleReplace = async (values: ReplaceFieldsFormValues) => {
    setSubmitting(true)
    try {
      const res = await replaceResourceFields(values.resourceId, {
        resourceVersion: values.resourceVersion,
        fields: (values.fields ?? []).map((f) => ({
          fieldCode: f.fieldCode,
          propertyPath: f.propertyPath,
          columnName: f.columnName || null,
          dataType: f.dataType ?? 'STRING',
          sensitiveLevel: f.sensitiveLevel ?? 'INTERNAL',
          systemManaged: f.systemManaged ?? false,
        })),
      })
      message.success(`字段元数据已替换（v${res.version}）`)
      setRows((prev) => [
        {
          resourceId: values.resourceId,
          replaceId: res.id,
          fieldCount: (values.fields ?? []).length,
          version: res.version,
          status: res.status,
          createdAt: dayjs().format('YYYY-MM-DD HH:mm:ss'),
        },
        ...prev,
      ])
      form.resetFields()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const columns: TableColumnsType<FieldSetRow> = [
    {
      title: '资源 ID',
      dataIndex: 'resourceId',
      render: (v: string) => <Typography.Text copyable={{ text: v }}>{v}</Typography.Text>,
    },
    { title: '字段数', dataIndex: 'fieldCount' },
    {
      title: '替换结果 ID',
      dataIndex: 'replaceId',
      render: (v: string) => <Typography.Text copyable={{ text: v }}>{v}</Typography.Text>,
    },
    { title: '版本', dataIndex: 'version', render: (v: number) => `v${v}` },
    { title: '状态', dataIndex: 'status', render: (v: string) => <Tag color="green">{v}</Tag> },
    { title: '操作时间', dataIndex: 'createdAt' },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl bg-white px-6 py-5 shadow-sm">
        <div className="flex items-center gap-4">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-emerald-500 to-teal-500 text-white">
            <ProfileOutlined className="text-xl" />
          </span>
          <div>
            <h1 className="text-xl font-semibold text-slate-800">资源字段元数据</h1>
            <p className="mt-0.5 text-sm text-slate-400">
              按资源整体替换受保护字段的编码、路径、类型与敏感级别
            </p>
          </div>
        </div>
      </div>

      <Card title="替换字段元数据（整体替换）" className="rounded-2xl">
        <Form
          form={form}
          layout="vertical"
          onFinish={handleReplace}
          initialValues={{ fields: [{}] }}
        >
          <div className="grid grid-cols-1 gap-x-4 sm:grid-cols-2">
            <Form.Item
              name="resourceId"
              label="资源 ID"
              rules={[{ required: true, message: '请输入资源 ID' }, ID_RULE]}
            >
              <Input placeholder="将被整体替换字段元数据的资源 ID" />
            </Form.Item>
            <Form.Item
              name="resourceVersion"
              label="当前资源版本（resourceVersion）"
              rules={[{ required: true, message: '请输入当前资源版本' }]}
            >
              <InputNumber min={0} precision={0} style={{ width: '100%' }} placeholder="0" />
            </Form.Item>
          </div>
          <div className="mb-2 text-sm font-medium text-slate-600">字段列表（最多 1000 行）</div>
          <Form.List name="fields">
            {(fields, { add, remove }) => (
              <>
                <div className="space-y-3">
                  {fields.map((field) => (
                    <div
                      key={field.key}
                      className="rounded-xl border border-slate-100 bg-slate-50/60 p-3"
                    >
                      <div className="flex items-start gap-2">
                        <Form.Item
                          name={[field.name, 'fieldCode']}
                          rules={[
                            { required: true, message: '请输入字段编码' },
                            { pattern: FIELD_CODE_PATTERN, message: '字母开头，仅含字母、数字与下划线' },
                          ]}
                          style={{ marginBottom: 0 }}
                          className="flex-1"
                        >
                          <Input placeholder="字段编码，如 phone" />
                        </Form.Item>
                        <Form.Item
                          name={[field.name, 'propertyPath']}
                          rules={[
                            { required: true, message: '请输入属性路径' },
                            {
                              pattern: PROPERTY_PATH_PATTERN,
                              message: '字母开头、点号分隔的路径，最多 8 段',
                            },
                          ]}
                          style={{ marginBottom: 0 }}
                          className="flex-1"
                        >
                          <Input placeholder="属性路径，如 contact.phone" />
                        </Form.Item>
                        <Form.Item
                          name={[field.name, 'columnName']}
                          rules={[{ pattern: COLUMN_NAME_PATTERN, message: '字母开头，仅含字母、数字与下划线' }]}
                          style={{ marginBottom: 0 }}
                          className="flex-1"
                        >
                          <Input placeholder="列名（可选）" />
                        </Form.Item>
                        <Button
                          type="text"
                          danger
                          icon={<DeleteOutlined />}
                          onClick={() => remove(field.name)}
                          aria-label="移除字段"
                        />
                      </div>
                      <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2">
                        <Form.Item
                          name={[field.name, 'dataType']}
                          initialValue="STRING"
                          style={{ marginBottom: 0 }}
                          className="w-56"
                        >
                          <Select options={DATA_TYPE_OPTIONS} />
                        </Form.Item>
                        <Form.Item
                          name={[field.name, 'sensitiveLevel']}
                          initialValue="INTERNAL"
                          style={{ marginBottom: 0 }}
                          className="w-56"
                        >
                          <Select options={SENSITIVE_LEVEL_OPTIONS} />
                        </Form.Item>
                        <Form.Item
                          name={[field.name, 'systemManaged']}
                          label="系统管理字段"
                          valuePropName="checked"
                          initialValue={false}
                          style={{ marginBottom: 0 }}
                        >
                          <Switch />
                        </Form.Item>
                      </div>
                    </div>
                  ))}
                </div>
                <Button
                  type="dashed"
                  block
                  className="mt-3"
                  icon={<PlusOutlined />}
                  onClick={() => add()}
                >
                  添加字段
                </Button>
              </>
            )}
          </Form.List>
          <Form.Item className="mb-0 mt-5">
            <Space>
              <Button type="primary" htmlType="submit" className="font-medium" loading={submitting}>
                替换字段元数据
              </Button>
              <Button onClick={() => form.resetFields()}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card title="本次会话替换的字段集" className="rounded-2xl">
        <Table
          columns={columns}
          dataSource={rows}
          rowKey={(record) => `${record.replaceId}-${record.createdAt}`}
          pagination={{
            defaultPageSize: 10,
            pageSizeOptions: ['10', '20', '50', '100'],
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条记录`,
          }}
          scroll={{ x: true }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="尚未替换字段元数据，请在上方表单完成替换"
              />
            ),
          }}
        />
      </Card>
    </div>
  )
}
