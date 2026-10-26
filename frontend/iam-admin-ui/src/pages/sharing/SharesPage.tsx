import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  ShareAltOutlined,
  StopOutlined,
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
  Radio,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { useMemo, useState } from 'react'
import type { Dayjs } from 'dayjs'
import { createShare, getShare, reshare, revokeShare } from '@/api/sharing/shares'
import { usePageSchema } from '@/hooks/useServerMetadata'
import { resolveErrorMessage } from '@/api/client/errors'
import type {
  CreateShareRequest,
  ShareScopeType,
  ShareStatus,
  ShareTargetType,
} from '@/types/sharing'
import { dayjs } from '@/utils/dayjs'

interface ShareRow {
  shareId: string
  status: ShareStatus
  version: number
  projectionEpoch: number
  scopeType?: ShareScopeType
  source: '创建' | '续分享' | '查询'
  recordedAt: string
}

interface ShareFormValues {
  resourceId: string
  resourceInstanceKey: string
  scopeType?: ShareScopeType
  folderPath?: string
  collectionResourceIds?: string[]
  target: {
    type: ShareTargetType
    id: string
  }
  expireAt: Dayjs
  operationIds: string[]
  canReshare: boolean
}

interface ReshareFormValues extends ShareFormValues {
  parentVersion: number
}

interface RevokeFormValues {
  version: number
  reason: string
}

const ID_PATTERN = /^[1-9][0-9]{0,18}$/
const ID_RULE = { pattern: ID_PATTERN, message: 'ID 必须为正整数' }

const STATUS_META: Record<ShareStatus, { color: string; label: string }> = {
  WAITING: { color: 'gold', label: '待生效' },
  ACTIVE: { color: 'green', label: '生效中' },
  REVOKED: { color: 'red', label: '已撤销' },
  EXPIRED: { color: 'default', label: '已过期' },
}

function ShareStatusTag({ status }: { status: ShareStatus }) {
  const meta = STATUS_META[status]
  return (
    <Tag color={meta.color}>
      {status} · {meta.label}
    </Tag>
  )
}

function ShareFormFields() {
  return (
    <>
      <div className="grid grid-cols-1 gap-x-4 sm:grid-cols-2">
        <Form.Item
          name="resourceId"
          label="资源 ID"
          rules={[{ required: true, message: '请输入资源 ID' }, ID_RULE]}
        >
          <Input placeholder="被分享的资源 ID" />
        </Form.Item>
        <Form.Item
          name="resourceInstanceKey"
          label="资源实例 Key"
          rules={[
            { required: true, message: '请输入资源实例 Key' },
            { max: 128, message: '最长 128 字符' },
          ]}
        >
          <Input placeholder="例如：order:20260923-001" />
        </Form.Item>
      </div>
      <Form.Item name="scopeType" label="分享作用域模式" initialValue="INSTANCE">
        <Radio.Group
          options={[
            { label: '单实例 (INSTANCE)', value: 'INSTANCE' },
            { label: '文件夹继承 ACL (FOLDER_INHERITED)', value: 'FOLDER_INHERITED' },
            { label: '资源集合打包分享 (COLLECTION)', value: 'COLLECTION' },
          ]}
        />
      </Form.Item>
      <div className="grid grid-cols-1 gap-x-4 sm:grid-cols-2">
        <Form.Item name="folderPath" label="文件夹继承路径前缀（FOLDER_INHERITED 可选）">
          <Input placeholder="例如：/projects/alpha/docs/" />
        </Form.Item>
        <Form.Item name="collectionResourceIds" label="资源集合实例列表（COLLECTION 可选）">
          <Select
            mode="tags"
            open={false}
            tokenSeparators={[',']}
            placeholder="输入集合内资源实例 Key 后回车"
          />
        </Form.Item>
      </div>
      <Form.Item name={['target', 'type']} label="分享对象类型">
        <Radio.Group
          options={[
            { label: '用户（USER）', value: 'USER' },
            { label: '团队（TEAM）', value: 'TEAM' },
            { label: '角色（ROLE）', value: 'ROLE' },
            { label: '团队角色（TEAM_ROLE）', value: 'TEAM_ROLE' },
          ]}
        />
      </Form.Item>
      <Form.Item
        name={['target', 'id']}
        label="分享对象 ID"
        rules={[{ required: true, message: '请输入分享对象 ID' }, ID_RULE]}
      >
        <Input placeholder="目标用户、团队、角色或团队角色 ID" />
      </Form.Item>
      <Form.Item
        name="expireAt"
        label="过期时间"
        rules={[{ required: true, message: '请选择过期时间' }]}
      >
        <DatePicker showTime className="!w-full" placeholder="选择过期时间" />
      </Form.Item>
      <Form.Item
        name="operationIds"
        label="授权操作 ID"
        rules={[{ required: true, message: '请至少输入一个操作 ID' }]}
      >
        <Select
          mode="tags"
          open={false}
          tokenSeparators={[',']}
          placeholder="输入操作 ID 后回车，可多个"
        />
      </Form.Item>
      <Form.Item name="canReshare" label="允许接收方继续分享" valuePropName="checked">
        <Switch checkedChildren="允许" unCheckedChildren="禁止" />
      </Form.Item>
    </>
  )
}

function toCreateRequest(values: ShareFormValues): CreateShareRequest {
  return {
    resourceId: values.resourceId,
    resourceInstanceKey: values.resourceInstanceKey,
    scopeType: values.scopeType ?? 'INSTANCE',
    folderPath: values.folderPath?.trim() || null,
    collectionResourceIds: values.collectionResourceIds ?? [],
    target: { type: values.target.type, id: values.target.id },
    operationIds: values.operationIds,
    fields: [],
    expireAt: values.expireAt.toISOString(),
    canReshare: values.canReshare,
  }
}

export default function SharesPage() {
  const { hasOperation } = usePageSchema('shares')
  const { message } = App.useApp()
  const [rows, setRows] = useState<ShareRow[]>([])
  const [queryForm] = Form.useForm<{ shareId: string }>()
  const [createForm] = Form.useForm<ShareFormValues>()
  const [reshareForm] = Form.useForm<ReshareFormValues>()
  const [revokeForm] = Form.useForm<RevokeFormValues>()

  const [queryLoading, setQueryLoading] = useState(false)
  const [queryResult, setQueryResult] = useState<ShareRow | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [reshareTarget, setReshareTarget] = useState<ShareRow | null>(null)
  const [revokeTarget, setRevokeTarget] = useState<ShareRow | null>(null)
  const [draftStatus, setDraftStatus] = useState<ShareStatus | 'ALL'>('ALL')
  const [draftTimeRange, setDraftTimeRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)
  const [appliedStatus, setAppliedStatus] = useState<ShareStatus | 'ALL'>('ALL')
  const [appliedTimeRange, setAppliedTimeRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)

  const now = () => dayjs().format('YYYY-MM-DD HH:mm:ss')

  const upsertRow = (row: ShareRow) => {
    setRows((prev) => {
      const rest = prev.filter((item) => item.shareId !== row.shareId)
      return [row, ...rest]
    })
  }

  const handleFilterSearch = () => {
    setAppliedStatus(draftStatus)
    setAppliedTimeRange(draftTimeRange)
  }

  const handleFilterReset = () => {
    setDraftStatus('ALL')
    setDraftTimeRange(null)
    setAppliedStatus('ALL')
    setAppliedTimeRange(null)
  }

  const handleQuery = async (values: { shareId: string }) => {
    setQueryLoading(true)
    try {
      const res = await getShare(values.shareId.trim())
      const row: ShareRow = {
        shareId: res.shareId,
        status: res.status,
        version: res.version,
        projectionEpoch: res.projectionEpoch,
        scopeType: res.scopeType,
        source: '查询',
        recordedAt: now(),
      }
      setQueryResult(row)
      upsertRow(row)
    } catch (error) {
      setQueryResult(null)
      message.error(resolveErrorMessage(error))
    } finally {
      setQueryLoading(false)
    }
  }

  const handleCreate = async (values: ShareFormValues) => {
    setSubmitting(true)
    try {
      const res = await createShare(toCreateRequest(values))
      message.success(`分享创建成功，当前状态 ${res.status}`)
      upsertRow({
        shareId: res.shareId,
        status: res.status,
        version: res.version,
        projectionEpoch: res.projectionEpoch,
        scopeType: values.scopeType ?? 'INSTANCE',
        source: '创建',
        recordedAt: now(),
      })
      setCreateOpen(false)
      createForm.resetFields()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const handleReshare = async (values: ReshareFormValues) => {
    if (!reshareTarget) return
    setSubmitting(true)
    try {
      const res = await reshare(reshareTarget.shareId, {
        ...toCreateRequest(values),
        parentVersion: values.parentVersion,
      })
      message.success(`续分享创建成功，当前状态 ${res.status}`)
      upsertRow({
        shareId: res.shareId,
        status: res.status,
        version: res.version,
        projectionEpoch: res.projectionEpoch,
        scopeType: values.scopeType ?? 'INSTANCE',
        source: '续分享',
        recordedAt: now(),
      })
      setReshareTarget(null)
      reshareForm.resetFields()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const handleRevoke = async (values: RevokeFormValues) => {
    if (!revokeTarget) return
    setSubmitting(true)
    try {
      const res = await revokeShare(revokeTarget.shareId, values)
      message.success(`分享已撤销，投影纪元推进至 #${res.projectionEpoch}`)
      upsertRow({
        shareId: res.shareId,
        status: res.status,
        version: res.version,
        projectionEpoch: res.projectionEpoch,
        scopeType: revokeTarget.scopeType,
        source: revokeTarget.source,
        recordedAt: now(),
      })
      setRevokeTarget(null)
      revokeForm.resetFields()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const filteredRows = useMemo(() => {
    return rows.filter((row) => {
      if (appliedStatus !== 'ALL' && row.status !== appliedStatus) return false
      if (appliedTimeRange && appliedTimeRange[0] && appliedTimeRange[1]) {
        const t = dayjs(row.recordedAt).valueOf()
        if (t < appliedTimeRange[0].startOf('day').valueOf() || t > appliedTimeRange[1].endOf('day').valueOf()) {
          return false
        }
      }
      return true
    })
  }, [rows, appliedStatus, appliedTimeRange])

  const columns: TableColumnsType<ShareRow> = [
    {
      title: '分享 ID',
      dataIndex: 'shareId',
      render: (v: string) => <Typography.Text copyable={{ text: v }}>{v}</Typography.Text>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: (v: ShareStatus) => <ShareStatusTag status={v} />,
    },
    {
      title: '作用域模式',
      dataIndex: 'scopeType',
      render: (v?: ShareScopeType) => <Tag color="blue">{v ?? 'INSTANCE'}</Tag>,
    },
    { title: '版本', dataIndex: 'version', render: (v: number) => `v${v}` },
    {
      title: '投影纪元',
      dataIndex: 'projectionEpoch',
      render: (v: number) => <Tag color="purple">#{v}</Tag>,
    },
    { title: '来源', dataIndex: 'source' },
    { title: '记录时间', dataIndex: 'recordedAt' },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => {
        const terminal = record.status === 'REVOKED' || record.status === 'EXPIRED'
        if (terminal) {
          return <span className="text-xs text-slate-300">—</span>
        }
        return (
          <Space size={0}>
            {hasOperation('share:revoke') && (
              <Button
                type="link"
                size="small"
                icon={<StopOutlined />}
                onClick={() => {
                  setRevokeTarget(record)
                  revokeForm.setFieldsValue({ version: record.version, reason: '' })
                }}
              >
                撤销
              </Button>
            )}
            <Button
              type="link"
              size="small"
              icon={<UserSwitchOutlined />}
              onClick={() => {
                setReshareTarget(record)
                reshareForm.setFieldsValue({ parentVersion: record.version })
              }}
            >
              续分享
            </Button>
          </Space>
        )
      },
    },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl bg-white px-6 py-5 shadow-sm">
        <div className="flex items-center gap-4">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-amber-500 to-orange-400 text-white">
            <ShareAltOutlined className="text-xl" />
          </span>
          <div>
            <h1 className="text-xl font-semibold text-slate-800">资源分享</h1>
            <p className="mt-0.5 text-sm text-slate-400">
              创建、查询、撤销资源实例分享、文件夹继承 ACL 或资源集合分享
            </p>
          </div>
        </div>
        <Button
          type="primary"
          className="font-medium"
          icon={<PlusOutlined />}
          onClick={() => setCreateOpen(true)}
        >
          创建分享
        </Button>
      </div>

      <Card title="按 ID 查询分享" className="rounded-2xl">
        <Form form={queryForm} layout="inline" onFinish={handleQuery}>
          <Form.Item
            name="shareId"
            rules={[{ required: true, message: '请输入分享 ID' }, ID_RULE]}
          >
            <Input placeholder="请输入分享 ID" className="w-64" allowClear />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              className="font-medium"
              icon={<SearchOutlined />}
              loading={queryLoading}
            >
              查询
            </Button>
          </Form.Item>
        </Form>
        {queryResult && (
          <div className="mt-4 flex flex-wrap items-center gap-4 rounded-xl bg-slate-50 px-4 py-3">
            <ShareStatusTag status={queryResult.status} />
            <Typography.Text copyable={{ text: queryResult.shareId }}>
              分享 ID：{queryResult.shareId}
            </Typography.Text>
            <span className="text-sm text-slate-500">版本 v{queryResult.version}</span>
            <span className="text-sm text-slate-500">
              投影纪元 #{queryResult.projectionEpoch}
            </span>
          </div>
        )}
      </Card>

      <Card
        title="本次会话的分享记录"
        className="rounded-2xl"
        extra={
          <Space wrap>
            <Select<ShareStatus | 'ALL'>
              value={draftStatus}
              onChange={setDraftStatus}
              style={{ width: 136 }}
              options={[
                { value: 'ALL', label: '全部状态' },
                { value: 'WAITING', label: '待生效' },
                { value: 'ACTIVE', label: '生效中' },
                { value: 'REVOKED', label: '已撤销' },
                { value: 'EXPIRED', label: '已过期' },
              ]}
            />
            <DatePicker.RangePicker
              placeholder={['记录开始日期', '记录结束日期']}
              style={{ width: 260 }}
              value={draftTimeRange}
              onChange={(values) => setDraftTimeRange(values)}
            />
            <Button type="primary" icon={<SearchOutlined />} onClick={handleFilterSearch}>
              查询
            </Button>
            <Button icon={<ReloadOutlined />} onClick={handleFilterReset}>
              重置
            </Button>
          </Space>
        }
      >
        <Table
          columns={columns}
          dataSource={filteredRows}
          rowKey="shareId"
          pagination={{
            defaultPageSize: 10,
            pageSizeOptions: ['10', '20', '50', '100'],
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条分享记录`,
          }}
          scroll={{ x: true }}
          locale={{
            emptyText: (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无分享记录">
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => setCreateOpen(true)}
                >
                  创建分享
                </Button>
              </Empty>
            ),
          }}
        />
      </Card>

      <Drawer
        title="创建分享"
        open={createOpen}
        onClose={() => {
          setCreateOpen(false)
          createForm.resetFields()
        }}
        destroyOnClose
        width={640}
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
              创建分享
            </Button>
          </Space>
        }
      >
        <Form
          form={createForm}
          layout="vertical"
          className="pt-2"
          initialValues={{
            scopeType: 'INSTANCE',
            target: { type: 'USER' },
            canReshare: false,
            expireAt: dayjs().add(7, 'day'),
          }}
          onFinish={handleCreate}
        >
          <ShareFormFields />
        </Form>
      </Drawer>

      <Drawer
        title={`续分享${reshareTarget ? ` · 父分享 ${reshareTarget.shareId}` : ''}`}
        open={!!reshareTarget}
        onClose={() => {
          setReshareTarget(null)
          reshareForm.resetFields()
        }}
        destroyOnClose
        width={640}
        extra={
          <Space>
            <Button
              onClick={() => {
                setReshareTarget(null)
                reshareForm.resetFields()
              }}
            >
              取消
            </Button>
            <Button type="primary" loading={submitting} onClick={() => reshareForm.submit()}>
              创建续分享
            </Button>
          </Space>
        }
      >
        <Form
          form={reshareForm}
          layout="vertical"
          className="pt-2"
          initialValues={{
            scopeType: 'INSTANCE',
            target: { type: 'USER' },
            canReshare: false,
            expireAt: dayjs().add(7, 'day'),
          }}
          onFinish={handleReshare}
        >
          <Form.Item
            name="parentVersion"
            label="父分享当前版本（parentVersion）"
            rules={[{ required: true, message: '请输入父分享当前版本' }]}
          >
            <InputNumber min={0} precision={0} style={{ width: '100%' }} placeholder="0" />
          </Form.Item>
          <ShareFormFields />
        </Form>
      </Drawer>

      <Drawer
        title={`撤销分享${revokeTarget ? ` · ${revokeTarget.shareId}` : ''}`}
        open={!!revokeTarget}
        onClose={() => {
          setRevokeTarget(null)
          revokeForm.resetFields()
        }}
        destroyOnClose
        width={520}
        extra={
          <Space>
            <Button
              onClick={() => {
                setRevokeTarget(null)
                revokeForm.resetFields()
              }}
            >
              取消
            </Button>
            <Button type="primary" danger loading={submitting} onClick={() => revokeForm.submit()}>
              确认撤销
            </Button>
          </Space>
        }
      >
        <Form form={revokeForm} layout="vertical" className="pt-2" onFinish={handleRevoke}>
          <Form.Item
            name="version"
            label="当前版本（乐观锁）"
            rules={[{ required: true, message: '请输入当前版本' }]}
          >
            <InputNumber min={0} precision={0} style={{ width: '100%' }} placeholder="0" />
          </Form.Item>
          <Form.Item
            name="reason"
            label="撤销原因"
            rules={[
              { required: true, message: '请输入撤销原因' },
              { max: 500, message: '最长 500 字符' },
            ]}
          >
            <Input.TextArea rows={3} placeholder="请输入撤销原因" />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
