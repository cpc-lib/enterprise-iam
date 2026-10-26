import { LockOutlined, ApartmentOutlined, UserOutlined } from '@ant-design/icons'
import { App, Button, Form, Input, Select } from 'antd'
import { useNavigate, useSearchParams } from 'react-router-dom'
import type { IdentityType } from '@/api/client/auth'
import { useAuthStore } from '@/stores/auth'

interface LoginFormValues {
  tenantCode: string
  identityType: IdentityType
  identity: string
  password: string
}

const IDENTITY_TYPE_OPTIONS = [
  { value: 'USERNAME', label: '用户名' },
  { value: 'EMAIL', label: '邮箱' },
  { value: 'PHONE', label: '手机号' },
]

export default function LoginPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { message } = App.useApp()
  const [form] = Form.useForm<LoginFormValues>()

  const status = useAuthStore((s) => s.status)
  const login = useAuthStore((s) => s.login)

  const handleFinish = async (values: LoginFormValues) => {
    const ok = await login(values)
    if (ok) {
      message.success('登录成功')
      const redirect = searchParams.get('redirect')
      navigate(redirect && redirect.startsWith('/') ? redirect : '/dashboard', { replace: true })
    } else {
      message.error(useAuthStore.getState().error ?? '登录失败，请稍后重试')
    }
  }

  return (
    <Form<LoginFormValues>
      form={form}
      layout="vertical"
      requiredMark={false}
      initialValues={{ identityType: 'USERNAME' }}
      onFinish={handleFinish}
    >
      <Form.Item
        name="tenantCode"
        label="租户编码"
        rules={[{ required: true, message: '请输入租户编码' }]}
      >
        <Input
          prefix={<ApartmentOutlined className="text-slate-300" />}
          placeholder="例如：acme-corp"
          size="large"
          autoComplete="organization"
        />
      </Form.Item>
      <Form.Item name="identityType" label="登录方式">
        <Select options={IDENTITY_TYPE_OPTIONS} />
      </Form.Item>
      <Form.Item
        name="identity"
        label="登录账号"
        rules={[{ required: true, message: '请输入登录账号' }]}
      >
        <Input
          prefix={<UserOutlined className="text-slate-300" />}
          placeholder="请输入登录账号"
          size="large"
          autoComplete="username"
        />
      </Form.Item>
      <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
        <Input.Password
          prefix={<LockOutlined className="text-slate-300" />}
          placeholder="请输入密码"
          size="large"
          autoComplete="current-password"
        />
      </Form.Item>
      <Form.Item className="mb-2">
        <Button type="primary" htmlType="submit" size="large" block loading={status === 'loading'}>
          登 录
        </Button>
      </Form.Item>
      <div className="mt-2 text-center">
        <Button type="link" size="small" disabled>
          注册新账号（请联系企业管理员开通）
        </Button>
      </div>
    </Form>
  )
}
