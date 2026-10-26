import { ApartmentOutlined, ThunderboltOutlined } from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  Select,
  Tag,
  Typography,
} from 'antd'
import { useState } from 'react'
import { resolveErrorMessage } from '@/api/client/errors'
import { bootstrapTenant } from '@/api/identity/tenant'
import type { IdentityType } from '@/api/client/auth'
import type { TenantBootstrapResponse } from '@/types/identity'

const TENANT_CODE_PATTERN = /^[a-z][a-z0-9-]{2,63}$/
const PASSWORD_MIN_LENGTH = 15

const IDENTITY_TYPE_OPTIONS = [
  { value: 'USERNAME', label: '用户名' },
  { value: 'EMAIL', label: '邮箱' },
  { value: 'PHONE', label: '手机号' },
]

interface BootstrapFormValues {
  tenantCode: string
  tenantName: string
  identityType: IdentityType
  identity: string
  displayName: string
  initialPassword: string
}

function renderId(value: string) {
  return (
    <Typography.Text copyable={{ text: value }} className="font-mono text-xs">
      {value}
    </Typography.Text>
  )
}

export default function TenantBootstrapPage() {
  const { message } = App.useApp()
  const [form] = Form.useForm<BootstrapFormValues>()
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<TenantBootstrapResponse | null>(null)

  const handleFinish = async () => {
    try {
      await form.validateFields()
    } catch {
      return
    }
    const values = form.getFieldsValue()
    setSubmitting(true)
    try {
      const data = await bootstrapTenant({
        tenantCode: values.tenantCode,
        tenantName: values.tenantName,
        initialAdmin: {
          identityType: values.identityType,
          identity: values.identity,
          displayName: values.displayName,
          initialPassword: values.initialPassword,
        },
      })
      setResult(data)
      message.success('租户引导请求已受理（202）')
      form.resetFields()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div>
        <h1 className="text-xl font-semibold text-slate-800">租户引导</h1>
        <p className="mt-1 text-sm text-slate-400">
          初始化新租户并创建初始管理员
        </p>
      </div>

      <Alert
        type="info"
        showIcon
        message="引导说明"
        description={
          <ul className="list-disc pl-4 text-xs leading-5 text-slate-500">
            <li>系统为每次提交自动生成 Idempotency-Key，重复提交不会重复建租户。</li>
            <li>返回 202 表示已受理：租户异步初始化，可凭返回的租户ID轮询租户资源状态。</li>
          </ul>
        }
        className="rounded-xl"
      />

      <Card className="rounded-2xl">
        <Form form={form} layout="vertical" requiredMark={false} onFinish={handleFinish}>
          <Form.Item
            name="tenantCode"
            label="租户编码"
            extra="小写字母开头，仅含小写字母 / 数字 / 连字符，3-64 位"
            rules={[
              { required: true, message: '请输入租户编码' },
              { pattern: TENANT_CODE_PATTERN, message: '格式：小写字母开头，3-64 位小写字母 / 数字 / 连字符' },
            ]}
          >
            <Input
              prefix={<ApartmentOutlined className="text-slate-300" />}
              placeholder="例如：acme-corp"
              maxLength={64}
            />
          </Form.Item>
          <Form.Item
            name="tenantName"
            label="租户名称"
            rules={[{ required: true, message: '请输入租户名称' }]}
          >
            <Input placeholder="例如：Acme 集团" maxLength={128} />
          </Form.Item>

          <div className="mb-1 mt-2 text-sm font-medium text-slate-600">初始管理员</div>
          <Form.Item name="identityType" label="登录方式" initialValue="USERNAME">
            <Select options={IDENTITY_TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item
            name="identity"
            label="登录身份"
            rules={[{ required: true, message: '请输入登录身份' }]}
          >
            <Input placeholder="用户名 / 邮箱 / 手机号" maxLength={320} />
          </Form.Item>
          <Form.Item
            name="displayName"
            label="管理员昵称"
            rules={[{ required: true, message: '请输入管理员昵称' }]}
          >
            <Input placeholder="例如：超级管理员" maxLength={128} />
          </Form.Item>
          <Form.Item
            name="initialPassword"
            label="初始密码"
            extra={`至少 ${PASSWORD_MIN_LENGTH} 位，提交后不会在页面回显，请线下妥善交付`}
            rules={[
              { required: true, message: '请输入初始密码' },
              { min: PASSWORD_MIN_LENGTH, message: `初始密码至少 ${PASSWORD_MIN_LENGTH} 位` },
            ]}
          >
            <Input.Password placeholder={`至少 ${PASSWORD_MIN_LENGTH} 位`} autoComplete="new-password" maxLength={128} />
          </Form.Item>

          <Form.Item className="mb-0">
            <Button type="primary" htmlType="submit" loading={submitting} icon={<ThunderboltOutlined />}>
              发起引导
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {result && (
        <Card title="引导结果" className="rounded-2xl">
          <Alert
            type="success"
            showIcon
            message="租户已受理初始化（202 Accepted）"
            description="租户正在异步初始化，可凭以下 ID 轮询租户资源状态。"
            className="rounded-xl"
          />
          <Descriptions column={1} className="mt-4" size="small" bordered>
            <Descriptions.Item label="租户ID">{renderId(result.tenantId)}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color="gold">初始化中（INITIALIZING）</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="操作ID">{renderId(result.operationId)}</Descriptions.Item>
          </Descriptions>
        </Card>
      )}
    </div>
  )
}
