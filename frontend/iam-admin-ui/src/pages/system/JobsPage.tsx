import { useCallback, useEffect, useState } from 'react'
import {
  HistoryOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import {
  App,
  Button,
  Card,
  Drawer,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { resolveErrorMessage } from '@/api/client/errors'
import { listJobExecutions, listJobs, pauseJob, resumeJob, triggerJob } from '@/api/job/jobs'
import { usePageSchema } from '@/hooks/useServerMetadata'
import type { JobDefinition, JobExecutionLog } from '@/types/job'
import { dayjs } from '@/utils/dayjs'

const { Text } = Typography

export default function JobsPage() {
  const { message } = App.useApp()
  const { hasOperation } = usePageSchema('system-jobs')

  const [jobs, setJobs] = useState<JobDefinition[]>([])
  const [loading, setLoading] = useState(false)
  const [actionLoading, setActionLoading] = useState<Record<string, boolean>>({})

  // 抽屉：历史执行记录
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [selectedJob, setSelectedJob] = useState<JobDefinition | null>(null)
  const [executions, setExecutions] = useState<JobExecutionLog[]>([])
  const [executionsLoading, setExecutionsLoading] = useState(false)

  const loadJobs = useCallback(async () => {
    setLoading(true)
    try {
      const data = await listJobs()
      setJobs(data)
    } catch (err) {
      message.error(resolveErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }, [message])

  useEffect(() => {
    loadJobs()
  }, [loadJobs])

  const handleTrigger = async (job: JobDefinition) => {
    setActionLoading((prev) => ({ ...prev, [job.jobCode]: true }))
    try {
      const exec = await triggerJob(job.jobCode)
      message.success(`作业 ${job.jobName} 触发成功，执行状态: ${exec.status}`)
      loadJobs()
    } catch (err) {
      message.error(resolveErrorMessage(err))
    } finally {
      setActionLoading((prev) => ({ ...prev, [job.jobCode]: false }))
    }
  }

  const handleToggleStatus = async (job: JobDefinition) => {
    setActionLoading((prev) => ({ ...prev, [job.jobCode]: true }))
    try {
      if (job.status === 'ACTIVE') {
        await pauseJob(job.jobCode)
        message.success(`作业 ${job.jobName} 已暂停调度`)
      } else {
        await resumeJob(job.jobCode)
        message.success(`作业 ${job.jobName} 已恢复调度`)
      }
      loadJobs()
    } catch (err) {
      message.error(resolveErrorMessage(err))
    } finally {
      setActionLoading((prev) => ({ ...prev, [job.jobCode]: false }))
    }
  }

  const handleViewExecutions = async (job: JobDefinition) => {
    setSelectedJob(job)
    setDrawerOpen(true)
    setExecutionsLoading(true)
    try {
      const logs = await listJobExecutions(job.jobCode)
      setExecutions(logs)
    } catch (err) {
      message.error(resolveErrorMessage(err))
    } finally {
      setExecutionsLoading(false)
    }
  }

  const columns: TableColumnsType<JobDefinition> = [
    {
      title: '任务编码',
      dataIndex: 'jobCode',
      key: 'jobCode',
      render: (code: string) => <Text code>{code}</Text>,
    },
    {
      title: '任务名称',
      dataIndex: 'jobName',
      key: 'jobName',
      render: (name: string, record) => (
        <div>
          <div className="font-medium text-slate-800">{name}</div>
          <div className="text-xs text-slate-400">{record.description}</div>
        </div>
      ),
    },
    {
      title: 'Cron 调度表达式',
      dataIndex: 'cronExpression',
      key: 'cronExpression',
      render: (cron: string) => <Tag color="blue">{cron}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) =>
        status === 'ACTIVE' ? (
          <Tag color="success">运行中</Tag>
        ) : (
          <Tag color="warning">已暂停</Tag>
        ),
    },
    {
      title: '上次执行时间',
      dataIndex: 'lastExecutionTime',
      key: 'lastExecutionTime',
      render: (time?: string) =>
        time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : <Text type="secondary">-</Text>,
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => {
        const isBusy = !!actionLoading[record.jobCode]
        return (
          <Space>
            {hasOperation('job:trigger') && (
              <Button
                type="link"
                size="small"
                icon={<PlayCircleOutlined />}
                loading={isBusy}
                onClick={() => handleTrigger(record)}
              >
                立即执行
              </Button>
            )}
            {record.status === 'ACTIVE' && hasOperation('job:pause') && (
              <Button
                type="link"
                size="small"
                danger
                icon={<PauseCircleOutlined />}
                loading={isBusy}
                onClick={() => handleToggleStatus(record)}
              >
                暂停
              </Button>
            )}
            {record.status === 'PAUSED' && hasOperation('job:resume') && (
              <Button
                type="link"
                size="small"
                icon={<PlayCircleOutlined />}
                loading={isBusy}
                onClick={() => handleToggleStatus(record)}
              >
                恢复
              </Button>
            )}
            <Button
              type="link"
              size="small"
              icon={<HistoryOutlined />}
              onClick={() => handleViewExecutions(record)}
            >
              执行历史
            </Button>
          </Space>
        )
      },
    },
  ]

  const executionColumns: TableColumnsType<JobExecutionLog> = [
    {
      title: '执行流水号',
      dataIndex: 'executionId',
      key: 'executionId',
      render: (id: string) => <Text code>{id}</Text>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        if (status === 'SUCCESS') return <Tag color="success">成功</Tag>
        if (status === 'FAILED') return <Tag color="error">失败</Tag>
        return <Tag color="processing">执行中</Tag>
      },
    },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      key: 'startTime',
      render: (time: string) => (time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '耗时/结束时间',
      dataIndex: 'endTime',
      key: 'endTime',
      render: (endTime?: string, record?: JobExecutionLog) => {
        if (!endTime) return <Text type="secondary">执行中...</Text>
        const start = record?.startTime ? dayjs(record.startTime) : null
        const end = dayjs(endTime)
        const durationMs = start ? end.diff(start) : null
        return (
          <div>
            <div>{end.format('YYYY-MM-DD HH:mm:ss')}</div>
            {durationMs !== null && (
              <div className="text-xs text-slate-400">耗时: {durationMs} ms</div>
            )}
          </div>
        )
      },
    },
    {
      title: '处理指标',
      key: 'metrics',
      render: (_, r) => (
        <span className="text-xs">
          处理: <Tag color="default">{r.itemsProcessed}</Tag>
          收敛: <Tag color="purple">{r.itemsConverged}</Tag>
        </span>
      ),
    },
    {
      title: '异常信息',
      dataIndex: 'errorMessage',
      key: 'errorMessage',
      render: (msg?: string) =>
        msg ? <Text type="danger">{msg}</Text> : <Text type="secondary">-</Text>,
    },
  ]

  return (
    <div className="space-y-4">
      <Card
        title="调度中心 — 后台作业管控"
        extra={
          <Button icon={<ReloadOutlined />} onClick={loadJobs} loading={loading}>
            刷新
          </Button>
        }
      >
        <Table<JobDefinition>
          rowKey="jobCode"
          loading={loading}
          columns={columns}
          dataSource={jobs}
          pagination={{
            defaultPageSize: 10,
            pageSizeOptions: ['10', '20', '50', '100'],
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 个调度任务`,
          }}
        />
      </Card>

      <Drawer
        title={`作业执行历史 — ${selectedJob?.jobName ?? ''}`}
        width={720}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        extra={
          selectedJob && (
            <Button
              icon={<ReloadOutlined />}
              size="small"
              onClick={() => handleViewExecutions(selectedJob)}
              loading={executionsLoading}
            >
              刷新记录
            </Button>
          )
        }
      >
        <Table<JobExecutionLog>
          rowKey="id"
          loading={executionsLoading}
          columns={executionColumns}
          dataSource={executions}
          pagination={{
            defaultPageSize: 10,
            pageSizeOptions: ['10', '20', '50', '100'],
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条执行记录`,
          }}
        />
      </Drawer>
    </div>
  )
}
