import { useEffect, useMemo, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import {
  KeyOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  App,
  Avatar,
  Breadcrumb,
  Button,
  Dropdown,
  Form,
  Input,
  Layout,
  Menu,
  Modal,
  Space,
  Tag,
} from 'antd'
import type { MenuProps } from 'antd'
import { useAuthStore } from '@/stores/auth'
import { buildMenuItems, flattenNavItems, getActiveKey, useNavigationStore } from '@/stores/navigation'
import { useUiStore } from '@/stores/ui'

const { Sider, Header, Content } = Layout

/** 修改密码：始终使用弹窗（产品偏好：不在页面上内联展示密码表单） */
function ChangePasswordModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const changePassword = useAuthStore((s) => s.changePassword)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<{
    currentPassword: string
    newPassword: string
    confirmPassword: string
  }>()

  const handleClose = () => {
    form.resetFields()
    onClose()
  }

  const handleOk = async () => {
    let values: { currentPassword: string; newPassword: string; confirmPassword: string }
    try {
      values = await form.validateFields()
    } catch {
      return
    }

    setSubmitting(true)
    try {
      const result = await changePassword({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      })
      if (result.outcome === 'failed') {
        message.error(result.message)
        return
      }
      handleClose()
      if (result.outcome === 'reauth-required') {
        message.warning('密码已修改，请重新登录')
        navigate('/login', { replace: true })
        return
      }
      message.success('密码已修改，其他会话已退出')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title="修改密码"
      open={open}
      onOk={handleOk}
      onCancel={handleClose}
      okText="确认修改"
      cancelText="取消"
      confirmLoading={submitting}
      destroyOnClose
    >
      <Form form={form} layout="vertical" className="pt-2">
        <Form.Item
          name="currentPassword"
          label="当前密码"
          rules={[{ required: true, message: '请输入当前密码' }]}
        >
          <Input.Password placeholder="请输入当前密码" autoComplete="current-password" />
        </Form.Item>
        <Form.Item
          name="newPassword"
          label="新密码"
          dependencies={['currentPassword']}
          rules={[
            { required: true, message: '请输入新密码' },
            ({ getFieldValue }) => ({
              validator(_, value: string | undefined) {
                if (!value) return Promise.resolve()
                const normalized = value.normalize('NFC')
                const length = Array.from(normalized).length
                if (length < 8 || length > 128) {
                  return Promise.reject(new Error('新密码长度须为 8–128 个字符'))
                }
                if (normalized === String(getFieldValue('currentPassword') ?? '').normalize('NFC')) {
                  return Promise.reject(new Error('新密码不能与当前密码相同'))
                }
                return Promise.resolve()
              },
            }),
          ]}
        >
          <Input.Password placeholder="8–128 个字符" autoComplete="new-password" />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          label="确认新密码"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: '请再次输入新密码' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (
                  !value ||
                  String(getFieldValue('newPassword') ?? '').normalize('NFC') ===
                    String(value).normalize('NFC')
                ) {
                  return Promise.resolve()
                }
                return Promise.reject(new Error('两次输入的密码不一致'))
              },
            }),
          ]}
        >
          <Input.Password placeholder="请再次输入新密码" autoComplete="new-password" />
        </Form.Item>
      </Form>
    </Modal>
  )
}

export function BasicLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const { modal } = App.useApp()

  const collapsed = useUiStore((s) => s.siderCollapsed)
  const toggleSider = useUiStore((s) => s.toggleSider)

  const schema = useNavigationStore((s) => s.schema)
  const loadNavigation = useNavigationStore((s) => s.load)

  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)

  const [passwordModalOpen, setPasswordModalOpen] = useState(false)

  useEffect(() => {
    loadNavigation()
  }, [loadNavigation])

  const menuItems = useMemo(() => buildMenuItems(schema), [schema])
  const flatItems = useMemo(() => flattenNavItems(schema), [schema])
  const activeKey = getActiveKey(schema, location.pathname) ?? location.pathname
  const currentItem = flatItems.find((item) => item.path === location.pathname)

  // 当登录用户无仪表盘权限且处于首页或仪表盘时，自动平滑重定向到其首个可用功能菜单
  useEffect(() => {
    if (flatItems.length > 0) {
      const hasDashboard = flatItems.some((item) => item.path === '/dashboard')
      if (!hasDashboard && (location.pathname === '/' || location.pathname === '/dashboard')) {
        navigate(flatItems[0].path, { replace: true })
      }
    }
  }, [flatItems, location.pathname, navigate])

  const handleMenuClick: MenuProps['onClick'] = (info) => {
    const target = flatItems.find((item) => item.key === info.key)
    if (target && target.path !== location.pathname) {
      navigate(target.path)
    }
  }

  const handleLogout = () => {
    modal.confirm({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      okText: '退出',
      cancelText: '取消',
      onOk: async () => {
        await logout()
        navigate('/login', { replace: true })
      },
    })
  }

  const userMenuItems: MenuProps['items'] = [
    { key: 'change-password', icon: <KeyOutlined />, label: '修改密码' },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', danger: true },
  ]

  const handleUserMenuClick: MenuProps['onClick'] = (info) => {
    if (info.key === 'change-password') {
      setPasswordModalOpen(true)
    }
    if (info.key === 'logout') {
      handleLogout()
    }
  }

  const breadcrumbItems = currentItem
    ? currentItem.groupTitle
      ? [{ title: currentItem.groupTitle }, { title: currentItem.title }]
      : [{ title: currentItem.title }]
    : [{ title: '工作台' }]

  return (
    <Layout className="h-screen overflow-hidden">
      <Sider
        theme="light"
        width={224}
        collapsed={collapsed}
        className="border-r border-slate-100 flex flex-col h-full"
      >
        <div className="flex h-16 shrink-0 items-center gap-2 overflow-hidden px-4 border-b border-slate-100">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-indigo-600 text-white">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="h-5 w-5"
            >
              <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
              <path d="m9 12 2 2 4-4" />
            </svg>
          </div>
          {!collapsed && (
            <span className="whitespace-nowrap text-base font-semibold text-slate-800">
              IAM 控制台
            </span>
          )}
        </div>
        <div className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden">
          <Menu
            mode="inline"
            items={menuItems}
            selectedKeys={[activeKey]}
            onClick={handleMenuClick}
            className="border-r-0"
          />
        </div>
      </Sider>
      <Layout className="min-w-0 flex-1 flex flex-col overflow-hidden bg-slate-50">
        <Header className="flex shrink-0 items-center justify-between border-b border-slate-100 bg-white">
          <div className="flex items-center gap-3">
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={toggleSider}
              aria-label="切换侧边栏"
            />
            <Breadcrumb items={breadcrumbItems} />
          </div>
          <Space size={12}>
            {user?.tenantCode && <Tag color="blue">{user.tenantCode}</Tag>}
            <Button
              type="text"
              icon={<KeyOutlined />}
              onClick={() => setPasswordModalOpen(true)}
              className="text-slate-600 hover:text-blue-600"
            >
              修改密码
            </Button>
            <Dropdown
              menu={{ items: userMenuItems, onClick: handleUserMenuClick }}
              placement="bottomRight"
            >
              <button
                type="button"
                className="flex cursor-pointer items-center gap-2 rounded-lg px-2 py-1 transition hover:bg-slate-50"
              >
                <Avatar size={28} icon={<UserOutlined />} style={{ backgroundColor: '#2563EB' }} />
                <span className="text-sm text-slate-700">{user?.displayName ?? user?.tenantCode ?? '未登录'}</span>
              </button>
            </Dropdown>
          </Space>
        </Header>
        <Content className="min-h-0 flex-1 overflow-auto">
          <div className="p-6 min-w-0">
            <Outlet />
          </div>
        </Content>
      </Layout>
      <ChangePasswordModal
        open={passwordModalOpen}
        onClose={() => setPasswordModalOpen(false)}
      />
    </Layout>
  )
}
