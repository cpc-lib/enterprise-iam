import { useCallback, useEffect, useState } from 'react'
import {
  DeleteOutlined,
  ExperimentOutlined,
  PlusOutlined,
  ReloadOutlined,
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
  createAbacPolicy,
  deleteAbacPolicy,
  evaluateAbacCondition,
  listAbacPolicies,
  updateAbacPolicyStatus,
} from '@/api/authorization/abacPolicies'
import { resolveErrorMessage } from '@/api/client/errors'
import { usePageSchema } from '@/hooks/useServerMetadata'
import { useAuthStore } from '@/stores/auth'
import type {
  AbacConditionEvaluationResult,
  AbacEffect,
  AbacPolicy,
  CreateAbacPolicyRequest,
} from '@/types/authorization'

interface VisualClause {
  leftAttr: string
  operator: string
  rightValue: string
}

const PRESET_ATTRIBUTES = [
  { value: 'subject.department', label: '主体部门 (subject.department)' },
  { value: 'subject.clearanceLevel', label: '主体密级 (subject.clearanceLevel)' },
  { value: 'resource.department', label: '资源所属部门 (resource.department)' },
  { value: 'resource.sensitivityLevel', label: '资源敏感等级 (resource.sensitivityLevel)' },
  { value: 'context.mfaVerified', label: '已完成多因素认证 (context.mfaVerified)' },
  { value: 'context.environment', label: '访问环境 (context.environment)' },
]

export default function AbacPoliciesPage() {
  const { message } = App.useApp()
  const tenantId = useAuthStore((s) => s.user?.tenantId)
  const { hasOperation } = usePageSchema('authz-abac-policies')

  const [policies, setPolicies] = useState<AbacPolicy[]>([])
  const [loading, setLoading] = useState(false)

  const [createOpen, setCreateOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<CreateAbacPolicyRequest>()

  // 可视化条件构建器状态
  const [clauses, setClauses] = useState<VisualClause[]>([
    { leftAttr: 'subject.department', operator: '==', rightValue: "'FINANCE'" },
    { leftAttr: 'context.mfaVerified', operator: '==', rightValue: 'true' },
  ])
  const [combinator, setCombinator] = useState<'&&' | '||'>('&&')

  // DSL 沙箱演练状态
  const [sandboxExpr, setSandboxExpr] = useState(
    "subject.department == 'FINANCE' && context.mfaVerified == true && subject.clearanceLevel >= 3",
  )
  const [sandboxContextJson, setSandboxContextJson] = useState(
    JSON.stringify(
      {
        'subject.department': 'FINANCE',
        'context.mfaVerified': true,
        'subject.clearanceLevel': 4,
      },
      null,
      2,
    ),
  )
  const [evalResult, setEvalResult] = useState<AbacConditionEvaluationResult | null>(null)
  const [evaluating, setEvaluating] = useState(false)

  const fetchPolicies = useCallback(async () => {
    if (!tenantId) return
    setLoading(true)
    try {
      const list = await listAbacPolicies(tenantId)
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

  const buildExpressionFromClauses = (nextClauses: VisualClause[], nextCombinator: '&&' | '||') => {
    const expr = nextClauses
      .filter((c) => c.leftAttr && c.operator && c.rightValue)
      .map((c) => `${c.leftAttr} ${c.operator} ${c.rightValue}`)
      .join(` ${nextCombinator} `)
    form.setFieldValue('conditionExpr', expr)
  }

  const handleCreate = async () => {
    if (!tenantId) return
    try {
      const values = await form.validateFields()
      setSubmitting(true)
      const created = await createAbacPolicy({
        ...values,
        tenantId,
      })
      message.success(`ABAC 策略 ${created.policyCode} 创建成功`)
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

  const handleToggleStatus = async (record: AbacPolicy, checked: boolean) => {
    if (!tenantId) return
    const nextStatus = checked ? 'ACTIVE' : 'DISABLED'
    try {
      const updated = await updateAbacPolicyStatus(record.id, tenantId, nextStatus)
      setPolicies((prev) => prev.map((item) => (item.id === updated.id ? updated : item)))
      message.success(`策略 ${record.policyCode} 已${checked ? '启用' : '停用'}`)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const handleDelete = async (record: AbacPolicy) => {
    if (!tenantId) return
    try {
      await deleteAbacPolicy(record.id, tenantId)
      setPolicies((prev) => prev.filter((item) => item.id !== record.id))
      message.success(`策略 ${record.policyCode} 已删除`)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const handleRunSandbox = async () => {
    try {
      const parsed = JSON.parse(sandboxContextJson) as Record<string, unknown>
      setEvaluating(true)
      const res = await evaluateAbacCondition(sandboxExpr, parsed)
      setEvalResult(res)
    } catch (error) {
      message.error(`上下文 JSON 或求值异常: ${resolveErrorMessage(error)}`)
    } finally {
      setEvaluating(false)
    }
  }

  const columns: ColumnsType<AbacPolicy> = [
    {
      title: '策略编码',
      dataIndex: 'policyCode',
      width: 170,
      render: (code: string) => <Typography.Text code>{code}</Typography.Text>,
    },
    {
      title: '策略名称',
      dataIndex: 'policyName',
      width: 180,
    },
    {
      title: '资源与操作',
      key: 'target',
      width: 170,
      render: (_, r) => (
        <Space size={4}>
          <Tag color="geekblue">{r.resourceType}</Tag>
          <Tag color="cyan">{r.operation}</Tag>
        </Space>
      ),
    },
    {
      title: '效果 (Effect)',
      dataIndex: 'effect',
      width: 110,
      render: (effect: AbacEffect) => (
        <Tag color={effect === 'ALLOW' ? 'success' : 'error'}>{effect}</Tag>
      ),
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      width: 90,
    },
    {
      title: '条件表达式 (Bounded DSL)',
      dataIndex: 'conditionExpr',
      ellipsis: true,
      render: (expr: string) => <Typography.Text code>{expr}</Typography.Text>,
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
    {
      title: '操作',
      key: 'actions',
      width: 90,
      render: (_, record) => (
        <Button
          type="link"
          danger
          size="small"
          icon={<DeleteOutlined />}
          onClick={() => void handleDelete(record)}
        >
          删除
        </Button>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-800">
            ABAC 可视化策略构建器与条件引擎
          </h1>
          <p className="mt-1 text-sm text-slate-400">
            基于受限安全 AST 条件表达式（DEC-014 复杂度边界：深度 ≤ 8、Token ≤ 64，异常默认 Fail-Closed）
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
                  effect: 'ALLOW',
                  priority: 100,
                  resourceType: 'DOCUMENT',
                  operation: 'READ',
                  conditionExpr:
                    "subject.department == 'FINANCE' && context.mfaVerified == true",
                })
                setCreateOpen(true)
              }}
            >
              新建 ABAC 策略
            </Button>
          )}
        </Space>
      </div>

      <Row gutter={16}>
        <Col xs={24} lg={15}>
          <Card title="已注册 ABAC 条件策略" className="rounded-2xl">
            <Table<AbacPolicy>
              rowKey="id"
              size="middle"
              columns={columns}
              dataSource={policies}
              loading={loading}
              scroll={{ x: 800 }}
              pagination={{
                defaultPageSize: 10,
                pageSizeOptions: ['10', '20', '50', '100'],
                showSizeChanger: true,
                showTotal: (total) => `共 ${total} 条策略`,
              }}
              locale={{ emptyText: <Empty description="暂无 ABAC 条件策略" /> }}
            />
          </Card>
        </Col>

        <Col xs={24} lg={9}>
          <Card
            title="Condition DSL 实时演练沙箱"
            className="rounded-2xl"
            extra={
              <Button
                type="primary"
                size="small"
                icon={<ExperimentOutlined />}
                loading={evaluating}
                onClick={() => void handleRunSandbox()}
              >
                执行求值
              </Button>
            }
          >
            <div className="space-y-3">
              <div>
                <div className="mb-1 text-xs font-medium text-slate-500">条件表达式</div>
                <Input.TextArea
                  rows={2}
                  value={sandboxExpr}
                  onChange={(e) => setSandboxExpr(e.target.value)}
                  className="font-mono text-xs"
                />
              </div>
              <div>
                <div className="mb-1 text-xs font-medium text-slate-500">
                  请求上下文属性 (JSON)
                </div>
                <Input.TextArea
                  rows={6}
                  value={sandboxContextJson}
                  onChange={(e) => setSandboxContextJson(e.target.value)}
                  className="font-mono text-xs"
                />
              </div>
              {evalResult && (
                <Alert
                  type={evalResult.valid && evalResult.matched ? 'success' : 'warning'}
                  showIcon
                  message={
                    evalResult.valid
                      ? `语法合法 · 求值结果: ${evalResult.matched ? 'MATCHED (命中)' : 'NOT_MATCHED (未命中)'}`
                      : '表达式超出安全复杂度或语法非法 (Fail-Closed)'
                  }
                  description={`Token 数量: ${evalResult.tokenCount} / 64 · 嵌套深度: ${evalResult.depth} / 8 · 详情: ${evalResult.detail}`}
                />
              )}
            </div>
          </Card>
        </Col>
      </Row>

      <Drawer
        title="新建 ABAC 策略（可视化构建器）"
        open={createOpen}
        width={720}
        onClose={() => setCreateOpen(false)}
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
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="policyCode"
                label="策略编码"
                rules={[{ required: true, message: '请输入策略编码' }]}
              >
                <Input placeholder="例如：ABAC_FINANCE_MFA_READ" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="policyName"
                label="策略名称"
                rules={[{ required: true, message: '请输入策略名称' }]}
              >
                <Input placeholder="例如：财务部门高敏文档 MFA 访问策略" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={12}>
            <Col span={8}>
              <Form.Item
                name="resourceType"
                label="资源类型"
                rules={[{ required: true, message: '请输入资源类型' }]}
              >
                <Input placeholder="例如：DOCUMENT /CUSTOMER" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="operation"
                label="操作编码"
                rules={[{ required: true, message: '请输入操作编码' }]}
              >
                <Input placeholder="例如：READ / EXPORT" />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="effect" label="决策效果" rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: 'ALLOW', label: 'ALLOW' },
                    { value: 'DENY', label: 'DENY' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="priority" label="优先级" rules={[{ required: true }]}>
                <InputNumber min={1} max={1000} className="w-full" />
              </Form.Item>
            </Col>
          </Row>

          <Card
            size="small"
            title="可视化条件规则组合器"
            className="mb-4 bg-slate-50"
            extra={
              <Space>
                <Select
                  size="small"
                  value={combinator}
                  onChange={(val) => {
                    setCombinator(val)
                    buildExpressionFromClauses(clauses, val)
                  }}
                  options={[
                    { value: '&&', label: '全部满足 (AND / &&)' },
                    { value: '||', label: '任一满足 (OR / ||)' },
                  ]}
                />
                <Button
                  size="small"
                  onClick={() => {
                    const next = [
                      ...clauses,
                      { leftAttr: 'subject.clearanceLevel', operator: '>=', rightValue: '3' },
                    ]
                    setClauses(next)
                    buildExpressionFromClauses(next, combinator)
                  }}
                >
                  + 添加条件子句
                </Button>
              </Space>
            }
          >
            <div className="space-y-2">
              {clauses.map((clause, idx) => (
                <div key={idx} className="flex items-center gap-2">
                  <Select
                    className="w-64"
                    value={clause.leftAttr}
                    options={PRESET_ATTRIBUTES}
                    onChange={(val) => {
                      const next = clauses.map((c, i) => (i === idx ? { ...c, leftAttr: val } : c))
                      setClauses(next)
                      buildExpressionFromClauses(next, combinator)
                    }}
                  />
                  <Select
                    className="w-28"
                    value={clause.operator}
                    options={[
                      { value: '==', label: '==' },
                      { value: '!=', label: '!=' },
                      { value: '>=', label: '>=' },
                      { value: '<=', label: '<=' },
                      { value: '>', label: '>' },
                      { value: '<', label: '<' },
                    ]}
                    onChange={(val) => {
                      const next = clauses.map((c, i) => (i === idx ? { ...c, operator: val } : c))
                      setClauses(next)
                      buildExpressionFromClauses(next, combinator)
                    }}
                  />
                  <Input
                    className="flex-1"
                    value={clause.rightValue}
                    placeholder="字面量，如 'FINANCE' 或 true 或 3"
                    onChange={(e) => {
                      const next = clauses.map((c, i) =>
                        i === idx ? { ...c, rightValue: e.target.value } : c,
                      )
                      setClauses(next)
                      buildExpressionFromClauses(next, combinator)
                    }}
                  />
                  <Button
                    danger
                    size="small"
                    onClick={() => {
                      const next = clauses.filter((_, i) => i !== idx)
                      setClauses(next)
                      buildExpressionFromClauses(next, combinator)
                    }}
                  >
                    移除
                  </Button>
                </div>
              ))}
            </div>
          </Card>

          <Form.Item
            name="conditionExpr"
            label="生成的安全条件表达式 (Condition DSL)"
            rules={[{ required: true, message: '请输入或生成条件表达式' }]}
          >
            <Input.TextArea rows={2} className="font-mono text-xs" />
          </Form.Item>

          <Form.Item name="description" label="策略描述">
            <Input placeholder="可选说明" />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
