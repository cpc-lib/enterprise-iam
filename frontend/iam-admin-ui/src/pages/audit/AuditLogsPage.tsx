import { useCallback, useEffect, useState } from 'react'
import {
  AuditOutlined,
  BarChartOutlined,
  CloudDownloadOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import {
  App,
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { Dayjs } from 'dayjs'
import { ApiError, resolveErrorMessage } from '@/api/client/errors'
import {
  exportAuditArchive,
  fetchAuditAnalyticsSummary,
  queryAuditLogs,
} from '@/api/audit'
import { useAuthStore } from '@/stores/auth'
import { dayjs } from '@/utils/dayjs'
import type { AuditAnalyticsSummary, AuditLog } from '@/types/audit'

interface AuditQueryValues {
  actorId?: string
  action?: string
  range?: [Dayjs, Dayjs] | null
  limit?: number
}

const ID_PATTERN = /^[1-9][0-9]{0,18}$/

function formatAuditTime(value?: string | null): string {
  if (!value) return '—'
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : '—'
}

function prettyDetail(detail: string): string {
  try {
    return JSON.stringify(JSON.parse(detail), null, 2)
  } catch {
    return detail
  }
}

export default function AuditLogsPage() {
  const { message } = App.useApp()
  const tenantId = useAuthStore((state) => state.user?.tenantId)
  const [form] = Form.useForm<AuditQueryValues>()

  const [logs, setLogs] = useState<AuditLog[]>([])
  const [loading, setLoading] = useState(false)
  const [degraded, setDegraded] = useState(false)

  // V2.0 OLAP 审计分析摘要与归档导出
  const [analytics, setAnalytics] = useState<AuditAnalyticsSummary | null>(null)
  const [exporting, setExporting] = useState(false)

  const loadAnalytics = useCallback(async () => {
    if (!tenantId || !ID_PATTERN.test(tenantId)) return
    try {
      const summary = await fetchAuditAnalyticsSummary(tenantId)
      setAnalytics(summary)
    } catch {
      // OLAP 摘要接口降级静默处理
    }
  }, [tenantId])

  const handleFinish = async (values: AuditQueryValues): Promise<void> => {
    if (!tenantId || !ID_PATTERN.test(tenantId)) {
      message.error('无法识别当前登录租户，请重新登录后再试')
      return
    }
    setLoading(true)
    try {
      const data = await queryAuditLogs({
        tenantId,
        actorId: values.actorId?.trim() || undefined,
        action: values.action?.trim() || undefined,
        from: values.range?.[0]?.toISOString(),
        to: values.range?.[1]?.toISOString(),
        limit: values.limit ?? 100,
      })
      setLogs(data)
      setDegraded(false)
      void loadAnalytics()
    } catch (error) {
      if (error instanceof ApiError && (error.status === 404 || error.status === 503)) {
        setDegraded(true)
        setLogs([])
      } else {
        message.error(resolveErrorMessage(error))
      }
    } finally {
      setLoading(false)
    }
  }

  const handleArchiveExport = async () => {
    if (!tenantId || !ID_PATTERN.test(tenantId)) return
    setExporting(true)
    try {
      const res = await exportAuditArchive(tenantId)
      message.success(
        `审计冷归档导出完成：共归档 ${res.exportedCount} 条事件至 ${res.storageTarget}`,
      )
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setExporting(false)
    }
  }

  useEffect(() => {
    form.submit()
  }, [form])

  const columns: ColumnsType<AuditLog> = [
    {
      title: '审计 ID',
      dataIndex: 'id',
      width: 200,
      render: (id: string) => (
        <Typography.Text copyable={{ text: id }} className="text-xs">
          {id}
        </Typography.Text>
      ),
    },
    {
      title: '操作者',
      dataIndex: 'actorId',
      width: 180,
      render: (actorId: string) => (
        <Typography.Text copyable={{ text: actorId }} className="text-xs">
          {actorId}
        </Typography.Text>
      ),
    },
    {
      title: '操作',
      dataIndex: 'action',
      width: 180,
      ellipsis: true,
      render: (action: string) => <Tag color="blue">{action}</Tag>,
    },
    {
      title: '资源类型',
      dataIndex: 'resourceType',
      width: 140,
      render: (resourceType: string) => resourceType || '—',
    },
    {
      title: '资源 ID',
      dataIndex: 'resourceId',
      width: 200,
      render: (resourceId: string) => (
        <Typography.Text copyable={{ text: resourceId }} className="text-xs">
          {resourceId}
        </Typography.Text>
      ),
    },
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 180,
      render: (createdAt: string) => formatAuditTime(createdAt),
    },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-800">审计日志与OLAP分析</h1>
          <p className="mt-1 text-sm text-slate-400">
            关键操作留痕、ClickHouse / OLAP 多维聚合分析与合规冷存储归档
          </p>
        </div>
        <Space>
          <Button
            icon={<CloudDownloadOutlined />}
            loading={exporting}
            onClick={() => void handleArchiveExport()}
          >
            冷归档导出
          </Button>
          <Button icon={<ReloadOutlined />} onClick={() => form.submit()}>
            刷新
          </Button>
        </Space>
      </div>

      {analytics && (
        <Row gutter={16}>
          <Col xs={12} sm={6}>
            <Card size="small" className="rounded-xl">
              <Statistic
                title="审计事件总数"
                value={analytics.totalEvents}
                prefix={<BarChartOutlined className="text-blue-500" />}
              />
            </Card>
          </Col>
          <Col xs={12} sm={6}>
            <Card size="small" className="rounded-xl">
              <Statistic
                title="允许 / 成功事件"
                value={analytics.allowCount}
                valueStyle={{ color: '#16a34a' }}
              />
            </Card>
          </Col>
          <Col xs={12} sm={6}>
            <Card size="small" className="rounded-xl">
              <Statistic
                title="拒绝 / 阻断事件"
                value={analytics.denyCount}
                valueStyle={{ color: '#dc2626' }}
              />
            </Card>
          </Col>
          <Col xs={12} sm={6}>
            <Card size="small" className="rounded-xl">
              <Statistic
                title={`高危操作 (${analytics.olapEngine})`}
                value={analytics.highRiskEventCount}
                valueStyle={{ color: '#d97706' }}
              />
            </Card>
          </Col>
        </Row>
      )}

      <Alert
        type="info"
        showIcon
        message="支持的筛选条件"
        description="租户 ID 自动取当前登录租户；支持按操作者 ID（精确）、操作类型 action（精确匹配）、时间范围（from/to）与返回条数 limit（默认 100）过滤。"
      />

      {degraded && (
        <Alert
          type="warning"
          showIcon
          message="审计服务不可用"
          description="审计查询接口（/admin/v1/audit/logs）无法访问：可能后端未启动，或 iam.audit.enabled=false。请启动服务后重试。"
        />
      )}

      <Card className="rounded-2xl">
        <Form<AuditQueryValues>
          form={form}
          layout="inline"
          onFinish={(values) => void handleFinish(values)}
          initialValues={{ limit: 100 }}
          className="mb-5 flex flex-wrap items-center gap-3"
        >
          <Form.Item name="range" label="时间范围" className="mb-0">
            <DatePicker.RangePicker showTime placeholder={['开始时间', '结束时间']} style={{ width: 330 }} />
          </Form.Item>
          <Form.Item name="actorId" label="操作者 ID" className="mb-0">
            <Input style={{ width: 140 }} placeholder="精确匹配" allowClear />
          </Form.Item>
          <Form.Item name="action" label="操作类型" className="mb-0">
            <Input allowClear style={{ width: 170 }} placeholder="如 user.login" />
          </Form.Item>
          <Form.Item name="limit" label="条数" className="mb-0">
            <InputNumber min={1} max={1000} style={{ width: 90 }} />
          </Form.Item>
          <Form.Item className="mb-0">
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={loading}>
                查询
              </Button>
              <Button
                onClick={() => {
                  form.resetFields()
                  form.submit()
                }}
              >
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>

        <Table<AuditLog>
          rowKey="id"
          size="middle"
          columns={columns}
          dataSource={logs}
          loading={loading}
          scroll={{ x: 1100 }}
          pagination={{
            defaultPageSize: 10,
            pageSizeOptions: ['10', '20', '50', '100'],
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条记录`,
          }}
          expandable={{
            expandedRowRender: (log) => (
              <pre className="max-h-64 overflow-auto rounded-lg bg-slate-50 p-3 text-xs leading-5 text-slate-600">
                {prettyDetail(log.detail ?? '')}
              </pre>
            ),
            rowExpandable: (log) => !!log.detail,
          }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={degraded ? '审计服务不可用，暂无数据' : '暂无审计日志'}
              >
                <Button icon={<AuditOutlined />} onClick={() => form.submit()}>
                  重新查询
                </Button>
              </Empty>
            ),
          }}
        />
      </Card>
    </div>
  )
}
