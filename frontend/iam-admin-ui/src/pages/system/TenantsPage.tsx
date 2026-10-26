import { useCallback, useEffect, useState } from 'react'
import {
  ApartmentOutlined,
  CheckCircleOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  StopOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { useNavigate } from 'react-router-dom'
import { resolveErrorMessage } from '@/api/client/errors'
import {
  disableTenant,
  enableTenant,
  getTenants,
  updateTenant,
} from '@/api/system/tenants'
import type { TenantItem } from '@/api/system/tenants'
import { dayjs } from '@/utils/dayjs'

const { Text } = Typography

const STATUS_TAG_MAP: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '正常' },
  DISABLED: { color: 'red', label: '已停用' },
  INITIALIZING: { color: 'blue', label: '初始化中' },
  SUSPENDED: { color: 'orange', label: '已冻结' },
}

interface FilterFormValues {
  keyword?: string
  status?: string
}

interface EditFormValues {
  tenantName: string
}

export default function TenantsPage() {
  const { message } = App.useApp()
  const navigate = useNavigate()

  const [tenants, setTenants] = useState<TenantItem[]>([])
  const [loading, setLoading] = useState(false)
  const [filterForm] = Form.useForm<FilterFormValues>()

  // 编辑弹窗
  const [editModalVisible, setEditModalVisible] = useState(false)
  const [editingTenant, setEditingTenant] = useState<TenantItem | null>(null)
  const [editSubmitting, setEditSubmitting] = useState(false)
  const [editForm] = Form.useForm<EditFormValues>()

  // 加载租户列表
  const loadTenants = useCallback(async () => {
    setLoading(true)
    try {
      const filters = filterForm.getFieldsValue()
      const data = await getTenants({
        keyword: filters.keyword?.trim() || undefined,
        status: filters.status || undefined,
      })
      setTenants(data)
    } catch (err) {
      message.error(resolveErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }, [filterForm, message])

  useEffect(() => {
    loadTenants()
  }, [loadTenants])

  // 打开编辑弹窗
  const handleOpenEdit = (record: TenantItem) => {
    setEditingTenant(record)
    editForm.setFieldsValue({
      tenantName: record.tenantName,
    })
    setEditModalVisible(true)
  }

  // 提交编辑
  const handleSaveEdit = async () => {
    try {
      const values = await editForm.validateFields()
      if (!editingTenant) return
      setEditSubmitting(true)
      await updateTenant(editingTenant.id, values.tenantName)
      message.success(`租户【${editingTenant.tenantCode}】信息修改成功`)
      setEditModalVisible(false)
      loadTenants()
    } catch (err) {
      message.error(resolveErrorMessage(err))
    } finally {
      setEditSubmitting(false)
    }
  }

  // 切换启用/停用
  const handleToggleStatus = async (record: TenantItem) => {
    if (record.id === '10001') {
      message.warning('系统运营平台为核心根租户，不可停用')
      return
    }
    const isEnabling = record.status === 'DISABLED'
    try {
      if (isEnabling) {
        await enableTenant(record.id)
        message.success(`租户【${record.tenantName}】已启用`)
      } else {
        await disableTenant(record.id)
        message.success(`租户【${record.tenantName}】已停用`)
      }
      loadTenants()
    } catch (err) {
      message.error(resolveErrorMessage(err))
    }
  }

  const columns: TableColumnsType<TenantItem> = [
    {
      title: '租户 ID',
      dataIndex: 'id',
      key: 'id',
      width: 140,
      render: (val: string) => (
        <Text copyable={{ text: val }} className="font-mono text-xs">
          {val}
        </Text>
      ),
    },
    {
      title: '租户编码',
      dataIndex: 'tenantCode',
      key: 'tenantCode',
      width: 160,
      render: (val: string) => <Tag color="blue" className="font-mono">{val}</Tag>,
    },
    {
      title: '租户名称',
      dataIndex: 'tenantName',
      key: 'tenantName',
      render: (val: string, record) => (
        <Space direction="horizontal" size="small">
          <Text strong>{val}</Text>
          {record.id === '10001' && (
            <Tag color="gold" bordered={false}>
              平台根租户
            </Tag>
          )}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (val: string) => {
        const meta = STATUS_TAG_MAP[val] ?? { color: 'default', label: val }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: '版本号',
      dataIndex: 'version',
      key: 'version',
      width: 90,
      align: 'center',
      render: (val: number) => <span className="font-mono text-xs text-gray-500">v{val}</span>,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (val: string) => (val ? dayjs(val).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 180,
      render: (val: string) => (val ? dayjs(val).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      render: (_, record) => {
        const isRoot = record.id === '10001'
        const isSuspendedOrDisabled = record.status === 'DISABLED'
        return (
          <Space size="middle">
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleOpenEdit(record)}
            >
              编辑
            </Button>

            {isRoot ? (
              <Tooltip title="平台根租户受安全保护，不可停用">
                <Button type="link" size="small" disabled icon={<StopOutlined />}>
                  停用
                </Button>
              </Tooltip>
            ) : isSuspendedOrDisabled ? (
              <Popconfirm
                title="启用租户"
                description={`确定要重新启用租户【${record.tenantName}】吗？`}
                okText="确认启用"
                cancelText="取消"
                onConfirm={() => handleToggleStatus(record)}
              >
                <Button type="link" size="small" icon={<CheckCircleOutlined />} className="text-green-600">
                  启用
                </Button>
              </Popconfirm>
            ) : (
              <Popconfirm
                title="停用租户"
                description={`确定要停用租户【${record.tenantName}】吗？停用后该租户下所有用户将无法登录。`}
                okText="确认停用"
                okButtonProps={{ danger: true }}
                cancelText="取消"
                onConfirm={() => handleToggleStatus(record)}
              >
                <Button type="link" size="small" danger icon={<StopOutlined />}>
                  停用
                </Button>
              </Popconfirm>
            )}

            <Button
              type="link"
              size="small"
              icon={<SafetyCertificateOutlined />}
              onClick={() => navigate(`/identity/roles?tenantId=${record.id}`)}
            >
              角色
            </Button>

            <Button
              type="link"
              size="small"
              icon={<TeamOutlined />}
              onClick={() => navigate(`/identity/users?tenantId=${record.id}`)}
            >
              用户
            </Button>
          </Space>
        )
      },
    },
  ]

  return (
    <div className="space-y-4">
      <Card bordered={false}>
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <ApartmentOutlined className="text-2xl text-blue-600" />
            <div>
              <h1 className="text-lg font-semibold text-gray-900 m-0">租户管理</h1>
              <p className="text-xs text-gray-500 m-0">
                平台超级管理员专属功能：集中管理入驻企业租户、配置租户生命周期及监管权限体系
              </p>
            </div>
          </div>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate('/system/tenant-bootstrap')}
          >
            初始化新租户
          </Button>
        </div>
      </Card>

      <Card bordered={false}>
        <Form form={filterForm} layout="inline" className="mb-4 flex flex-wrap items-center gap-3">
          <Form.Item name="keyword" label="关键字" className="mb-0">
            <Input
              placeholder="搜索租户 ID / 编码 / 名称"
              allowClear
              prefix={<SearchOutlined className="text-slate-400" />}
              onPressEnter={loadTenants}
              style={{ width: 240 }}
            />
          </Form.Item>
          <Form.Item name="status" label="租户状态" className="mb-0">
            <Select
              placeholder="全部状态"
              allowClear
              style={{ width: 140 }}
              options={[
                { value: 'ACTIVE', label: '正常' },
                { value: 'DISABLED', label: '已停用' },
                { value: 'INITIALIZING', label: '初始化中' },
                { value: 'SUSPENDED', label: '已冻结' },
              ]}
            />
          </Form.Item>
          <Form.Item className="mb-0">
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={loadTenants}>
                查询
              </Button>
              <Button
                onClick={() => {
                  filterForm.resetFields()
                  loadTenants()
                }}
              >
                重置
              </Button>
              <Button icon={<ReloadOutlined />} onClick={loadTenants}>
                刷新
              </Button>
            </Space>
          </Form.Item>
        </Form>

        <Table<TenantItem>
          rowKey="id"
          columns={columns}
          dataSource={tenants}
          loading={loading}
          pagination={{
            showQuickJumper: true,
            showSizeChanger: true,
            defaultPageSize: 10,
            pageSizeOptions: ['10', '20', '50', '100'],
            showTotal: (total) => `共 ${total} 个租户`,
          }}
        />
      </Card>

      {/* 编辑租户抽屉 */}
      <Drawer
        title={`编辑租户【${editingTenant?.tenantCode ?? ''}】`}
        open={editModalVisible}
        onClose={() => setEditModalVisible(false)}
        width={520}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setEditModalVisible(false)}>取消</Button>
            <Button type="primary" loading={editSubmitting} onClick={handleSaveEdit}>
              保存
            </Button>
          </Space>
        }
      >
        <Form form={editForm} layout="vertical" className="mt-4">
          <Form.Item label="租户 ID">
            <Input value={editingTenant?.id} disabled />
          </Form.Item>
          <Form.Item label="租户编码">
            <Input value={editingTenant?.tenantCode} disabled />
          </Form.Item>
          <Form.Item
            name="tenantName"
            label="租户名称"
            rules={[
              { required: true, message: '请输入租户名称' },
              { max: 128, message: '租户名称长度不能超过128个字符' },
            ]}
          >
            <Input placeholder="请输入新的租户名称" maxLength={128} />
          </Form.Item>
          <Alert
            type="info"
            showIcon
            message="租户名称修改后即刻在平台全局生效，租户编码不可更改以维持引用一致性。"
          />
        </Form>
      </Drawer>
    </div>
  )
}
