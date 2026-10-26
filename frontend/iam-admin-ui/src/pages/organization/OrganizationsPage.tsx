import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  ApartmentOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  StopOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import {
  App,
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Row,
  Space,
  Table,
  Tag,
  Tree,
  Typography,
} from 'antd'
import type { TableColumnsType, TreeDataNode } from 'antd'
import {
  createOrganization,
  deleteOrganization,
  disableOrganization,
  enableOrganization,
  getOrganizationTree,
  listOrganizationMembers,
  updateOrganization,
} from '@/api/organization/organizations'
import { resolveErrorMessage } from '@/api/client/errors'
import { usePageSchema } from '@/hooks/useServerMetadata'
import { useAuthStore } from '@/stores/auth'
import type {
  CreateOrganizationRequest,
  DepartmentMember,
  DepartmentNode,
  UpdateOrganizationRequest,
} from '@/types/organization'

const ORG_CODE_PATTERN = /^[A-Z][A-Z0-9_]{1,63}$/

const STATUS_TAG: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '启用' },
  DISABLED: { color: 'red', label: '停用' },
}

function renderStatus(status: string) {
  const meta = STATUS_TAG[status] ?? { color: 'default', label: status }
  return <Tag color={meta.color}>{meta.label}</Tag>
}

function renderId(value: string) {
  return (
    <Typography.Text copyable={{ text: value }} className="font-mono text-xs">
      {value}
    </Typography.Text>
  )
}

function convertToTreeData(nodes: DepartmentNode[]): TreeDataNode[] {
  return nodes.map((node) => ({
    key: node.id,
    title: (
      <Space size={6}>
        <span className="font-medium text-slate-800">{node.name}</span>
        <Tag color="blue">{node.code}</Tag>
        {node.status === 'DISABLED' && <Tag color="red">已停用</Tag>}
      </Space>
    ),
    children: node.children && node.children.length > 0 ? convertToTreeData(node.children) : undefined,
  }))
}

function findNode(nodes: DepartmentNode[], id: string): DepartmentNode | null {
  for (const node of nodes) {
    if (node.id === id) return node
    if (node.children && node.children.length > 0) {
      const found = findNode(node.children, id)
      if (found) return found
    }
  }
  return null
}

export default function OrganizationsPage() {
  const { message } = App.useApp()
  const user = useAuthStore((s) => s.user)
  const tenantId = user?.tenantId || '1'
  const { hasOperation } = usePageSchema('organization-orgs')

  const [treeData, setTreeData] = useState<DepartmentNode[]>([])
  const [loading, setLoading] = useState(false)
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const [selectedNode, setSelectedNode] = useState<DepartmentNode | null>(null)

  const [members, setMembers] = useState<DepartmentMember[]>([])
  const [membersLoading, setMembersLoading] = useState(false)

  const [createOpen, setCreateOpen] = useState(false)
  const [parentForCreate, setParentForCreate] = useState<DepartmentNode | null>(null)
  const [editOpen, setEditOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const [createForm] = Form.useForm<CreateOrganizationRequest>()
  const [editForm] = Form.useForm<UpdateOrganizationRequest>()

  const fetchTree = useCallback(async (selectId?: string) => {
    setLoading(true)
    try {
      const data = await getOrganizationTree(tenantId)
      setTreeData(data)
      const targetId = selectId || selectedKey || (data.length > 0 ? data[0].id : null)
      if (targetId) {
        setSelectedKey(targetId)
        const found = findNode(data, targetId)
        setSelectedNode(found)
      } else {
        setSelectedNode(null)
      }
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }, [tenantId, selectedKey, message])

  const fetchMembers = useCallback(async (nodeId: string) => {
    setMembersLoading(true)
    try {
      const list = await listOrganizationMembers(nodeId, tenantId)
      setMembers(list)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setMembersLoading(false)
    }
  }, [tenantId, message])

  useEffect(() => {
    void fetchTree()
  }, [fetchTree])

  useEffect(() => {
    if (selectedKey) {
      void fetchMembers(selectedKey)
    } else {
      setMembers([])
    }
  }, [selectedKey, fetchMembers])

  const handleSelect = (keys: React.Key[]) => {
    if (keys.length > 0) {
      const keyStr = String(keys[0])
      setSelectedKey(keyStr)
      setSelectedNode(findNode(treeData, keyStr))
    }
  }

  const handleCreate = async () => {
    try {
      const values = await createForm.validateFields()
      setSubmitting(true)
      const created = await createOrganization({
        orgCode: values.orgCode.trim().toUpperCase(),
        orgName: values.orgName.trim(),
        parentId: parentForCreate ? parentForCreate.id : null,
      })
      message.success(`部门「${values.orgName}」创建成功`)
      setCreateOpen(false)
      createForm.resetFields()
      void fetchTree(created.id)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const handleEdit = async () => {
    if (!selectedNode) return
    try {
      const values = await editForm.validateFields()
      setSubmitting(true)
      await updateOrganization(selectedNode.id, {
        name: values.name.trim(),
        sortOrder: values.sortOrder ?? selectedNode.sortOrder,
      })
      message.success('部门信息更新成功')
      setEditOpen(false)
      void fetchTree(selectedNode.id)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const handleToggleStatus = async () => {
    if (!selectedNode) return
    try {
      if (selectedNode.status === 'ACTIVE') {
        await disableOrganization(selectedNode.id, tenantId)
        message.success(`部门「${selectedNode.name}」已停用`)
      } else {
        await enableOrganization(selectedNode.id, tenantId)
        message.success(`部门「${selectedNode.name}」已启用`)
      }
      void fetchTree(selectedNode.id)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const handleDelete = async () => {
    if (!selectedNode) return
    try {
      await deleteOrganization(selectedNode.id, tenantId)
      message.success(`部门「${selectedNode.name}」已删除`)
      setSelectedKey(null)
      setSelectedNode(null)
      void fetchTree()
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const memberColumns: TableColumnsType<DepartmentMember> = [
    { title: '用户ID', dataIndex: 'userId', width: 200, render: renderId },
    { title: '用户名', dataIndex: 'username', width: 140 },
    { title: '姓名', dataIndex: 'realName', width: 120, render: (v) => v || '-' },
    { title: '岗位', dataIndex: 'positionName', width: 140, render: (v) => (v ? <Tag color="cyan">{v}</Tag> : <span className="text-slate-300">未分配</span>) },
    { title: '邮箱', dataIndex: 'email', ellipsis: true, render: (v) => v || '-' },
    { title: '手机号', dataIndex: 'phone', width: 130, render: (v) => v || '-' },
    { title: '状态', dataIndex: 'status', width: 80, render: renderStatus },
  ]

  const antdTreeData = useMemo(() => convertToTreeData(treeData), [treeData])

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-slate-800">组织机构与部门管理</h1>
          <p className="mt-1 text-sm text-slate-400">
            企业部门层级树管理，支持部门增删改查、层级展示、状态启停及部门成员查询
          </p>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void fetchTree()} loading={loading}>
            刷新
          </Button>
          {hasOperation('org:create') && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setParentForCreate(null)
                createForm.resetFields()
                setCreateOpen(true)
              }}
            >
              新建根部门
            </Button>
          )}
        </Space>
      </div>

      <Row gutter={16}>
        <Col xs={24} md={8}>
          <Card
            title={
              <Space>
                <ApartmentOutlined />
                部门组织架构
              </Space>
            }
            className="rounded-2xl h-full shadow-sm"
            styles={{ body: { padding: '12px 16px', maxHeight: '720px', overflowY: 'auto' } }}
          >
            {treeData.length === 0 && !loading ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无部门数据"
                className="py-12"
              >
                <Button
                  type="primary"
                  onClick={() => {
                    setParentForCreate(null)
                    createForm.resetFields()
                    setCreateOpen(true)
                  }}
                >
                  创建根部门
                </Button>
              </Empty>
            ) : (
              <Tree
                showLine={{ showLeafIcon: false }}
                defaultExpandAll
                treeData={antdTreeData}
                selectedKeys={selectedKey ? [selectedKey] : []}
                onSelect={handleSelect}
              />
            )}
          </Card>
        </Col>

        <Col xs={24} md={16}>
          {selectedNode ? (
            <div className="space-y-4">
              <Card
                title={
                  <Space>
                    <span className="font-semibold text-base">{selectedNode.name}</span>
                    {renderStatus(selectedNode.status)}
                  </Space>
                }
                extra={
                  <Space>
                    {hasOperation('org:create-child') && (
                      <Button
                        size="small"
                        icon={<PlusOutlined />}
                        onClick={() => {
                          setParentForCreate(selectedNode)
                          createForm.resetFields()
                          setCreateOpen(true)
                        }}
                      >
                        添加子部门
                      </Button>
                    )}
                    {hasOperation('org:update') && (
                      <Button
                        size="small"
                        icon={<EditOutlined />}
                        onClick={() => {
                          editForm.setFieldsValue({
                            name: selectedNode.name,
                            sortOrder: selectedNode.sortOrder,
                          })
                          setEditOpen(true)
                        }}
                      >
                        编辑
                      </Button>
                    )}
                    {hasOperation('org:toggle-status') && (
                      <Button
                        size="small"
                        danger={selectedNode.status === 'ACTIVE'}
                        icon={selectedNode.status === 'ACTIVE' ? <StopOutlined /> : <CheckCircleOutlined />}
                        onClick={() => void handleToggleStatus()}
                      >
                        {selectedNode.status === 'ACTIVE' ? '停用' : '启用'}
                      </Button>
                    )}
                    {hasOperation('org:delete') && (
                      <Popconfirm
                        title="确认删除该部门？"
                        description="有子部门或已有部门成员时将禁止删除。"
                        onConfirm={() => void handleDelete()}
                        okText="删除"
                        cancelText="取消"
                        okButtonProps={{ danger: true }}
                      >
                        <Button size="small" danger icon={<DeleteOutlined />}>
                          删除
                        </Button>
                      </Popconfirm>
                    )}
                  </Space>
                }
                className="rounded-2xl shadow-sm"
              >
                <Descriptions column={{ xs: 1, sm: 2, md: 3 }} size="small">
                  <Descriptions.Item label="部门ID">{renderId(selectedNode.id)}</Descriptions.Item>
                  <Descriptions.Item label="部门编码">
                    <Tag color="blue">{selectedNode.code}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="排序号">{selectedNode.sortOrder}</Descriptions.Item>
                  <Descriptions.Item label="上级部门ID">
                    {selectedNode.parentId ? renderId(selectedNode.parentId) : <span className="text-slate-300">顶级部门</span>}
                  </Descriptions.Item>
                  <Descriptions.Item label="子部门数量">
                    {selectedNode.children ? selectedNode.children.length : 0}
                  </Descriptions.Item>
                  <Descriptions.Item label="成员数量">{members.length}</Descriptions.Item>
                </Descriptions>
              </Card>

              <Card
                title={
                  <Space>
                    <TeamOutlined />
                    部门成员列表
                  </Space>
                }
                className="rounded-2xl shadow-sm"
                styles={{ body: { paddingTop: 12 } }}
              >
                <Table<DepartmentMember>
                  rowKey="userId"
                  size="middle"
                  loading={membersLoading}
                  columns={memberColumns}
                  dataSource={members}
                  pagination={{
                    defaultPageSize: 10,
                    pageSizeOptions: ['10', '20', '50', '100'],
                    showSizeChanger: true,
                    showTotal: (t) => `共 ${t} 名成员`,
                  }}
                  locale={{
                    emptyText: (
                      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该部门暂无成员" />
                    ),
                  }}
                />
              </Card>
            </div>
          ) : (
            <Card className="rounded-2xl text-center py-24">
              <Empty description="请从左侧选择部门节点查看详情与成员" />
            </Card>
          )}
        </Col>
      </Row>

      <Drawer
        title={parentForCreate ? `在「${parentForCreate.name}」下新建子部门` : '新建根部门'}
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        width={520}
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
          {parentForCreate && (
            <Form.Item label="上级部门">
              <Input value={`${parentForCreate.name} (${parentForCreate.code})`} disabled />
            </Form.Item>
          )}
          <Form.Item
            name="orgCode"
            label="部门编码"
            rules={[
              { required: true, message: '请输入部门编码' },
              { pattern: ORG_CODE_PATTERN, message: '大写字母开头，2-64位大写字母、数字或下划线' },
            ]}
          >
            <Input placeholder="例如：DEPT_ENG" maxLength={64} />
          </Form.Item>
          <Form.Item
            name="orgName"
            label="部门名称"
            rules={[{ required: true, message: '请输入部门名称' }]}
          >
            <Input placeholder="例如：研发中心" maxLength={128} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title="编辑部门"
        open={editOpen}
        onClose={() => setEditOpen(false)}
        width={520}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setEditOpen(false)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={handleEdit}>
              保存
            </Button>
          </Space>
        }
        footer={
          <div className="flex justify-end gap-2">
            <Button onClick={() => setEditOpen(false)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={handleEdit}>
              保存
            </Button>
          </div>
        }
      >
        <Form form={editForm} layout="vertical" className="pt-2" requiredMark={false}>
          <Form.Item label="部门编码">
            <Input value={selectedNode?.code} disabled />
          </Form.Item>
          <Form.Item
            name="name"
            label="部门名称"
            rules={[{ required: true, message: '请输入部门名称' }]}
          >
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序号">
            <InputNumber min={0} max={9999} className="w-full" />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
