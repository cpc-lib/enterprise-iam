import { QuestionCircleOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import { App, Button, Card, Empty, Form, Input, Table, Tag, Timeline, Typography } from 'antd'
import type { TableColumnsType } from 'antd'
import { useState } from 'react'
import { explainDecision } from '@/api/authorization/explain'
import { ApiError, resolveErrorMessage } from '@/api/client/errors'
import type { AuthorizationDecision, ExplainResponse } from '@/types/authorization'
import { cn } from '@/utils/cn'
import { dayjs } from '@/utils/dayjs'

interface RecentQuery {
  decisionId: string
  decision: AuthorizationDecision
  reasonCode: string
  evaluatedAt: string
  queriedAt: string
}

interface QueryFormValues {
  decisionId: string
}

export default function ExplainPage() {
  const { message } = App.useApp()
  const [form] = Form.useForm<QueryFormValues>()
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<ExplainResponse | null>(null)
  const [notFound, setNotFound] = useState(false)
  const [recent, setRecent] = useState<RecentQuery[]>([])

  const handleQuery = async (values: QueryFormValues) => {
    const decisionId = values.decisionId.trim()
    if (!decisionId) return
    setLoading(true)
    try {
      const res = await explainDecision(decisionId)
      setResult(res)
      setNotFound(false)
      setRecent((prev) =>
        [
          {
            decisionId: res.decisionId,
            decision: res.decision,
            reasonCode: res.reasonCode,
            evaluatedAt: res.evaluatedAt,
            queriedAt: dayjs().format('YYYY-MM-DD HH:mm:ss'),
          },
          ...prev.filter((item) => item.decisionId !== res.decisionId),
        ].slice(0, 20),
      )
    } catch (error) {
      if (error instanceof ApiError && (error.code === 'IAM_AUTHZ_DECISION_NOT_FOUND' || error.status === 404)) {
        setResult(null)
        setNotFound(true)
      } else {
        message.error(resolveErrorMessage(error))
      }
    } finally {
      setLoading(false)
    }
  }

  const handleRequery = (decisionId: string) => {
    form.setFieldsValue({ decisionId })
    void handleQuery({ decisionId })
  }

  const recentColumns: TableColumnsType<RecentQuery> = [
    {
      title: '决策 ID',
      dataIndex: 'decisionId',
      render: (v: string) => <Typography.Text copyable={{ text: v }}>{v}</Typography.Text>,
    },
    {
      title: '结论',
      dataIndex: 'decision',
      render: (v: AuthorizationDecision) => (
        <Tag color={v === 'ALLOW' ? 'green' : 'red'}>{v}</Tag>
      ),
    },
    { title: '原因码', dataIndex: 'reasonCode' },
    {
      title: '评估时间',
      dataIndex: 'evaluatedAt',
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm:ss'),
    },
    { title: '查询时间', dataIndex: 'queriedAt' },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => handleRequery(record.decisionId)}>
          重新查询
        </Button>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl bg-white px-6 py-5 shadow-sm">
        <div className="flex items-center gap-4">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-sky-500 to-cyan-400 text-white">
            <QuestionCircleOutlined className="text-xl" />
          </span>
          <div>
            <h1 className="text-xl font-semibold text-slate-800">决策解释</h1>
            <p className="mt-0.5 text-sm text-slate-400">
              按决策 ID 查询授权结论与脱敏后的评估步骤
            </p>
          </div>
        </div>
      </div>

      <Card className="rounded-2xl">
        <Form<QueryFormValues> form={form} layout="inline" onFinish={handleQuery}>
          <Form.Item
            name="decisionId"
            label="决策 ID"
            rules={[{ required: true, message: '请输入决策 ID' }]}
          >
            <Input placeholder="请输入决策 ID" className="w-64" allowClear />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              className="font-medium"
              icon={<SearchOutlined />}
              loading={loading}
            >
              查询
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {result && (
        <Card
          title="决策解释结果"
          className="rounded-2xl"
          extra={
            <Typography.Text copyable={{ text: result.decisionId }}>
              决策 ID：{result.decisionId}
            </Typography.Text>
          }
        >
          <div className="flex flex-wrap items-center gap-4">
            <span
              className={cn(
                'inline-flex items-center rounded-full px-5 py-1.5 text-base font-bold',
                result.decision === 'ALLOW'
                  ? 'bg-emerald-50 text-emerald-600'
                  : 'bg-rose-50 text-rose-600',
              )}
            >
              {result.decision}
            </span>
            <Tag color="geekblue" className="!text-xs">
              {result.reasonCode}
            </Tag>
            <span className="text-sm text-slate-400">
              评估时间：{dayjs(result.evaluatedAt).format('YYYY-MM-DD HH:mm:ss')}
            </span>
          </div>
          {result.sanitizedSteps.length > 0 ? (
            <Timeline
              className="mt-6"
              items={result.sanitizedSteps.map((step) => ({
                children: <span className="text-sm text-slate-600">{step}</span>,
              }))}
            />
          ) : (
            <div className="mt-4 text-sm text-slate-400">该决策暂无脱敏评估步骤</div>
          )}
        </Card>
      )}

      {notFound && (
        <Card className="rounded-2xl">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              <div className="space-y-1">
                <div className="text-sm font-medium text-slate-600">未找到该决策的解释记录</div>
                <div className="text-xs text-slate-400">
                  决策记录可能已归档或尚未产生，请确认决策 ID 是否正确（404 ·
                  IAM_AUTHZ_DECISION_NOT_FOUND）
                </div>
              </div>
            }
          >
            <Button icon={<ReloadOutlined />} onClick={() => form.resetFields()}>
              清空重填
            </Button>
          </Empty>
        </Card>
      )}

      <Card title="最近查询" className="rounded-2xl">
        <Table
          columns={recentColumns}
          dataSource={recent}
          rowKey="decisionId"
          pagination={{
            defaultPageSize: 10,
            pageSizeOptions: ['10', '20', '50', '100'],
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条最近查询记录`,
          }}
          scroll={{ x: true }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无查询记录，请在上方输入决策 ID 查询"
              />
            ),
          }}
        />
      </Card>
    </div>
  )
}
