import { useCallback, useEffect, useState } from 'react'
import {
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  createSodPolicy,
  evaluateSodConflict,
  listSodPolicies,
  updateSodPolicyStatus,
} from '@/api/identity/sodPolicies'
import { resolveErrorMessage } from '@/api/client/errors'
import { usePageSchema } from '@/hooks/useServerMetadata'
import { useAuthStore } from '@/stores/auth'
import type {
  CreateSodPolicyRequest,
  SodConflictEvaluateResult,
  SodPolicy,
  SodSeverity,
} from '@/types/identity'

const SEVERITY_COLORS: Record<SodSeverity, string> = {
  CRITICAL: 'red',
  HIGH: 'volcano',
  MEDIUM: 'gold',
}

export default function SodPoliciesPage() {
  const { message } = App.useApp()
  const tenantId = useAuthStore((s) => s.user?.tenantId)
  const { hasOperation } = usePageSchema('authz-sod-policies')

  const [policies, setPolicies] = useState<SodPolicy[]>([])
  const [loading, setLoading] = useState(false)

  const [createOpen, setCreateOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<CreateSodPolicyRequest>()

  // 角色组合冲突模拟器
  const [simRoleIdsInput, setSimRoleIdsInput] = useState('2001, 2002')
  const [simResult, setSimResult] = useState<SodConflictEvaluateResult | null>(null)
  const [simulating, setSimulating] = useState(false)

  const fetchPolicies = useCallback(async () => {
    if (!tenantId) return
    setLoading(true)
    try {
      const list = await listSodPolicies(tenantId)
      setPolicies(list)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }, [tenantId, message])

  useEffect(() => {
    void fetchPolicies()
  }, [fetchPolicies])

  const handleCreate = async () => {
    if (!tenantId) return
    try {
      const values = await form.validateFields()
      setSubmitting(true)
      const created = await createSodPolicy({
        ...values,
        tenantId,
      })
      message.success(`职责分离策略 ${created.policyCode} 创建成功`)
      setCreateOpen(false)
      form.resetFields()
      setPolicies((prev) => [created, ...prev])
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const handleToggleStatus = async (record: SodPolicy, checked: boolean) => {
    if (!tenantId) return
    const nextStatus = checked ? 'ACTIVE' : 'DISABLED'
    try {
      const updated = await updateSodPolicyStatus(record.id, tenantId, nextStatus)
      setPolicies((prev) => prev.map((item) => (item.id === updated.id ? updated : item)))
      message.success(`策略 ${record.policyCode} 已${checked ? '启用' : '停用'}`)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const handleSimulate = async () => {
    if (!tenantId) return
    const roleIds = simRoleIdsInput
      .split(/[,，\s]+/)
      .map((s) => s.trim())
      .filter(Boolean)
    if (roleIds.length === 0) {
      message.warning('请输入至少一个待检测角色 ID')
      return
    }
    setSimulating(true)
    try {
      const res = await evaluateSodConflict(tenantId, roleIds)
      setSimResult(res)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSimulating(false)
    }
  }

  const columns: ColumnsType<SodPolicy> = [
    {
      title: '策略编码',
      dataIndex: 'policyCode',
      width: 170,
      render: (code: string) => <Typography.Text code>{code}</Typography.Text>,
    },
    {
      title: '策略名称',
      dataIndex: 'policyName',
      width: 190,
    },
    {
      title: '互斥角色集合 (Role IDs)',
      dataIndex: 'conflictingRoleIds',
      render: (ids: string[]) => (
        <Space wrap size={4}>
          {(ids ?? []).map((id) => (
            <Tag key={id} color="purple">
              Role #{id}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '允许兼任上限',
      dataIndex: 'maxAllowedRoles',
      width: 120,
      render: (max: number) => `≤ ${max} 个`,
    },
    {
      title: '控制模式',
      dataIndex: 'enforcementMode',
      width: 120,
      render: (mode: string) => (
        <Tag color={mode === 'STATIC' ? 'blue' : 'cyan'}>
          {mode === 'STATIC' ? '静态赋权拦截' : '动态会话拦截'}
        </Tag>
      ),
    },
    {
      title: '风险等级',
      dataIndex: 'severity',
      width: 100,
      render: (sev: SodSeverity) => <Tag color={SEVERITY_COLORS[sev] ?? 'default'}>{sev}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: string, record) => (
        <Switch
          checked={status === 'ACTIVE'}
          checkedChildren="启用"
          unCheckedChildren="停用"
          onChange={(checked) => void handleToggleStatus(record, checked)}
        />
      ),
    },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-800">
            高级职责分离
          </h1>
          <p className="mt-1 text-sm text-slate-400">
            防止高危互斥角色（如「付款制单员」与「付款复核员」）被同一用户同时持有或激活
          </p>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void fetchPolicies()} loading={loading}>
            刷新
          </Button>
          {hasOperation('create') && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                form.setFieldsValue({
                  maxAllowedRoles: 1,
                  enforcementMode: 'STATIC',
                  severity: 'CRITICAL',
                })
                setCreateOpen(true)
              }}
            >
              新建 SoD 互斥策略
            </Button>
          )}
        </Space>
      </div>

      <Row gutter={16}>
        <Col xs={24} lg={16}>
          <Card title="职责分离约束规则" className="rounded-2xl">
            <Table<SodPolicy>
              rowKey="id"
              size="middle"
              columns={columns}
              dataSource={policies}
              loading={loading}
              scroll={{ x: 900 }}
              pagination={{
                defaultPageSize: 10,
                pageSizeOptions: ['10', '20', '50', '100'],
                showSizeChanger: true,
                showTotal: (total) => `共 ${total} 条策略`,
              }}
              locale={{ emptyText: <Empty description="暂无职责分离策略" /> }}
            />
          </Card>
        </Col>

        <Col xs={24} lg={8}>
          <Card
            title="角色组合互斥预检器"
            className="rounded-2xl"
            extra={
              <Button
                type="primary"
                size="small"
                icon={<SafetyCertificateOutlined />}
                loading={simulating}
                onClick={() => void handleSimulate()}
              >
                检测冲突
              </Button>
            }
          >
            <div className="space-y-3">
              <div className="text-xs text-slate-500">
                输入拟授予同一用户的多个角色 ID（逗号分隔），验证是否触发 SoD 互斥阻断：
              </div>
              <Input
                value={simRoleIdsInput}
                onChange={(e) => setSimRoleIdsInput(e.target.value)}
                placeholder="例如：2001, 2002"
              />
              {simResult && (
                <Alert
                  type={simResult.violated ? 'error' : 'success'}
                  showIcon
                  message={
                    simResult.violated
                      ? `检测到职责分离冲突：${simResult.violatedPolicyName} (${simResult.violatedPolicyCode})`
                      : '通过校验：未触发任何启用中的 SoD 互斥规则'
                  }
                  description={
                    simResult.violated
                      ? `冲突角色集合：${simResult.conflictingRoleIds.join(', ')}`
                      : '该角色集合可安全授予同一用户。'
                  }
                />
              )}
            </div>
          </Card>
        </Col>
      </Row>

      <Drawer
        title="新建职责分离策略"
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        width={560}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setCreateOpen(false)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={() => void handleCreate()}>
              创建策略
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="policyCode"
            label="策略编码"
            rules={[{ required: true, message: '请输入策略编码' }]}
          >
            <Input placeholder="例如：SOD_FINANCE_PAYMENT_MAKER_CHECKER" />
          </Form.Item>
          <Form.Item
            name="policyName"
            label="策略名称"
            rules={[{ required: true, message: '请输入策略名称' }]}
          >
            <Input placeholder="例如：资金付款制单与复核互斥" />
          </Form.Item>
          <Form.Item
            name="conflictingRoleIds"
            label="互斥角色 ID 列表"
            rules={[{ required: true, message: '请输入至少两个互斥角色 ID' }]}
          >
            <Select
              mode="tags"
              placeholder="输入角色 ID 后回车，例如 2001、2002"
              tokenSeparators={[',', ' ']}
            />
          </Form.Item>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="maxAllowedRoles" label="最大兼任数" rules={[{ required: true }]}>
                <InputNumber min={1} max={5} className="w-full" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="enforcementMode" label="控制模式" rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: 'STATIC', label: 'STATIC (赋权阻断)' },
                    { value: 'DYNAMIC', label: 'DYNAMIC (会话阻断)' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="severity" label="严重程度" rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: 'CRITICAL', label: 'CRITICAL' },
                    { value: 'HIGH', label: 'HIGH' },
                    { value: 'MEDIUM', label: 'MEDIUM' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="策略说明">
            <Input.TextArea rows={2} placeholder="可选合规条款说明" />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
