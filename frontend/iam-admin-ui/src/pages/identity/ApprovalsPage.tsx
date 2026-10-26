import { useCallback, useEffect, useState } from 'react'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
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
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  approvePermissionRequest,
  listApprovalRequests,
  rejectPermissionRequest,
  submitApprovalRequest,
} from '@/api/identity/approvals'
import { resolveErrorMessage } from '@/api/client/errors'
import { usePageSchema } from '@/hooks/useServerMetadata'
import { useAuthStore } from '@/stores/auth'
import { dayjs } from '@/utils/dayjs'
import type {
  ApprovalRequestType,
  ApprovalStatus,
  CreateApprovalRequestCommand,
  PermissionApprovalRequestItem,
} from '@/types/identity'

const STATUS_TAG_MAP: Record<ApprovalStatus, { color: string; label: string }> = {
  PENDING: { color: 'processing', label: '待审批' },
  APPROVED: { color: 'success', label: '已批准' },
  REJECTED: { color: 'error', label: '已驳回' },
  CANCELLED: { color: 'default', label: '已取消' },
}

const REQUEST_TYPE_LABELS: Record<ApprovalRequestType, string> = {
  ROLE_ASSIGN: '租户角色授权',
  TEAM_ROLE_ASSIGN: '团队角色授权',
  RESOURCE_SHARE: '资源实例共享',
}

export default function ApprovalsPage() {
  const { message } = App.useApp()
  const user = useAuthStore((s) => s.user)
  const { hasOperation } = usePageSchema('identity-approvals')

  const [requests, setRequests] = useState<PermissionApprovalRequestItem[]>([])
  const [loading, setLoading] = useState(false)
  const [statusFilter, setStatusFilter] = useState<ApprovalStatus | undefined>(undefined)

  const [createOpen, setCreateOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [createForm] = Form.useForm<CreateApprovalRequestCommand>()

  const [decisionOpen, setDecisionOpen] = useState(false)
  const [decisionAction, setDecisionAction] = useState<'approve' | 'reject'>('approve')
  const [activeRequest, setActiveRequest] = useState<PermissionApprovalRequestItem | null>(null)
  const [decisionForm] = Form.useForm<{ comment: string }>()
  const [deciding, setDeciding] = useState(false)

  const fetchRequests = useCallback(async () => {
    if (!user?.tenantId) return
    setLoading(true)
    try {
      const list = await listApprovalRequests(user.tenantId, statusFilter)
      setRequests(list)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }, [user?.tenantId, statusFilter, message])

  useEffect(() => {
    void fetchRequests()
  }, [fetchRequests])

  const handleSubmitRequest = async () => {
    if (!user?.tenantId || !user?.userId) return
    try {
      const values = await createForm.validateFields()
      setSubmitting(true)
      const created = await submitApprovalRequest({
        ...values,
        tenantId: user.tenantId,
        applicantUserId: values.applicantUserId || user.userId,
      })
      message.success(`权限申请单 #${created.id} 已提交`)
      setCreateOpen(false)
      createForm.resetFields()
      setRequests((prev) => [created, ...prev])
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const openDecisionModal = (record: PermissionApprovalRequestItem, action: 'approve' | 'reject') => {
    setActiveRequest(record)
    setDecisionAction(action)
    decisionForm.resetFields()
    setDecisionOpen(true)
  }

  const handleConfirmDecision = async () => {
    if (!activeRequest || !user?.tenantId || !user?.userId) return
    try {
      const values = await decisionForm.validateFields()
      setDeciding(true)
      const payload = {
        tenantId: user.tenantId,
        approverUserId: user.userId,
        comment: values.comment,
      }
      const updated =
        decisionAction === 'approve'
          ? await approvePermissionRequest(activeRequest.id, payload)
          : await rejectPermissionRequest(activeRequest.id, payload)
      message.success(
        decisionAction === 'approve'
          ? `已批准申请 #${updated.id}，对应权限绑定已自动生效`
          : `已驳回申请 #${updated.id}`,
      )
      setDecisionOpen(false)
      setRequests((prev) => prev.map((item) => (item.id === updated.id ? updated : item)))
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return
      message.error(resolveErrorMessage(error))
    } finally {
      setDeciding(false)
    }
  }

  const columns: ColumnsType<PermissionApprovalRequestItem> = [
    {
      title: '申请单 ID',
      dataIndex: 'id',
      width: 160,
      render: (id: string) => <Typography.Text copyable>{id}</Typography.Text>,
    },
    {
      title: '申请人 ID',
      dataIndex: 'applicantUserId',
      width: 140,
    },
    {
      title: '申请类型',
      dataIndex: 'requestType',
      width: 150,
      render: (type: ApprovalRequestType) => <Tag color="blue">{REQUEST_TYPE_LABELS[type] ?? type}</Tag>,
    },
    {
      title: '目标角色 / 资源',
      key: 'target',
      width: 200,
      render: (_, record) => (
        <div>
          <div className="font-medium text-slate-700">{record.targetName}</div>
          <div className="text-xs text-slate-400">ID: {record.targetId}</div>
        </div>
      ),
    },
    {
      title: '申请理由',
      dataIndex: 'justification',
      width: 220,
      ellipsis: true,
    },
    {
      title: '有效期(小时)',
      dataIndex: 'durationHours',
      width: 110,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (status: ApprovalStatus) => {
        const meta = STATUS_TAG_MAP[status] ?? { color: 'default', label: status }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: '申请时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (val: string) => (val ? dayjs(val).format('YYYY-MM-DD HH:mm:ss') : '—'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      fixed: 'right',
      render: (_, record) => {
        if (record.status !== 'PENDING') {
          return (
            <span className="text-xs text-slate-400">
              {record.decisionComment ? `意见：${record.decisionComment}` : '已归档'}
            </span>
          )
        }
        return (
          <Space size="small">
            {hasOperation('approve') && (
              <Button
                type="link"
                size="small"
                icon={<CheckCircleOutlined />}
                onClick={() => openDecisionModal(record, 'approve')}
              >
                批准
              </Button>
            )}
            {hasOperation('reject') && (
              <Button
                type="link"
                danger
                size="small"
                icon={<CloseCircleOutlined />}
                onClick={() => openDecisionModal(record, 'reject')}
              >
                驳回
              </Button>
            )}
          </Space>
        )
      },
    },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-800">权限申请与审批流</h1>
          <p className="mt-1 text-sm text-slate-400">
            自助式角色与共享权限申请，支持提交前静态职责分离（SoD）预检及审批通过后自动赋权
          </p>
        </div>
        <Space>
          <Select<ApprovalStatus | undefined>
            allowClear
            placeholder="按状态筛选"
            className="w-36"
            value={statusFilter}
            onChange={(val) => setStatusFilter(val)}
            options={[
              { value: 'PENDING', label: '待审批' },
              { value: 'APPROVED', label: '已批准' },
              { value: 'REJECTED', label: '已驳回' },
            ]}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void fetchRequests()} loading={loading}>
            刷新
          </Button>
          {hasOperation('create') && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                createForm.setFieldsValue({
                  applicantUserId: user?.userId ?? '',
                  requestType: 'ROLE_ASSIGN',
                  durationHours: 168,
                })
                setCreateOpen(true)
              }}
            >
              发起权限申请
            </Button>
          )}
        </Space>
      </div>

      <Alert
        type="info"
        showIcon
        message="自动职责分离（SoD）校验与即时生效"
        description="当发起「租户角色授权」申请或审批通过时，系统将自动执行静态 SoD 冲突检查；若与申请人现有角色存在互斥，系统将阻断赋权。"
      />

      <Card className="rounded-2xl" styles={{ body: { paddingTop: 16 } }}>
        <Table<PermissionApprovalRequestItem>
          rowKey="id"
          size="middle"
          columns={columns}
          dataSource={requests}
          loading={loading}
          scroll={{ x: 1200 }}
          pagination={{
            defaultPageSize: 10,
            pageSizeOptions: ['10', '20', '50', '100'],
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条记录`,
          }}
          locale={{
            emptyText: <Empty description="暂无权限审批单" />,
          }}
        />
      </Card>

      <Drawer
        title="发起权限申请"
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        width={560}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setCreateOpen(false)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={() => void handleSubmitRequest()}>
              提交申请
            </Button>
          </Space>
        }
      >
        <Form form={createForm} layout="vertical">
          <Form.Item
            name="applicantUserId"
            label="申请人用户 ID"
            rules={[{ required: true, message: '请输入申请人用户 ID' }]}
          >
            <Input placeholder="默认当前登录用户 ID" />
          </Form.Item>
          <Form.Item
            name="requestType"
            label="申请类型"
            rules={[{ required: true, message: '请选择申请类型' }]}
          >
            <Select
              options={[
                { value: 'ROLE_ASSIGN', label: '租户角色授权 (ROLE_ASSIGN)' },
                { value: 'TEAM_ROLE_ASSIGN', label: '团队角色授权 (TEAM_ROLE_ASSIGN)' },
                { value: 'RESOURCE_SHARE', label: '资源实例共享 (RESOURCE_SHARE)' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="targetId"
            label="目标角色 / 资源 ID"
            rules={[{ required: true, message: '请输入目标 ID' }]}
          >
            <Input placeholder="例如：角色 ID 2001" />
          </Form.Item>
          <Form.Item
            name="targetName"
            label="目标名称"
            rules={[{ required: true, message: '请输入目标角色或资源名称' }]}
          >
            <Input placeholder="例如：财务复核员 (FINANCE_AUDITOR)" />
          </Form.Item>
          <Form.Item
            name="durationHours"
            label="申请有效期（小时）"
            rules={[{ required: true, message: '请输入有效期' }]}
          >
            <InputNumber min={1} max={8760} className="w-full" />
          </Form.Item>
          <Form.Item
            name="justification"
            label="业务申请理由"
            rules={[{ required: true, message: '请填写申请理由' }]}
          >
            <Input.TextArea rows={3} placeholder="请说明申请该权限的业务背景与工单号" />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={decisionAction === 'approve' ? '批准权限申请' : '驳回权限申请'}
        open={decisionOpen}
        onClose={() => setDecisionOpen(false)}
        width={520}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setDecisionOpen(false)}>取消</Button>
            <Button
              type="primary"
              danger={decisionAction === 'reject'}
              loading={deciding}
              onClick={() => void handleConfirmDecision()}
            >
              {decisionAction === 'approve' ? '确认批准' : '确认驳回'}
            </Button>
          </Space>
        }
      >
        <Form form={decisionForm} layout="vertical">
          <Form.Item
            name="comment"
            label="审批意见"
            rules={[{ required: true, message: '请填写审批意见' }]}
          >
            <Input.TextArea
              rows={3}
              placeholder={
                decisionAction === 'approve'
                  ? '同意赋权，符合最小权限与职责分离规范'
                  : '请补充业务负责人签批工单后重新发起'
              }
            />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
