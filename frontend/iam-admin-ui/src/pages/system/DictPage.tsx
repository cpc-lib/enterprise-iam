import { useCallback, useEffect, useState } from 'react'
import {
  CheckCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  FolderOpenOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  StopOutlined,
} from '@ant-design/icons'
import {
  App,
  Button,
  Card,
  Col,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Radio,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { resolveErrorMessage } from '@/api/client/errors'
import {
  createDictData,
  createDictType,
  deleteDictData,
  deleteDictType,
  listDictData,
  listDictTypes,
  updateDictData,
  updateDictType,
} from '@/api/identity/dict'
import type {
  CreateDictDataRequest,
  CreateDictTypeRequest,
  DictDataItem,
  DictTypeItem,
} from '@/api/identity/dict'

const { Text } = Typography

export default function DictPage() {
  const { message } = App.useApp()

  // 字典类型状态
  const [types, setTypes] = useState<DictTypeItem[]>([])
  const [typesLoading, setTypesLoading] = useState(false)
  const [typeTotal, setTypeTotal] = useState(0)
  const [typePage, setTypePage] = useState(1)
  const [typeKeyword, setTypeKeyword] = useState('')
  const [selectedType, setSelectedType] = useState<DictTypeItem | null>(null)

  // 字典数据状态
  const [dataList, setDataList] = useState<DictDataItem[]>([])
  const [dataLoading, setDataLoading] = useState(false)
  const [dataTotal, setDataTotal] = useState(0)
  const [dataPage, setDataPage] = useState(1)
  const [dataKeyword, setDataKeyword] = useState('')

  // 弹窗状态
  const [typeModalVisible, setTypeModalVisible] = useState(false)
  const [editingType, setEditingType] = useState<DictTypeItem | null>(null)
  const [typeForm] = Form.useForm<CreateDictTypeRequest>()

  const [dataModalVisible, setDataModalVisible] = useState(false)
  const [editingData, setEditingData] = useState<DictDataItem | null>(null)
  const [dataForm] = Form.useForm<CreateDictDataRequest>()

  // 加载字典类型
  const loadDictTypes = useCallback(async () => {
    setTypesLoading(true)
    try {
      const res = await listDictTypes({
        keyword: typeKeyword || undefined,
        page: typePage,
        pageSize: 10,
      })
      setTypes(res.items || [])
      setTypeTotal(res.total || 0)
      if (res.items && res.items.length > 0 && !selectedType) {
        setSelectedType(res.items[0])
      }
    } catch (err) {
      message.error(resolveErrorMessage(err))
    } finally {
      setTypesLoading(false)
    }
  }, [typeKeyword, typePage, selectedType, message])

  // 加载字典数据项
  const loadDictData = useCallback(async () => {
    if (!selectedType) {
      setDataList([])
      setDataTotal(0)
      return
    }
    setDataLoading(true)
    try {
      const res = await listDictData({
        dictType: selectedType.dictType,
        keyword: dataKeyword || undefined,
        page: dataPage,
        pageSize: 10,
      })
      setDataList(res.items || [])
      setDataTotal(res.total || 0)
    } catch (err) {
      message.error(resolveErrorMessage(err))
    } finally {
      setDataLoading(false)
    }
  }, [selectedType, dataKeyword, dataPage, message])

  useEffect(() => {
    loadDictTypes()
  }, [loadDictTypes])

  useEffect(() => {
    loadDictData()
  }, [loadDictData])

  // ================= 字典类型操作 =================
  const handleOpenCreateType = () => {
    setEditingType(null)
    typeForm.resetFields()
    typeForm.setFieldsValue({ status: 'ACTIVE' })
    setTypeModalVisible(true)
  }

  const handleOpenEditType = (record: DictTypeItem) => {
    setEditingType(record)
    typeForm.setFieldsValue({
      dictType: record.dictType,
      dictName: record.dictName,
      status: record.status,
      description: record.description,
    })
    setTypeModalVisible(true)
  }

  const handleSaveType = async () => {
    try {
      const values = await typeForm.validateFields()
      if (editingType) {
        await updateDictType(editingType.id, {
          dictName: values.dictName,
          status: values.status,
          description: values.description,
          version: editingType.version,
        })
        message.success('字典类型更新成功')
      } else {
        await createDictType(values)
        message.success('字典类型创建成功')
      }
      setTypeModalVisible(false)
      loadDictTypes()
    } catch (err) {
      message.error(resolveErrorMessage(err))
    }
  }

  const handleDeleteType = async (id: string) => {
    try {
      await deleteDictType(id)
      message.success('字典类型已删除')
      if (selectedType?.id === id) {
        setSelectedType(null)
      }
      loadDictTypes()
    } catch (err) {
      message.error(resolveErrorMessage(err))
    }
  }

  // ================= 字典数据操作 =================
  const handleOpenCreateData = () => {
    if (!selectedType) return
    setEditingData(null)
    dataForm.resetFields()
    dataForm.setFieldsValue({
      dictType: selectedType.dictType,
      status: 'ACTIVE',
      sortOrder: 0,
      isDefault: false,
      cssTag: 'default',
    })
    setDataModalVisible(true)
  }

  const handleOpenEditData = (record: DictDataItem) => {
    setEditingData(record)
    dataForm.setFieldsValue({
      dictType: record.dictType,
      dictLabel: record.dictLabel,
      dictValue: record.dictValue,
      sortOrder: record.sortOrder,
      status: record.status,
      cssTag: record.cssTag || 'default',
      isDefault: record.isDefault,
      description: record.description,
    })
    setDataModalVisible(true)
  }

  const handleSaveData = async () => {
    try {
      const values = await dataForm.validateFields()
      if (editingData) {
        await updateDictData(editingData.id, {
          dictLabel: values.dictLabel,
          dictValue: values.dictValue,
          sortOrder: values.sortOrder,
          status: values.status,
          cssTag: values.cssTag,
          isDefault: values.isDefault,
          description: values.description,
          version: editingData.version,
        })
        message.success('字典数据更新成功')
      } else {
        await createDictData(values)
        message.success('字典数据创建成功')
      }
      setDataModalVisible(false)
      loadDictData()
    } catch (err) {
      message.error(resolveErrorMessage(err))
    }
  }

  const handleDeleteData = async (id: string) => {
    try {
      await deleteDictData(id)
      message.success('字典数据已删除')
      loadDictData()
    } catch (err) {
      message.error(resolveErrorMessage(err))
    }
  }

  // 字典类型表格列
  const typeColumns: TableColumnsType<DictTypeItem> = [
    {
      title: '字典名称',
      dataIndex: 'dictName',
      key: 'dictName',
      render: (name: string, record) => (
        <Space direction="vertical" size={0}>
          <Text strong className="text-xs">
            {name}
          </Text>
          <Text code className="text-xs text-neutral-400">
            {record.dictType}
          </Text>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 70,
      render: (st: string) =>
        st === 'ACTIVE' ? (
          <Tag color="success">启用</Tag>
        ) : (
          <Tag color="default">停用</Tag>
        ),
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, record) => (
        <Space size="small">
          <Button
            type="text"
            size="small"
            icon={<EditOutlined />}
            onClick={(e) => {
              e.stopPropagation()
              handleOpenEditType(record)
            }}
          />
          <Popconfirm
            title="删除确认"
            description="确定删除此字典类型及其数据吗？"
            onConfirm={(e) => {
              e?.stopPropagation()
              handleDeleteType(record.id)
            }}
            okText="删除"
            cancelText="取消"
          >
            <Button
              type="text"
              danger
              size="small"
              icon={<DeleteOutlined />}
              onClick={(e) => e.stopPropagation()}
            />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  // 字典数据表格列
  const dataColumns: TableColumnsType<DictDataItem> = [
    {
      title: '字典标签',
      dataIndex: 'dictLabel',
      key: 'dictLabel',
      render: (label: string, record) => (
        <Space size={4}>
          <Tag color={record.cssTag || 'default'}>{label}</Tag>
          {record.isDefault && <Tag color="blue">默认</Tag>}
        </Space>
      ),
    },
    {
      title: '字典键值',
      dataIndex: 'dictValue',
      key: 'dictValue',
      render: (val: string) => (
        <Text copyable code className="text-xs">
          {val}
        </Text>
      ),
    },
    {
      title: '排序',
      dataIndex: 'sortOrder',
      key: 'sortOrder',
      width: 60,
    },
    {
      title: '样式标签',
      dataIndex: 'cssTag',
      key: 'cssTag',
      width: 90,
      render: (tag: string) => (tag ? <Tag color={tag}>{tag}</Tag> : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (st: string) =>
        st === 'ACTIVE' ? (
          <Tag icon={<CheckCircleOutlined />} color="success">
            启用
          </Tag>
        ) : (
          <Tag icon={<StopOutlined />} color="default">
            停用
          </Tag>
        ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (desc: string) => <span className="text-xs text-neutral-400">{desc || '-'}</span>,
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) => (
        <Space size="small">
          <Button
            type="text"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleOpenEditData(record)}
          />
          <Popconfirm
            title="删除字典项确认"
            description={`确定删除字典项 ${record.dictLabel} 吗？`}
            onConfirm={() => handleDeleteData(record.id)}
            okText="删除"
            cancelText="取消"
          >
            <Button type="text" danger size="small" icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      <Row gutter={16}>
        {/* 左侧：字典类型管理 */}
        <Col span={9}>
          <Card
            title={
              <Space>
                <FolderOpenOutlined className="text-blue-500" />
                <span>字典分类</span>
              </Space>
            }
            extra={
              <Button type="primary" size="small" icon={<PlusOutlined />} onClick={handleOpenCreateType}>
                新增分类
              </Button>
            }
            size="small"
            bordered={false}
            className="shadow-xs"
          >
            <div className="mb-3 flex items-center gap-2">
              <Input
                placeholder="搜索类型或名称"
                allowClear
                size="small"
                prefix={<SearchOutlined className="text-slate-400" />}
                style={{ width: 180 }}
                value={typeKeyword}
                onChange={(e) => setTypeKeyword(e.target.value)}
                onPressEnter={() => {
                  setTypePage(1)
                  loadDictTypes()
                }}
              />
              <Button size="small" type="primary" icon={<SearchOutlined />} onClick={() => loadDictTypes()}>
                搜索
              </Button>
            </div>
            <Table<DictTypeItem>
              rowKey="id"
              size="small"
              loading={typesLoading}
              columns={typeColumns}
              dataSource={types}
              rowClassName={(r) =>
                r.id === selectedType?.id ? 'bg-blue-50/60 dark:bg-blue-900/20 cursor-pointer' : 'cursor-pointer'
              }
              onRow={(record) => ({
                onClick: () => setSelectedType(record),
              })}
              pagination={{
                current: typePage,
                pageSize: 10,
                total: typeTotal,
                simple: true,
                onChange: (p) => setTypePage(p),
              }}
            />
          </Card>
        </Col>

        {/* 右侧：字典数据管理 */}
        <Col span={15}>
          <Card
            title={
              selectedType ? (
                <Space>
                  <span>
                    数据项：<strong>{selectedType.dictName}</strong>
                  </span>
                  <Tag color="cyan">{selectedType.dictType}</Tag>
                </Space>
              ) : (
                '字典数据项'
              )
            }
            extra={
              <Space>
                <Button
                  size="small"
                  icon={<ReloadOutlined />}
                  onClick={loadDictData}
                  disabled={!selectedType}
                  loading={dataLoading}
                >
                  刷新
                </Button>
                <Button
                  type="primary"
                  size="small"
                  icon={<PlusOutlined />}
                  disabled={!selectedType}
                  onClick={handleOpenCreateData}
                >
                  新增字典项
                </Button>
              </Space>
            }
            size="small"
            bordered={false}
            className="shadow-xs"
          >
            {selectedType ? (
              <>
                <div className="mb-3 flex items-center gap-2">
                  <Input
                    placeholder="搜索字典标签或键值"
                    allowClear
                    size="small"
                    prefix={<SearchOutlined className="text-slate-400" />}
                    style={{ width: 220 }}
                    value={dataKeyword}
                    onChange={(e) => setDataKeyword(e.target.value)}
                    onPressEnter={() => {
                      setDataPage(1)
                      loadDictData()
                    }}
                  />
                  <Button size="small" type="primary" icon={<SearchOutlined />} onClick={() => loadDictData()}>
                    查询
                  </Button>
                </div>
                <Table<DictDataItem>
                  rowKey="id"
                  size="small"
                  loading={dataLoading}
                  columns={dataColumns}
                  dataSource={dataList}
                  pagination={{
                    current: dataPage,
                    pageSize: 10,
                    defaultPageSize: 10,
                    total: dataTotal,
                    showSizeChanger: true,
                    pageSizeOptions: ['10', '20', '50', '100'],
                    showTotal: (t) => `共 ${t} 项`,
                    onChange: (p) => setDataPage(p),
                  }}
                />
              </>
            ) : (
              <Empty description="请在左侧选择一个字典分类" className="my-16" />
            )}
          </Card>
        </Col>
      </Row>

      {/* 字典类型 新增/编辑 Drawer */}
      <Drawer
        title={editingType ? '编辑字典分类' : '新建字典分类'}
        open={typeModalVisible}
        onClose={() => setTypeModalVisible(false)}
        width={520}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setTypeModalVisible(false)}>取消</Button>
            <Button type="primary" onClick={handleSaveType}>
              保存
            </Button>
          </Space>
        }
      >
        <Form form={typeForm} layout="vertical">
          <Form.Item
            name="dictType"
            label="字典类型编码"
            rules={[
              { required: true, message: '请输入字典类型编码' },
              { pattern: /^[a-z0-9_]+$/, message: '仅支持小写字母、数字和下划线' },
            ]}
          >
            <Input placeholder="如：sys_user_sex, order_status" disabled={!!editingType} />
          </Form.Item>
          <Form.Item
            name="dictName"
            label="字典分类名称"
            rules={[{ required: true, message: '请输入字典分类名称' }]}
          >
            <Input placeholder="如：用户性别、订单状态" />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue="ACTIVE">
            <Radio.Group>
              <Radio value="ACTIVE">启用</Radio>
              <Radio value="DISABLED">停用</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item name="description" label="备注说明">
            <Input.TextArea rows={2} placeholder="可输入备注信息" />
          </Form.Item>
        </Form>
      </Drawer>

      {/* 字典数据项 新增/编辑 Drawer */}
      <Drawer
        title={editingData ? '编辑字典项' : '新建字典项'}
        open={dataModalVisible}
        onClose={() => setDataModalVisible(false)}
        width={520}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setDataModalVisible(false)}>取消</Button>
            <Button type="primary" onClick={handleSaveData}>
              保存
            </Button>
          </Space>
        }
      >
        <Form form={dataForm} layout="vertical">
          <Form.Item name="dictType" label="字典类型编码">
            <Input disabled />
          </Form.Item>
          <Form.Item
            name="dictLabel"
            label="字典标签"
            rules={[{ required: true, message: '请输入字典标签' }]}
          >
            <Input placeholder="展示文字，如：男、已发货" />
          </Form.Item>
          <Form.Item
            name="dictValue"
            label="字典键值"
            rules={[{ required: true, message: '请输入字典键值' }]}
          >
            <Input placeholder="系统存储值，如：MALE、1" />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序权重" initialValue={0}>
            <InputNumber min={0} max={9999} className="w-full" />
          </Form.Item>
          <Form.Item name="cssTag" label="标签展示样式 (Tag 颜色)">
            <Select
              allowClear
              options={[
                { label: '默认灰 (default)', value: 'default' },
                { label: '成功绿 (success)', value: 'success' },
                { label: '处理蓝 (processing)', value: 'processing' },
                { label: '警告橙 (warning)', value: 'warning' },
                { label: '错误红 (error)', value: 'error' },
                { label: '青色 (cyan)', value: 'cyan' },
                { label: '紫色 (purple)', value: 'purple' },
              ]}
            />
          </Form.Item>
          <Form.Item name="isDefault" label="是否默认值" valuePropName="checked">
            <Switch checkedChildren="是" unCheckedChildren="否" />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue="ACTIVE">
            <Radio.Group>
              <Radio value="ACTIVE">启用</Radio>
              <Radio value="DISABLED">停用</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input.TextArea rows={2} placeholder="可输入备注信息" />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
