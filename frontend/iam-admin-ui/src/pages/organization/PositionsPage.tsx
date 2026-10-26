import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  CheckCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  StopOutlined,
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
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { TableColumnsType } from 'antd'
import {
  createPosition,
  deletePosition,
  disablePosition,
  enablePosition,
  listPositions,
  updatePosition,
} from '@/api/organization/positions'
import { resolveErrorMessage } from '@/api/client/errors'
import { usePageSchema } from '@/hooks/useServerMetadata'
import { useAuthStore } from '@/stores/auth'
import { dayjs } from '@/utils/dayjs'
import type { CreatePositionRequest, PositionItem, UpdatePositionRequest } from '@/types/organization'

const CODE_PATTERN = /^[A-Z][A-Z0-9_]{1,63}$/

const STATUS_TAG: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '启用' },
  DISABLED: { color: 'red', label: '停用' },
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

export default function PositionsPage() {
  const { message } = App.useApp()
  const user = useAuthStore((s) => s.user)
  const tenantId = user?.tenantId || '1'
  const { hasOperation } = usePageSchema('organization-positions')

  const [positions, setPositions] = useState<PositionItem[]>([])
  const [loading, setLoading] = useState(false)
  const [draftKeyword, setDraftKeyword] = useState('')
  const [draftStatus, setDraftStatus] = useState<string | undefined>(undefined)
  const [appliedKeyword, setAppliedKeyword] = useState('')
  const [appliedStatus, setAppliedStatus] = useState<string | undefined>(undefined)

  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const [createOpen, setCreateOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [editingPosition, setEditingPosition] = useState<PositionItem | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const [createForm] = Form.useForm<CreatePositionRequest>()
  const [editForm] = Form.useForm<UpdatePositionRequest>()

  const fetchPositions = useCallback(async () => {
    setLoading(true)
    try {
      const list = await listPositions(tenantId, appliedStatus)
      setPositions(list)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }, [tenantId, appliedStatus, message])

  const handleSearch = () => {
    setAppliedKeyword(draftKeyword.trim())
    setAppliedStatus(draftStatus)
    setPage(1)
  }

  const handleReset = () => {
    setDraftKeyword('')
    setDraftStatus(undefined)
    setAppliedKeyword('')
    setAppliedStatus(undefined)
    setPage(1)
  }

  useEffect(() => {
    void fetchPositions()
  }, [fetchPositions])

  const filtered = useMemo(() => {
    const text = appliedKeyword.toLowerCase()
    if (!text) return positions
    return positions.filter(
      (item) =>
        item.code.toLowerCase().includes(text) ||
        item.name.toLowerCase().includes(text) ||
        item.id.includes(text),
    )
  }, [positions, appliedKeyword])

  const handleCreate = async () => {
    try {
      const values = await createForm.validateFields()
      setSubmitting(true)
      await createPosition({
        ...values,
        code: values.code.trim().toUpperCase(),
        name: values.name.trim(),
        sortOrder: values.sortOrder ?? 0,
      })
      message.success('岗位创建成功')
      setCreateOpen(false)
      createForm.resetFields()
      void fetchPositions()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const handleEdit = async () => {
    if (!editingPosition) return
    try {
      const values = await editForm.validateFields()
      setSubmitting(true)
      await updatePosition(editingPosition.id, {
        name: values.name.trim(),
        sortOrder: values.sortOrder ?? editingPosition.sortOrder,
      })
      message.success('岗位更新成功')
      setEditOpen(false)
      setEditingPosition(null)
      void fetchPositions()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const handleToggleStatus = async (record: PositionItem) => {
    try {
      if (record.status === 'ACTIVE') {
        await disablePosition(record.id, tenantId)
        message.success(`岗位「${record.name}」已停用`)
      } else {
        await enablePosition(record.id, tenantId)
        message.success(`岗位「${record.name}」已启用`)
      }
      void fetchPositions()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const handleDelete = async (record: PositionItem) => {
    try {
      await deletePosition(record.id, tenantId)
      message.success(`岗位「${record.name}」已删除`)
      void fetchPositions()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const columns: TableColumnsType<PositionItem> = [
    { title: '岗位ID', dataIndex: 'id', width: 220, render: renderId },
    {
      title: '岗位编码',
      dataIndex: 'code',
      width: 180,
      render: (val: string) => <Tag color="blue">{val}</Tag>,
    },
    { title: '岗位名称', dataIndex: 'name', ellipsis: true },
    { title: '排序号', dataIndex: 'sortOrder', width: 90 },
    { title: '状态', dataIndex: 'status', width: 90, render: renderStatus },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (val: string) => (val ? dayjs(val).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_, record) => (
        <Space size={4}>
          {hasOperation('position:update') && (
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => {
                setEditingPosition(record)
                editForm.setFieldsValue({
                  name: record.name,
                  sortOrder: record.sortOrder,
                })
                setEditOpen(true)
              }}
            >
              编辑
            </Button>
          )}
          {hasOperation('position:toggle-status') && (
            <Button
              type="link"
              size="small"
              danger={record.status === 'ACTIVE'}
              icon={record.status === 'ACTIVE' ? <StopOutlined /> : <CheckCircleOutlined />}
              onClick={() => void handleToggleStatus(record)}
            >
              {record.status === 'ACTIVE' ? '停用' : '启用'}
            </Button>
          )}
          {hasOperation('position:delete') && (
            <Popconfirm
              title="确认删除该岗位？"
              description="删除后无法恢复，若已有用户关联此岗位将被拒绝。"
              onConfirm={() => void handleDelete(record)}
              okText="删除"
              cancelText="取消"
              okButtonProps={{ danger: true }}
            >
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-slate-800">岗位管理</h1>
          <p className="mt-1 text-sm text-slate-400">
            企业组织独立岗位主数据管理，支持岗位创建、编辑、启停、删除及排序
          </p>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void fetchPositions()} loading={loading}>
            刷新
          </Button>
          {hasOperation('position:create') && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                createForm.resetFields()
                setCreateOpen(true)
              }}
            >
              新建岗位
            </Button>
          )}
        </Space>
      </div>

      <Card className="rounded-2xl" styles={{ body: { paddingTop: 16 } }}>
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <Input
            allowClear
            placeholder="按 编码 / 名称 / ID 搜索"
            style={{ width: 260 }}
            prefix={<SearchOutlined className="text-slate-400" />}
            value={draftKeyword}
            onChange={(e) => setDraftKeyword(e.target.value)}
            onPressEnter={handleSearch}
          />
          <Select
            allowClear
            placeholder="状态"
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

        <Table<PositionItem>
          rowKey="id"
          size="middle"
          loading={loading}
          columns={columns}
          dataSource={filtered}
          pagination={{
            current: page,
            pageSize,
            defaultPageSize: 10,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100'],
            onChange: (p, s) => {
              setPage(p)
              setPageSize(s)
            },
            showTotal: (total) => `共 ${total} 条岗位`,
          }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无岗位数据"
                className="py-8"
              >
                <Button type="primary" onClick={() => setCreateOpen(true)}>
                  新建岗位
                </Button>
              </Empty>
            ),
          }}
        />
      </Card>

      <Drawer
        title="新建岗位"
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        width={520}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setCreateOpen(false)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={handleCreate}>
              创建
            </Button>
          </Space>
        }
        footer={
          <div className="flex justify-end gap-2">
            <Button onClick={() => setCreateOpen(false)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={handleCreate}>
              创建
            </Button>
          </div>
        }
      >
        <Form form={createForm} layout="vertical" className="pt-2" requiredMark={false}>
          <Form.Item
            name="code"
            label="岗位编码"
            rules={[
              { required: true, message: '请输入岗位编码' },
              { pattern: CODE_PATTERN, message: '大写字母开头，支持大写字母、数字与下划线，2-64位' },
            ]}
          >
            <Input placeholder="例如：TECH_LEAD" maxLength={64} />
          </Form.Item>
          <Form.Item
            name="name"
            label="岗位名称"
            rules={[{ required: true, message: '请输入岗位名称' }]}
          >
            <Input placeholder="例如：技术组长" maxLength={128} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序号" initialValue={0}>
            <InputNumber min={0} max={9999} className="w-full" />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title="编辑岗位"
        open={editOpen}
        onClose={() => {
          setEditOpen(false)
          setEditingPosition(null)
        }}
        width={520}
        destroyOnClose
        extra={
          <Space>
            <Button
              onClick={() => {
                setEditOpen(false)
                setEditingPosition(null)
              }}
            >
              取消
            </Button>
            <Button type="primary" loading={submitting} onClick={handleEdit}>
              保存
            </Button>
          </Space>
        }
        footer={
          <div className="flex justify-end gap-2">
            <Button
              onClick={() => {
                setEditOpen(false)
                setEditingPosition(null)
              }}
            >
              取消
            </Button>
            <Button type="primary" loading={submitting} onClick={handleEdit}>
              保存
            </Button>
          </div>
        }
      >
        <Form form={editForm} layout="vertical" className="pt-2" requiredMark={false}>
          <Form.Item label="岗位编码">
            <Input value={editingPosition?.code} disabled />
          </Form.Item>
          <Form.Item
            name="name"
            label="岗位名称"
            rules={[{ required: true, message: '请输入岗位名称' }]}
          >
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序号">
            <InputNumber min={0} max={9999} className="w-full" />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
