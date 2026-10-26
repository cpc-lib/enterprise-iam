import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  ApartmentOutlined,
  CheckCircleOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
  EditOutlined,
  FolderOutlined,
  MenuOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  StopOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons'
import {
  App,
  Button,
  Card,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Radio,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  TreeSelect,
  Typography,
} from 'antd'
import type { TableColumnsType } from 'antd'
import {
  createMenu,
  deleteMenu,
  disableMenu,
  enableMenu,
  getMenuPage,
  getMenuTree,
  publishAllMenus,
  publishMenu,
  updateMenu,
} from '@/api/identity/menus'
import { resolveErrorMessage } from '@/api/client/errors'
import { usePageSchema } from '@/hooks/useServerMetadata'
import { useAuthStore } from '@/stores/auth'
import type {
  CreateMenuRequest,
  MenuItem,
  MenuNode,
  MenuPublishStatus,
  MenuStatus,
  MenuType,
  UpdateMenuRequest,
} from '@/types/identity'

const MENU_TYPE_TAG: Record<MenuType, { color: string; label: string }> = {
  DIRECTORY: { color: 'cyan', label: '目录' },
  MENU: { color: 'blue', label: '菜单' },
  BUTTON: { color: 'purple', label: '按钮' },
}

const STATUS_TAG: Record<MenuStatus, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '启用' },
  DISABLED: { color: 'red', label: '停用' },
}

const PUBLISH_TAG: Record<MenuPublishStatus, { color: string; label: string }> = {
  DRAFT: { color: 'orange', label: '待发布' },
  PUBLISHED: { color: 'green', label: '已发布' },
}

function renderId(value: string) {
  return (
    <Typography.Text copyable={{ text: value }} className="font-mono text-xs">
      {value}
    </Typography.Text>
  )
}

interface TreeSelectOption {
  title: string
  value: string
  key: string
  children?: TreeSelectOption[]
}

function convertMenuToTreeSelect(nodes: MenuNode[]): TreeSelectOption[] {
  return nodes.map((node) => ({
    title: `${node.title} (${node.code})`,
    value: node.id,
    key: node.id,
    children: node.children && node.children.length > 0 ? convertMenuToTreeSelect(node.children) : undefined,
  }))
}

export default function MenusPage() {
  const { message } = App.useApp()
  const user = useAuthStore((s) => s.user)
  const tenantId = user?.tenantId || '1'
  const { hasOperation } = usePageSchema('system-menus')

  // 过滤与分页查询状态
  const [searchTitle, setSearchTitle] = useState('')
  const [searchType, setSearchType] = useState<MenuType | undefined>(undefined)
  const [searchStatus, setSearchStatus] = useState<MenuStatus | undefined>(undefined)
  const [viewMode, setViewMode] = useState<'tree' | 'flat'>('tree')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  // 数据列表状态
  const [menuTree, setMenuTree] = useState<MenuNode[]>([])
  const [allMenusTree, setAllMenusTree] = useState<MenuNode[]>([])
  const [flatItems, setFlatItems] = useState<MenuItem[]>([])
  const [flatTotal, setFlatTotal] = useState(0)
  const [expandedRowKeys, setExpandedRowKeys] = useState<React.Key[]>([])

  const [loading, setLoading] = useState(false)
  const [publishingAll, setPublishingAll] = useState(false)

  const [createOpen, setCreateOpen] = useState(false)
  const [parentForCreate, setParentForCreate] = useState<MenuNode | null>(null)
  const [editOpen, setEditOpen] = useState(false)
  const [editingMenu, setEditingMenu] = useState<MenuItem | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const [createForm] = Form.useForm<CreateMenuRequest>()
  const [editForm] = Form.useForm<UpdateMenuRequest>()

  // 加载全量树用于上级菜单下拉框
  const loadMasterTree = useCallback(async () => {
    try {
      const data = await getMenuTree(tenantId)
      setAllMenusTree(data)
    } catch {
      // ignore
    }
  }, [tenantId])

  useEffect(() => {
    void loadMasterTree()
  }, [loadMasterTree])

  // 上级菜单名称映射（用于平铺视图）
  const parentNameMap = useMemo(() => {
    const map = new Map<string, string>()
    function collect(nodes: MenuNode[]) {
      for (const node of nodes) {
        map.set(String(node.id), node.title)
        if (node.children) collect(node.children)
      }
    }
    collect(allMenusTree.length > 0 ? allMenusTree : menuTree)
    return map
  }, [allMenusTree, menuTree])

  // 获取菜单数据（支持树形层级或平铺分页）
  const fetchData = useCallback(async () => {
    setLoading(true)
    try {
      if (viewMode === 'tree') {
        const data = await getMenuTree(tenantId, {
          title: searchTitle.trim() || undefined,
          menuType: searchType,
          status: searchStatus,
        })
        setMenuTree(data)
        // 若有搜索条件，自动展开匹配节点
        if (searchTitle.trim() || searchType) {
          const keys: string[] = []
          function collectKeys(nodes: MenuNode[]) {
            for (const n of nodes) {
              if (n.children && n.children.length > 0) {
                keys.push(String(n.id))
                collectKeys(n.children)
              }
            }
          }
          collectKeys(data)
          setExpandedRowKeys(keys)
        }
      } else {
        const res = await getMenuPage({
          tenantId,
          title: searchTitle.trim() || undefined,
          menuType: searchType,
          status: searchStatus,
          page,
          pageSize,
        })
        setFlatItems(res.items)
        setFlatTotal(res.total)
      }
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }, [tenantId, viewMode, searchTitle, searchType, searchStatus, page, pageSize, message])

  useEffect(() => {
    void fetchData()
  }, [fetchData])

  const handleSearch = () => {
    setPage(1)
    void fetchData()
  }

  const handleReset = () => {
    setSearchTitle('')
    setSearchType(undefined)
    setSearchStatus(undefined)
    setPage(1)
  }

  const handleCreate = async () => {
    try {
      const values = await createForm.validateFields()
      setSubmitting(true)
      await createMenu({
        ...values,
        tenantId,
        parentId: parentForCreate ? parentForCreate.id : values.parentId || null,
        sortOrder: values.sortOrder ?? 0,
      })
      message.success(`菜单「${values.title}」已创建（状态：待发布）`)
      setCreateOpen(false)
      createForm.resetFields()
      void fetchData()
      void loadMasterTree()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const handleEdit = async () => {
    if (!editingMenu) return
    try {
      const values = await editForm.validateFields()
      setSubmitting(true)
      await updateMenu(editingMenu.id, {
        ...values,
        tenantId,
        code: editingMenu.code,
        sortOrder: values.sortOrder ?? editingMenu.sortOrder,
      })
      message.success('菜单配置已更新（状态已进入待发布）')
      setEditOpen(false)
      setEditingMenu(null)
      void fetchData()
      void loadMasterTree()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const handleToggleStatus = async (record: MenuItem) => {
    try {
      if (record.status === 'ACTIVE') {
        await disableMenu(record.id, tenantId)
        message.success(`菜单「${record.title}」已停用`)
      } else {
        await enableMenu(record.id, tenantId)
        message.success(`菜单「${record.title}」已启用`)
      }
      void fetchData()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const handlePublish = async (record: MenuItem) => {
    try {
      await publishMenu(record.id, tenantId)
      message.success(`菜单「${record.title}」已发布上线`)
      void fetchData()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const handlePublishAll = async () => {
    setPublishingAll(true)
    try {
      await publishAllMenus(tenantId)
      message.success('所有草稿菜单已全部发布上线')
      void fetchData()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setPublishingAll(false)
    }
  }

  const handleDelete = async (record: MenuItem) => {
    try {
      await deleteMenu(record.id, tenantId)
      message.success(`菜单「${record.title}」已删除`)
      void fetchData()
      void loadMasterTree()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const treeSelectOptions = convertMenuToTreeSelect(allMenusTree.length > 0 ? allMenusTree : menuTree)

  // 树形模式下分页切片根节点
  const pagedTree = useMemo(() => {
    const from = (page - 1) * pageSize
    return menuTree.slice(from, from + pageSize)
  }, [menuTree, page, pageSize])

  // 树形视图表格列
  const columns: TableColumnsType<MenuNode> = [
    {
      title: '菜单名称',
      dataIndex: 'title',
      width: 220,
      render: (val: string, record) => (
        <Space size={6}>
          {record.menuType === 'DIRECTORY' ? <FolderOutlined /> : <MenuOutlined />}
          <span className="font-medium text-slate-800">{val}</span>
        </Space>
      ),
    },
    {
      title: '菜单编码',
      dataIndex: 'code',
      width: 160,
      render: (val: string) => <Tag>{val}</Tag>,
    },
    {
      title: '类型',
      dataIndex: 'menuType',
      width: 80,
      render: (val: MenuType) => {
        const meta = MENU_TYPE_TAG[val] ?? { color: 'default', label: val }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: '路由路径',
      dataIndex: 'path',
      width: 170,
      render: (val?: string) => val ? <code className="text-xs text-slate-600">{val}</code> : '-',
    },
    {
      title: '权限标识',
      dataIndex: 'permission',
      width: 180,
      render: (val?: string) => val ? <Tag color="blue">{val}</Tag> : '-',
    },
    { title: '排序', dataIndex: 'sortOrder', width: 65 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (val: MenuStatus) => {
        const meta = STATUS_TAG[val] ?? { color: 'default', label: val }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: '发布状态',
      dataIndex: 'publishStatus',
      width: 90,
      render: (val: MenuPublishStatus) => {
        const meta = PUBLISH_TAG[val] ?? { color: 'default', label: val }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 260,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          {record.menuType === 'DIRECTORY' && hasOperation('menu:create-child') && (
            <Button
              type="link"
              size="small"
              icon={<PlusOutlined />}
              onClick={() => {
                setParentForCreate(record)
                createForm.resetFields()
                setCreateOpen(true)
              }}
            >
              加子菜单
            </Button>
          )}
          {hasOperation('menu:update') && (
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => {
                setEditingMenu(record)
                editForm.setFieldsValue({
                  title: record.title,
                  menuType: record.menuType,
                  path: record.path || undefined,
                  component: record.component || undefined,
                  icon: record.icon || undefined,
                  permission: record.permission || undefined,
                  sortOrder: record.sortOrder,
                })
                setEditOpen(true)
              }}
            >
              编辑
            </Button>
          )}
          {record.publishStatus === 'DRAFT' && hasOperation('menu:publish') && (
            <Button
              type="link"
              size="small"
              className="text-amber-600 hover:text-amber-500"
              icon={<CloudUploadOutlined />}
              onClick={() => void handlePublish(record)}
            >
              发布
            </Button>
          )}
          {hasOperation('menu:toggle-status') && (
            <Button
              type="link"
              size="small"
              danger={record.status === 'ACTIVE'}
              icon={record.status === 'ACTIVE' ? <StopOutlined /> : <CheckCircleOutlined />}
              onClick={() => void handleToggleStatus(record)}
            >
              {record.status === 'ACTIVE' ? '停用' : '启用'}
            </Button>
          )}
          {hasOperation('menu:delete') && (
            <Popconfirm
              title="确认删除该菜单配置？"
              description="删除后无法恢复，若存在子菜单项将禁止删除。"
              onConfirm={() => void handleDelete(record)}
              okText="删除"
              cancelText="取消"
              okButtonProps={{ danger: true }}
            >
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  // 平铺列表视图表格列
  const flatColumns: TableColumnsType<MenuItem> = [
    {
      title: '菜单名称',
      dataIndex: 'title',
      width: 180,
      render: (val: string, record) => (
        <Space size={6}>
          {record.menuType === 'DIRECTORY' ? <FolderOutlined /> : <MenuOutlined />}
          <span className="font-medium text-slate-800">{val}</span>
        </Space>
      ),
    },
    {
      title: '所属上级',
      dataIndex: 'parentId',
      width: 130,
      render: (parentId?: string | null) =>
        parentId ? (
          <Tag color="cyan">{parentNameMap.get(String(parentId)) ?? parentId}</Tag>
        ) : (
          <Tag color="purple">顶级模块</Tag>
        ),
    },
    {
      title: '菜单编码',
      dataIndex: 'code',
      width: 150,
      render: (val: string) => <Tag>{val}</Tag>,
    },
    {
      title: '类型',
      dataIndex: 'menuType',
      width: 80,
      render: (val: MenuType) => {
        const meta = MENU_TYPE_TAG[val] ?? { color: 'default', label: val }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: '路由路径',
      dataIndex: 'path',
      width: 160,
      render: (val?: string) => val ? <code className="text-xs text-slate-600">{val}</code> : '-',
    },
    {
      title: '权限标识',
      dataIndex: 'permission',
      width: 180,
      render: (val?: string) => val ? <Tag color="blue">{val}</Tag> : '-',
    },
    { title: '排序', dataIndex: 'sortOrder', width: 65 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (val: MenuStatus) => {
        const meta = STATUS_TAG[val] ?? { color: 'default', label: val }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: '发布状态',
      dataIndex: 'publishStatus',
      width: 90,
      render: (val: MenuPublishStatus) => {
        const meta = PUBLISH_TAG[val] ?? { color: 'default', label: val }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          {hasOperation('menu:update') && (
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => {
                setEditingMenu(record)
                editForm.setFieldsValue({
                  title: record.title,
                  menuType: record.menuType,
                  path: record.path || undefined,
                  component: record.component || undefined,
                  icon: record.icon || undefined,
                  permission: record.permission || undefined,
                  sortOrder: record.sortOrder,
                })
                setEditOpen(true)
              }}
            >
              编辑
            </Button>
          )}
          {record.publishStatus === 'DRAFT' && hasOperation('menu:publish') && (
            <Button
              type="link"
              size="small"
              className="text-amber-600 hover:text-amber-500"
              icon={<CloudUploadOutlined />}
              onClick={() => void handlePublish(record)}
            >
              发布
            </Button>
          )}
          {hasOperation('menu:toggle-status') && (
            <Button
              type="link"
              size="small"
              danger={record.status === 'ACTIVE'}
              icon={record.status === 'ACTIVE' ? <StopOutlined /> : <CheckCircleOutlined />}
              onClick={() => void handleToggleStatus(record)}
            >
              {record.status === 'ACTIVE' ? '停用' : '启用'}
            </Button>
          )}
          {hasOperation('menu:delete') && (
            <Popconfirm
              title="确认删除该菜单配置？"
              description="删除后无法恢复，若存在子菜单项将禁止删除。"
              onConfirm={() => void handleDelete(record)}
              okText="删除"
              cancelText="取消"
              okButtonProps={{ danger: true }}
            >
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-slate-800">菜单与权限配置</h1>
          <p className="mt-1 text-sm text-slate-400">
            企业系统动态菜单与功能按钮配置，支持层级树构建、权限标识绑定、排序维护、启停控制及发布上线
          </p>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void fetchData()} loading={loading}>
            刷新
          </Button>
          {hasOperation('menu:publish-all') && (
            <Popconfirm
              title="确定发布所有待发布的菜单变更？"
              description="发布后新配置将立即对所有租户用户导航栏和路由生效。"
              onConfirm={() => void handlePublishAll()}
              okText="确认发布"
              cancelText="取消"
            >
              <Button icon={<CloudUploadOutlined />} loading={publishingAll}>
                一键发布所有变更
              </Button>
            </Popconfirm>
          )}
          {hasOperation('menu:create') && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setParentForCreate(null)
                createForm.resetFields()
                setCreateOpen(true)
              }}
            >
              新建根菜单
            </Button>
          )}
        </Space>
      </div>

      <Card className="rounded-2xl" styles={{ body: { paddingTop: 16 } }}>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <Input
              allowClear
              placeholder="按菜单名称模糊搜索"
              value={searchTitle}
              onChange={(e) => setSearchTitle(e.target.value)}
              onPressEnter={handleSearch}
              style={{ width: 240 }}
              prefix={<SearchOutlined className="text-slate-400" />}
            />
            <Select
              allowClear
              placeholder="菜单类型"
              value={searchType}
              onChange={(val) => setSearchType(val)}
              style={{ width: 130 }}
              options={[
                { value: 'DIRECTORY', label: '目录' },
                { value: 'MENU', label: '菜单' },
                { value: 'BUTTON', label: '按钮' },
              ]}
            />
            <Select
              allowClear
              placeholder="状态"
              value={searchStatus}
              onChange={(val) => setSearchStatus(val)}
              style={{ width: 120 }}
              options={[
                { value: 'ACTIVE', label: '启用' },
                { value: 'DISABLED', label: '停用' },
              ]}
            />
            <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
              查询
            </Button>
            <Button icon={<ReloadOutlined />} onClick={handleReset}>
              重置
            </Button>
          </div>

          <Segmented
            value={viewMode}
            onChange={(val) => {
              setViewMode(val as 'tree' | 'flat')
              setPage(1)
            }}
            options={[
              { label: '树形层级', value: 'tree', icon: <ApartmentOutlined /> },
              { label: '平铺列表', value: 'flat', icon: <UnorderedListOutlined /> },
            ]}
          />
        </div>

        {viewMode === 'tree' ? (
          <Table<MenuNode>
            rowKey="id"
            size="middle"
            loading={loading}
            columns={columns}
            dataSource={pagedTree}
            expandable={{
              expandedRowKeys,
              onExpandedRowsChange: (keys) => setExpandedRowKeys(keys as React.Key[]),
            }}
            pagination={{
              current: page,
              pageSize,
              total: menuTree.length,
              showSizeChanger: true,
              pageSizeOptions: ['10', '15', '20', '50'],
              showTotal: (total) => `共 ${total} 个顶层模块（展开含全部子菜单）`,
              onChange: (p, s) => {
                setPage(p)
                setPageSize(s)
              },
            }}
            scroll={{ x: 1300 }}
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="暂无匹配的菜单数据"
                  className="py-12"
                />
              ),
            }}
          />
        ) : (
          <Table<MenuItem>
            rowKey="id"
            size="middle"
            loading={loading}
            columns={flatColumns}
            dataSource={flatItems}
            pagination={{
              current: page,
              pageSize,
              total: flatTotal,
              showSizeChanger: true,
              pageSizeOptions: ['10', '15', '20', '50'],
              showTotal: (total) => `共 ${total} 项菜单数据`,
              onChange: (p, s) => {
                setPage(p)
                setPageSize(s)
              },
            }}
            scroll={{ x: 1300 }}
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="暂无匹配的菜单数据"
                  className="py-12"
                />
              ),
            }}
          />
        )}
      </Card>

      <Drawer
        title={parentForCreate ? `在「${parentForCreate.title}」下新建子项` : '新建根菜单/目录'}
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        width={560}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setCreateOpen(false)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={handleCreate}>
              创建
            </Button>
          </Space>
        }
        footer={
          <div className="flex justify-end gap-2">
            <Button onClick={() => setCreateOpen(false)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={handleCreate}>
              创建
            </Button>
          </div>
        }
      >
        <Form form={createForm} layout="vertical" className="pt-2" requiredMark={false}>
          {!parentForCreate && (
            <Form.Item name="parentId" label="上级菜单（可选）">
              <TreeSelect
                allowClear
                placeholder="不选则为顶级根目录"
                treeData={treeSelectOptions}
                treeDefaultExpandAll
              />
            </Form.Item>
          )}

          <Form.Item
            name="title"
            label="菜单名称"
            rules={[{ required: true, message: '请输入菜单名称' }]}
          >
            <Input placeholder="例如：用户管理 / 角色权限" maxLength={64} />
          </Form.Item>

          <Form.Item
            name="code"
            label="菜单编码 (menuKey)"
            rules={[
              { required: true, message: '请输入菜单唯一编码' },
              { pattern: /^[a-z0-9-_]{2,64}$/, message: '格式：2-64位小写字母、数字、连字符或下划线' },
            ]}
          >
            <Input placeholder="例如：identity-users" maxLength={64} />
          </Form.Item>

          <Form.Item
            name="menuType"
            label="菜单类型"
            initialValue="MENU"
            rules={[{ required: true }]}
          >
            <Radio.Group buttonStyle="solid">
              <Radio.Button value="DIRECTORY">目录</Radio.Button>
              <Radio.Button value="MENU">菜单页面</Radio.Button>
              <Radio.Button value="BUTTON">按钮/操作权限</Radio.Button>
            </Radio.Group>
          </Form.Item>

          <Form.Item name="path" label="路由路径">
            <Input placeholder="例如：/identity/users（仅目录/菜单有效）" maxLength={256} />
          </Form.Item>

          <Form.Item name="component" label="前端组件路径">
            <Input placeholder="例如：identity/UsersPage（仅菜单页面需要）" maxLength={128} />
          </Form.Item>

          <Form.Item name="icon" label="图标标识">
            <Input placeholder="例如：user / safety / team" maxLength={64} />
          </Form.Item>

          <Form.Item name="permission" label="权限标识符">
            <Input placeholder="例如：identity:user:list（按钮或接口操作码）" maxLength={64} />
          </Form.Item>

          <Form.Item name="sortOrder" label="排序权重" initialValue={10}>
            <InputNumber min={0} max={9999} className="w-full" />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={`编辑菜单配置「${editingMenu?.title ?? ''}」`}
        open={editOpen}
        onClose={() => {
          setEditOpen(false)
          setEditingMenu(null)
        }}
        width={560}
        destroyOnClose
        extra={
          <Space>
            <Button
              onClick={() => {
                setEditOpen(false)
                setEditingMenu(null)
              }}
            >
              取消
            </Button>
            <Button type="primary" loading={submitting} onClick={handleEdit}>
              保存
            </Button>
          </Space>
        }
        footer={
          <div className="flex justify-end gap-2">
            <Button
              onClick={() => {
                setEditOpen(false)
                setEditingMenu(null)
              }}
            >
              取消
            </Button>
            <Button type="primary" loading={submitting} onClick={handleEdit}>
              保存
            </Button>
          </div>
        }
      >
        <Form form={editForm} layout="vertical" className="pt-2" requiredMark={false}>
          <Form.Item label="菜单ID / 编码">
            <Space>
              {renderId(editingMenu?.id ?? '')}
              <Tag>{editingMenu?.code}</Tag>
            </Space>
          </Form.Item>

          <Form.Item
            name="title"
            label="菜单名称"
            rules={[{ required: true, message: '请输入菜单名称' }]}
          >
            <Input placeholder="菜单名称" maxLength={64} />
          </Form.Item>

          <Form.Item
            name="menuType"
            label="菜单类型"
            rules={[{ required: true }]}
          >
            <Radio.Group buttonStyle="solid">
              <Radio.Button value="DIRECTORY">目录</Radio.Button>
              <Radio.Button value="MENU">菜单页面</Radio.Button>
              <Radio.Button value="BUTTON">按钮/操作权限</Radio.Button>
            </Radio.Group>
          </Form.Item>

          <Form.Item name="path" label="路由路径">
            <Input placeholder="例如：/identity/users" maxLength={256} />
          </Form.Item>

          <Form.Item name="component" label="前端组件路径">
            <Input placeholder="例如：identity/UsersPage" maxLength={128} />
          </Form.Item>

          <Form.Item name="icon" label="图标标识">
            <Input placeholder="例如：user" maxLength={64} />
          </Form.Item>

          <Form.Item name="permission" label="权限标识符">
            <Input placeholder="例如：identity:user:list" maxLength={64} />
          </Form.Item>

          <Form.Item name="sortOrder" label="排序权重">
            <InputNumber min={0} max={9999} className="w-full" />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
