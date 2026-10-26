import { useCallback, useEffect, useState } from 'react'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  DesktopOutlined,
  DisconnectOutlined,
  MobileOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons'
import {
  App,
  Button,
  Card,
  Col,
  Form,
  Input,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { resolveErrorMessage } from '@/api/client/errors'
import { listSessions, revokeSession } from '@/api/auth/sessions'
import type { ActiveSession } from '@/api/auth/sessions'
import { dayjs } from '@/utils/dayjs'

const { Text } = Typography

interface FilterFormValues {
  userId?: string
  status?: string
}

export default function SessionsPage() {
  const { message } = App.useApp()

  const [sessions, setSessions] = useState<ActiveSession[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [actionLoading, setActionLoading] = useState<Record<string, boolean>>({})

  const [form] = Form.useForm<FilterFormValues>()
  const [filters, setFilters] = useState<FilterFormValues>({ status: 'ACTIVE' })

  const loadSessions = useCallback(async () => {
    setLoading(true)
    try {
      const data = await listSessions({
        userId: filters.userId || undefined,
        status: filters.status || undefined,
        page,
        pageSize,
      })
      setSessions(data.items || [])
      setTotal(data.total || 0)
    } catch (err) {
      message.error(resolveErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }, [filters, page, pageSize, message])

  useEffect(() => {
    loadSessions()
  }, [loadSessions])

  const handleSearch = (values: FilterFormValues) => {
    setFilters(values)
    setPage(1)
  }

  const handleReset = () => {
    form.resetFields()
    setFilters({})
    setPage(1)
  }

  const handleRevoke = async (sessionId: string) => {
    setActionLoading((prev) => ({ ...prev, [sessionId]: true }))
    try {
      await revokeSession(sessionId, '管理员控制台强制下线')
      message.success('已成功强制下线该会话')
      loadSessions()
    } catch (err) {
      message.error(resolveErrorMessage(err))
    } finally {
      setActionLoading((prev) => ({ ...prev, [sessionId]: false }))
    }
  }

  const activeCount = sessions.filter((s) => s.status === 'ACTIVE').length
  const revokedCount = sessions.filter((s) => s.status === 'REVOKED').length

  const columns: TableColumnsType<ActiveSession> = [
    {
      title: '会话 ID',
      dataIndex: 'sessionId',
      key: 'sessionId',
      width: 190,
      render: (val: string) => (
        <Text copyable code className="text-xs">
          {val}
        </Text>
      ),
    },
    {
      title: '用户 ID',
      dataIndex: 'userId',
      key: 'userId',
      width: 140,
      render: (val: string) => (
        <Text copyable code className="text-xs text-blue-600">
          {val}
        </Text>
      ),
    },
    {
      title: '设备环境',
      dataIndex: 'deviceType',
      key: 'deviceType',
      width: 110,
      render: (device: string) => {
        const isMobile = device && (device.toUpperCase().includes('MOBILE') || device.toUpperCase().includes('APP'))
        return (
          <Space size={4}>
            {isMobile ? <MobileOutlined /> : <DesktopOutlined />}
            <span className="text-xs">{device || 'PC Browser'}</span>
          </Space>
        )
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => {
        if (status === 'ACTIVE') {
          return (
            <Tag icon={<CheckCircleOutlined />} color="success">
              在线
            </Tag>
          )
        }
        if (status === 'REVOKED') {
          return (
            <Tag icon={<CloseCircleOutlined />} color="error">
              已强退
            </Tag>
          )
        }
        return <Tag color="default">已过期</Tag>
      },
    },
    {
      title: '登录时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (time: string) => (
        <span className="text-xs">{time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'}</span>
      ),
    },
    {
      title: '最近活跃',
      dataIndex: 'lastAccessAt',
      key: 'lastAccessAt',
      width: 170,
      render: (time: string) => (
        <span className="text-xs">{time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'}</span>
      ),
    },
    {
      title: '绝对过期时间',
      dataIndex: 'absoluteExpireAt',
      key: 'absoluteExpireAt',
      width: 170,
      render: (time: string) => (
        <span className="text-xs text-neutral-500">
          {time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'}
        </span>
      ),
    },
    {
      title: '强退原因',
      key: 'revocation',
      width: 150,
      render: (_, record) => {
        if (record.status !== 'REVOKED') return <span className="text-neutral-400 text-xs">-</span>
        return (
          <span className="text-xs text-rose-500" title={record.revokedAt || ''}>
            {record.revokeReason || '手动下线'}
          </span>
        )
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      fixed: 'right',
      render: (_, record) => (
        <Popconfirm
          title="强制下线确认"
          description={`确定要立即注销会话 ${record.sessionId} 吗？该用户将需要重新认证。`}
          onConfirm={() => handleRevoke(record.sessionId)}
          okText="立即下线"
          cancelText="取消"
          okButtonProps={{ danger: true, loading: actionLoading[record.sessionId] }}
          disabled={record.status !== 'ACTIVE'}
        >
          <Button
            type="link"
            danger
            size="small"
            icon={<DisconnectOutlined />}
            disabled={record.status !== 'ACTIVE'}
            loading={actionLoading[record.sessionId]}
          >
            强退
          </Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      {/* 顶部统计卡片 */}
      <Row gutter={16}>
        <Col span={8}>
          <Card size="small" bordered={false} className="shadow-xs">
            <Statistic
              title="当前展示会话总数"
              value={total}
              prefix={<SafetyCertificateOutlined className="text-blue-500" />}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" bordered={false} className="shadow-xs">
            <Statistic
              title="当前页在线会话"
              value={activeCount}
              valueStyle={{ color: '#3f8600' }}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" bordered={false} className="shadow-xs">
            <Statistic
              title="当前页已撤销会话"
              value={revokedCount}
              valueStyle={{ color: '#cf1322' }}
              prefix={<CloseCircleOutlined />}
            />
          </Card>
        </Col>
      </Row>

      {/* 过滤面板 */}
      <Card size="small" bordered={false} className="shadow-xs">
        <Form
          form={form}
          layout="inline"
          initialValues={{ status: 'ACTIVE' }}
          onFinish={handleSearch}
          className="flex flex-wrap items-center gap-3"
        >
          <Form.Item name="userId" label="用户 ID" className="mb-0">
            <Input placeholder="输入目标用户 ID" allowClear style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="status" label="状态" className="mb-0">
            <Select
              style={{ width: 150 }}
              allowClear
              placeholder="全部状态"
              options={[
                { label: '全部状态', value: '' },
                { label: '在线 (ACTIVE)', value: 'ACTIVE' },
                { label: '已强退 (REVOKED)', value: 'REVOKED' },
                { label: '已过期 (EXPIRED)', value: 'EXPIRED' },
              ]}
            />
          </Form.Item>
          <Form.Item className="mb-0">
            <Space>
              <Button type="primary" htmlType="submit">
                查询
              </Button>
              <Button onClick={handleReset}>重置</Button>
              <Button icon={<ReloadOutlined />} onClick={loadSessions} loading={loading}>
                刷新
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      {/* 会话列表 */}
      <Card size="small" bordered={false} className="shadow-xs">
        <Table<ActiveSession>
          rowKey="sessionId"
          loading={loading}
          columns={columns}
          dataSource={sessions}
          scroll={{ x: 1200 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100'],
            showTotal: (t) => `共 ${t} 条在线会话记录`,
            onChange: (p, s) => {
              setPage(p)
              setPageSize(s)
            },
          }}
        />
      </Card>
    </div>
  )
}
