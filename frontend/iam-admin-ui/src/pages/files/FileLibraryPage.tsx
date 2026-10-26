import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  CloudUploadOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EyeOutlined,
  FileOutlined,
  ProfileOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import {
  App,
  Alert,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Divider,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { Dayjs } from 'dayjs'
import { ApiError, resolveErrorMessage } from '@/api/client/errors'
import { useUploadQueue } from '@/features/file-management/store/uploadQueue'
import { useShallow } from 'zustand/react/shallow'
import FilePreview from '@/features/file-management/components/FilePreview'
import FileUploader from '@/features/file-management/components/FileUploader'
import {
  archiveFileToColdTier,
  authorizeFileDownload,
  authorizeFilePreview,
  createFileReference,
  deleteFile,
  executeFileDrDrill,
  fetchDynamicWatermark,
  inspectFileDlp,
  listDemoFiles,
  listFileDrDrills,
  restoreFileFromColdTier,
} from '@/features/file-management/api'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime, humanFileSize, isImageContentType } from '@/features/file-management/utils/format'
import { FILE_STATE_META, fileStateMeta, scanStatusMeta } from '@/features/file-management/utils/status'
import type { StatusMeta } from '@/features/file-management/utils/status'
import type {
  DemoFileRecord,
  DynamicWatermarkSpec,
  FileDlpInspectionResult,
  FileDrDrillRecord,
  FileListItem,
  FileReferenceType,
} from '@/types/file'

const FILE_STATE_OPTIONS = Object.entries(FILE_STATE_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

const ID_PATTERN = /^[1-9][0-9]{0,18}$/

const REFERENCE_TYPE_OPTIONS: { value: FileReferenceType; label: string }[] = [
  { value: 'ATTACHMENT', label: '附件' },
  { value: 'INLINE', label: '内嵌' },
  { value: 'EVIDENCE', label: '凭证' },
  { value: 'AVATAR', label: '头像' },
]

const REFERENCE_TYPE_LABELS: Record<FileReferenceType, string> = {
  ATTACHMENT: '附件',
  INLINE: '内嵌',
  EVIDENCE: '凭证',
  AVATAR: '头像',
}

interface DetailFormValues {
  resourceId: string
  resourceInstanceKey: string
  referenceType: FileReferenceType
  sortOrder?: number
}

interface DeleteFormValues {
  reason: string
}

function isLockedError(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 423 || error.code === 'IAM_FILE_LOCKED')
}

function renderStatusTag(
  status: string | null | undefined,
  lookup: (value?: string | null) => StatusMeta | undefined,
) {
  const meta = lookup(status)
  return meta ? <Tag color={meta.color}>{meta.label}</Tag> : <Tag>{status ?? '—'}</Tag>
}

export default function FileLibraryPage() {
  const { message } = App.useApp()

  const [uploaderOpen, setUploaderOpen] = useState(false)
  const [demoFiles, setDemoFiles] = useState<DemoFileRecord[]>([])
  const [demoNotice, setDemoNotice] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const [draftStatus, setDraftStatus] = useState<string | undefined>(undefined)
  const [draftRange, setDraftRange] = useState<[Dayjs, Dayjs] | null>(null)
  const [appliedStatus, setAppliedStatus] = useState<string | undefined>(undefined)
  const [appliedRange, setAppliedRange] = useState<[Dayjs, Dayjs] | null>(null)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const [detailTarget, setDetailTarget] = useState<FileListItem | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [previewError, setPreviewError] = useState<string | null>(null)
  const [createdReferences, setCreatedReferences] = useState<{ id: string; type: FileReferenceType }[]>([])
  const [detailForm] = Form.useForm<DetailFormValues>()

  const [deleteTarget, setDeleteTarget] = useState<FileListItem | null>(null)
  const [deleteForm] = Form.useForm<DeleteFormValues>()

  // V2.0: 动态水印、DLP 检测、冷归档与 S204 容灾恢复演练
  const user = useAuthStore((s) => s.user)
  const [watermarkSpec, setWatermarkSpec] = useState<DynamicWatermarkSpec | null>(null)
  const [dlpResult, setDlpResult] = useState<FileDlpInspectionResult | null>(null)
  const [drModalOpen, setDrModalOpen] = useState(false)
  const [drDrills, setDrDrills] = useState<FileDrDrillRecord[]>([])
  const [drLoading, setDrLoading] = useState(false)

  const handleLoadWatermark = async (fileId: string) => {
    if (!user?.tenantId) return
    try {
      const spec = await fetchDynamicWatermark(fileId, user.tenantId, user.userId)
      setWatermarkSpec(spec)
      message.success('已生成防篡改动态水印签名')
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const handleInspectDlp = async (record: FileListItem) => {
    if (!user?.tenantId) return
    try {
      const res = await inspectFileDlp(record.id, {
        tenantId: user.tenantId,
        fileName: record.fileName,
        compressionRatio: 12,
        nestedArchiveDepth: 1,
        contentSample: 'Enterprise IAM V2.0 Content Inspection Sample',
      })
      setDlpResult(res)
      message.success(`DLP 与压缩包炸弹深度检测完成：${res.dlpStatus}`)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const handleArchiveToggle = async (record: FileListItem, action: 'archive' | 'restore') => {
    if (!user?.tenantId) return
    try {
      const res =
        action === 'archive'
          ? await archiveFileToColdTier(record.id, user.tenantId)
          : await restoreFileFromColdTier(record.id, user.tenantId)
      message.success(
        action === 'archive'
          ? `文件已转入冷归档存储 (${res.storageTier ?? 'GLACIER_ARCHIVE'})`
          : `文件已从冷归档恢复 (${res.storageTier ?? 'STANDARD'})`,
      )
      setDetailTarget((prev) => (prev ? { ...prev, status: res.status } : prev))
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const handleOpenDrModal = async () => {
    setDrModalOpen(true)
    if (!user?.tenantId) return
    setDrLoading(true)
    try {
      const list = await listFileDrDrills(user.tenantId)
      setDrDrills(list)
    } catch {
      // 忽略首次空列表或离线
    } finally {
      setDrLoading(false)
    }
  }

  const handleRunDrDrill = async (scenarioCode: FileDrDrillRecord['scenarioCode']) => {
    if (!user?.tenantId) return
    setDrLoading(true)
    try {
      const record = await executeFileDrDrill({
        tenantId: user.tenantId,
        scenarioCode,
        targetRpoSeconds: 60,
        targetRtoSeconds: 300,
      })
      setDrDrills((prev) => [record, ...prev])
      message.success(`S204 容灾演练 (${scenarioCode}) 执行通过`)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setDrLoading(false)
    }
  }

  const completedTasks = useUploadQueue(
    useShallow((state) => state.tasks.filter((task) => task.status === 'completed' && task.fileId)),
  )

  const loadDemoFiles = useCallback(async () => {
    setLoading(true)
    try {
      const data = await listDemoFiles()
      setDemoFiles(data)
      setDemoNotice(null)
    } catch {
      setDemoFiles([])
      setDemoNotice(
        '演示文件列表接口不可用：后端可能未开启 iam.security.delegation.enabled 或服务未启动。上传与文件操作不受影响。',
      )
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadDemoFiles()
  }, [loadDemoFiles])

  const handleUploaded = useCallback(() => {
    void loadDemoFiles()
  }, [loadDemoFiles])

  const sessionItems = useMemo<FileListItem[]>(
    () =>
      completedTasks.flatMap((task) =>
        task.fileId
          ? [
              {
                id: task.fileId,
                fileName: task.fileName,
                contentType: task.contentType,
                size: task.fileSize,
                status: task.result?.status ?? 'VERIFYING',
                scanStatus: task.result?.scanStatus ?? 'PENDING',
                version: task.result?.version,
                source: 'session' as const,
                createdAt: task.createdAt,
                resourceId: task.resourceId,
                resourceInstanceKey: task.resourceInstanceKey,
              },
            ]
          : [],
      ),
    [completedTasks],
  )

  const mergedFiles = useMemo(() => {
    const byId = new Map<string, FileListItem>()
    for (const item of sessionItems) byId.set(item.id, item)
    for (const demo of demoFiles) {
      if (!byId.has(demo.id)) {
        byId.set(demo.id, {
          id: demo.id,
          fileName: demo.fileName,
          contentType: demo.contentType,
          size: demo.size,
          source: 'demo',
        })
      }
    }
    let list = [...byId.values()]
    if (appliedStatus) {
      list = list.filter((item) => item.status === appliedStatus)
    }
    if (appliedRange?.[0] && appliedRange?.[1]) {
      const from = appliedRange[0].startOf('day').valueOf()
      const to = appliedRange[1].endOf('day').valueOf()
      list = list.filter(
        (item) => item.createdAt !== undefined && item.createdAt >= from && item.createdAt <= to,
      )
    }
    return list
  }, [sessionItems, demoFiles, appliedStatus, appliedRange])

  const handleSearch = () => {
    setAppliedStatus(draftStatus)
    setAppliedRange(draftRange)
    setPage(1)
  }

  const handleReset = () => {
    setDraftStatus(undefined)
    setDraftRange(null)
    setAppliedStatus(undefined)
    setAppliedRange(null)
    setPage(1)
  }

  const resolveContext = (record: FileListItem): { resourceId: string; resourceInstanceKey: string } | null => {
    if (detailTarget?.id === record.id) {
      const values = detailForm.getFieldsValue()
      if (values.resourceId && values.resourceInstanceKey?.trim()) {
        return { resourceId: values.resourceId, resourceInstanceKey: values.resourceInstanceKey.trim() }
      }
    }
    if (record.resourceId && record.resourceInstanceKey) {
      return { resourceId: record.resourceId, resourceInstanceKey: record.resourceInstanceKey }
    }
    return null
  }

  const openDetail = useCallback(
    (record: FileListItem) => {
      setDetailTarget(record)
      setCreatedReferences([])
      setPreviewUrl(null)
      setPreviewError(null)
      detailForm.setFieldsValue({
        resourceId: record.resourceId ?? '1001',
        resourceInstanceKey: record.resourceInstanceKey ?? 'demo-console',
        referenceType: 'ATTACHMENT',
        sortOrder: 0,
      })
    },
    [detailForm],
  )

  const loadPreview = useCallback(async () => {
    const record = detailTarget
    if (!record) return
    const values = detailForm.getFieldsValue()
    if (!values.resourceId || !values.resourceInstanceKey?.trim()) {
      setPreviewError('请先填写资源 ID 与资源实例标识')
      return
    }
    setPreviewLoading(true)
    setPreviewError(null)
    try {
      const access = await authorizeFilePreview(record.id, {
        resourceId: values.resourceId,
        resourceInstanceKey: values.resourceInstanceKey.trim(),
        requestedMode: 'AUTO',
      })
      if (access.accessUrl) {
        setPreviewUrl(access.accessUrl)
      } else {
        setPreviewError(`授权成功（${access.mode}），但未返回可用的预览地址`)
      }
    } catch (error) {
      setPreviewError(
        isLockedError(error)
          ? '文件正处于安全锁定状态（423）：安全扫描通过后即可预览。'
          : resolveErrorMessage(error),
      )
    } finally {
      setPreviewLoading(false)
    }
  }, [detailTarget, detailForm])

  // 图片类型文件打开详情时自动获取沙箱预览
  useEffect(() => {
    if (detailTarget && isImageContentType(detailTarget.contentType)) {
      void loadPreview()
    }
  }, [detailTarget, loadPreview])

  const handleDownload = async (record: FileListItem): Promise<void> => {
    const context = resolveContext(record)
    if (!context) {
      message.warning('缺少资源上下文，请先在「详情」中补充资源 ID 与资源实例标识')
      return
    }
    try {
      const access = await authorizeFileDownload(record.id, { ...context, requestedMode: 'AUTO' })
      if (access.accessUrl) {
        window.open(access.accessUrl, '_blank', 'noopener,noreferrer')
        message.success('下载授权成功，已在新窗口打开下载地址')
      } else {
        message.warning(`授权成功（${access.mode}），但未返回下载地址`)
      }
    } catch (error) {
      if (isLockedError(error)) {
        message.warning('文件正处于安全锁定状态（423）：安全扫描通过后即可下载。')
      } else {
        message.error(resolveErrorMessage(error))
      }
    }
  }

  const handleCreateReference = async (values: DetailFormValues): Promise<void> => {
    if (!detailTarget) return
    try {
      const reference = await createFileReference(detailTarget.id, {
        resourceId: values.resourceId,
        resourceInstanceKey: values.resourceInstanceKey.trim(),
        referenceType: values.referenceType,
        sortOrder: values.sortOrder,
      })
      message.success(`引用创建成功（ID: ${reference.id}）`)
      setCreatedReferences((prev) => [...prev, { id: reference.id, type: values.referenceType }])
    } catch (error) {
      message.error(resolveErrorMessage(error))
    }
  }

  const handleDelete = async (values: DeleteFormValues): Promise<void> => {
    if (!deleteTarget) return
    try {
      const result = await deleteFile(deleteTarget.id, {
        version: deleteTarget.version ?? 0,
        reason: values.reason.trim(),
      })
      message.success(`文件已删除（状态：${fileStateMeta(result.status)?.label ?? result.status}）`)
      setDeleteTarget(null)
      deleteForm.resetFields()
      void loadDemoFiles()
    } catch (error) {
      if (isLockedError(error)) {
        message.warning('文件正处于安全锁定状态（423）：安全扫描完成前无法删除，请稍后再试。')
      } else {
        message.error(resolveErrorMessage(error))
      }
    }
  }

  const columns: ColumnsType<FileListItem> = [
    {
      title: '文件名',
      dataIndex: 'fileName',
      ellipsis: true,
      render: (_, record) => (
        <span className="flex items-center gap-2">
          <FileOutlined className="text-blue-500" />
          <span className="text-sm text-slate-700">{record.fileName ?? `文件 ${record.id}`}</span>
        </span>
      ),
    },
    {
      title: '文件 ID',
      dataIndex: 'id',
      width: 200,
      render: (id: string) => (
        <Typography.Text copyable={{ text: id }} className="text-xs">
          {id}
        </Typography.Text>
      ),
    },
    { title: '大小', dataIndex: 'size', width: 100, render: (size?: number) => humanFileSize(size) },
    {
      title: '类型',
      dataIndex: 'contentType',
      width: 140,
      ellipsis: true,
      render: (contentType?: string) => contentType ?? '—',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status?: string) => renderStatusTag(status, fileStateMeta),
    },
    {
      title: '扫描状态',
      dataIndex: 'scanStatus',
      width: 110,
      render: (scanStatus?: string) => renderStatusTag(scanStatus, scanStatusMeta),
    },
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (createdAt?: number) => formatDateTime(createdAt),
    },
    {
      title: '操作',
      key: 'actions',
      width: 210,
      render: (_, record) => (
        <Space size={0}>
          <Button type="link" size="small" icon={<ProfileOutlined />} onClick={() => openDetail(record)}>
            详情
          </Button>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openDetail(record)}>
            预览
          </Button>
          <Button type="link" size="small" icon={<DownloadOutlined />} onClick={() => void handleDownload(record)}>
            下载
          </Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => setDeleteTarget(record)}>
            删除
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-800">文件管理</h1>
          <p className="mt-1 text-sm text-slate-400">上传、预览、引用与安全扫描全流程管理，大文件自动分片直传</p>
        </div>
        <Space>
          <Button onClick={() => void handleOpenDrModal()}>S204 容灾恢复演练</Button>
          <Button icon={<ReloadOutlined />} onClick={() => void loadDemoFiles()}>
            刷新
          </Button>
          <Button type="primary" icon={<CloudUploadOutlined />} onClick={() => setUploaderOpen(true)}>
            上传文件
          </Button>
        </Space>
      </div>

      {demoNotice && (
        <Alert type="warning" showIcon closable message={demoNotice} onClose={() => setDemoNotice(null)} />
      )}

      <Card className="rounded-2xl">
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <Select
            allowClear
            placeholder="按状态筛选"
            style={{ width: 160 }}
            value={draftStatus}
            onChange={(value) => setDraftStatus(value)}
            options={FILE_STATE_OPTIONS}
          />
          <DatePicker.RangePicker
            allowEmpty={[false, false]}
            style={{ width: 260 }}
            value={draftRange}
            onChange={(values) => setDraftRange(values as [Dayjs, Dayjs] | null)}
            placeholder={['开始日期', '结束日期']}
          />
          <Space>
            <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
              查询
            </Button>
            <Button icon={<ReloadOutlined />} onClick={handleReset}>
              重置
            </Button>
          </Space>
        </div>
        <Table<FileListItem>
          rowKey="id"
          size="middle"
          columns={columns}
          dataSource={mergedFiles}
          loading={loading}
          scroll={{ x: 1200 }}
          pagination={{
            current: page,
            pageSize,
            defaultPageSize: 10,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100'],
            onChange: (p, s) => {
              setPage(p)
              setPageSize(s)
            },
            showTotal: (total) => `共 ${total} 个文件`,
          }}
          locale={{
            emptyText: (
              <Empty description="暂无文件，上传后即可在此管理">
                <Button type="primary" icon={<CloudUploadOutlined />} onClick={() => setUploaderOpen(true)}>
                  去上传
                </Button>
              </Empty>
            ),
          }}
        />
      </Card>

      <FileUploader open={uploaderOpen} onClose={() => setUploaderOpen(false)} onUploaded={handleUploaded} />

      <Drawer
        title={<span className="flex items-center gap-2"><FileOutlined /> 文件详情</span>}
        open={!!detailTarget}
        onClose={() => setDetailTarget(null)}
        width={720}
        extra={
          <Space>
            <Button
              key="download"
              type="primary"
              icon={<DownloadOutlined />}
              onClick={() => detailTarget && void handleDownload(detailTarget)}
            >
              下载
            </Button>
            <Button key="close" onClick={() => setDetailTarget(null)}>
              关闭
            </Button>
          </Space>
        }
      >
        {detailTarget && (
          <div className="space-y-4">
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="文件 ID" span={2}>
                <Typography.Text copyable={{ text: detailTarget.id }}>{detailTarget.id}</Typography.Text>
              </Descriptions.Item>
              <Descriptions.Item label="文件名">{detailTarget.fileName ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="大小">{humanFileSize(detailTarget.size)}</Descriptions.Item>
              <Descriptions.Item label="类型">{detailTarget.contentType ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="版本">{detailTarget.version ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="状态">{renderStatusTag(detailTarget.status, fileStateMeta)}</Descriptions.Item>
              <Descriptions.Item label="扫描状态">
                {renderStatusTag(detailTarget.scanStatus, scanStatusMeta)}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">{formatDateTime(detailTarget.createdAt)}</Descriptions.Item>
            </Descriptions>

            <FilePreview
              fileName={detailTarget.fileName}
              contentType={detailTarget.contentType}
              previewUrl={previewUrl}
              loading={previewLoading}
              error={previewError}
            />

            {isImageContentType(detailTarget.contentType) && (
              <div className="flex justify-end">
                <Button size="small" icon={<ReloadOutlined />} onClick={() => void loadPreview()}>
                  重新加载预览
                </Button>
              </div>
            )}

            <Divider plain className="my-2">
              V2.0 安全治理（动态水印 · DLP/压缩包炸弹检测 · 冷存储分层）
            </Divider>
            <Space wrap>
              <Button size="small" onClick={() => void handleLoadWatermark(detailTarget.id)}>
                签发防篡改动态水印
              </Button>
              <Button size="small" onClick={() => void handleInspectDlp(detailTarget)}>
                执行 DLP & 压缩包炸弹深度检测
              </Button>
              <Button size="small" onClick={() => void handleArchiveToggle(detailTarget, 'archive')}>
                转入 Glacier 冷归档
              </Button>
              <Button size="small" onClick={() => void handleArchiveToggle(detailTarget, 'restore')}>
                从冷归档恢复
              </Button>
            </Space>
            {watermarkSpec && watermarkSpec.fileId === detailTarget.id && (
              <Alert
                type="info"
                showIcon
                message={`动态水印文本: ${watermarkSpec.watermarkText} (${watermarkSpec.subText})`}
                description={`签名摘要: ${watermarkSpec.signature} · 旋转角: ${watermarkSpec.rotateAngle}° · 透明度: ${watermarkSpec.opacity}`}
              />
            )}
            {dlpResult && dlpResult.fileId === detailTarget.id && (
              <Alert
                type={dlpResult.dlpStatus === 'BLOCKED' ? 'error' : 'success'}
                showIcon
                message={`DLP 检测状态: ${dlpResult.dlpStatus} · 压缩包炸弹: ${dlpResult.archiveBombDetected ? '已拦截' : '正常'}`}
                description={dlpResult.detail}
              />
            )}

            <Divider plain className="my-2">
              资源上下文与引用
            </Divider>
            <Form<DetailFormValues>
              form={detailForm}
              layout="inline"
              onFinish={(values) => void handleCreateReference(values)}
              className="flex-wrap"
            >
              <Form.Item
                name="resourceId"
                label="资源 ID"
                rules={[
                  { required: true, message: '请输入资源 ID' },
                  { pattern: ID_PATTERN, message: 'ID 需为 1-19 位正整数' },
                ]}
              >
                <Input className="w-40" placeholder="例如 1001" />
              </Form.Item>
              <Form.Item
                name="resourceInstanceKey"
                label="资源实例"
                rules={[{ required: true, message: '请输入资源实例标识' }]}
              >
                <Input className="w-40" placeholder="demo-console" />
              </Form.Item>
              <Form.Item name="referenceType" label="引用类型">
                <Select className="w-28" options={REFERENCE_TYPE_OPTIONS} />
              </Form.Item>
              <Form.Item name="sortOrder" label="排序">
                <InputNumber min={0} max={100000} className="w-20" />
              </Form.Item>
              <Form.Item>
                <Button type="primary" htmlType="submit">
                  创建引用
                </Button>
              </Form.Item>
            </Form>
            {createdReferences.length > 0 && (
              <div className="flex flex-wrap gap-2">
                {createdReferences.map((reference) => (
                  <Tag key={reference.id} color="blue">
                    {REFERENCE_TYPE_LABELS[reference.type]} · ID {reference.id}
                  </Tag>
                ))}
              </div>
            )}
          </div>
        )}
      </Drawer>

      <Modal
        title="删除文件"
        open={!!deleteTarget}
        onCancel={() => setDeleteTarget(null)}
        onOk={() => deleteForm.submit()}
        okText="确认删除"
        okButtonProps={{ danger: true }}
      >
        <Alert
          type="warning"
          showIcon
          className="mb-4"
          message={`确认删除「${deleteTarget?.fileName ?? `文件 ${deleteTarget?.id ?? ''}`}」？该操作为逻辑删除，文件将进入已删除状态。`}
        />
        <Form<DeleteFormValues> form={deleteForm} layout="vertical" onFinish={(values) => void handleDelete(values)}>
          <Form.Item label="版本号（乐观锁）">
            <Input value={String(deleteTarget?.version ?? 0)} disabled />
          </Form.Item>
          <Form.Item name="reason" label="删除原因" rules={[{ required: true, message: '请填写删除原因' }]}>
            <Input.TextArea rows={3} maxLength={500} showCount placeholder="例如：包含过期附件，清理存储" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="S204 文件存储容灾与恢复演练 (File DR / Restore Drill)"
        open={drModalOpen}
        onCancel={() => setDrModalOpen(false)}
        footer={null}
        width={760}
      >
        <div className="space-y-4">
          <Space wrap>
            <Button
              type="primary"
              loading={drLoading}
              onClick={() => void handleRunDrDrill('SCENARIO_A_OBJECT_CORRUPTION')}
            >
              演练场景 A：单对象损坏恢复
            </Button>
            <Button
              loading={drLoading}
              onClick={() => void handleRunDrDrill('SCENARIO_B_BUCKET_LOSS')}
            >
              演练场景 B：Bucket 级跨副本切换
            </Button>
            <Button
              loading={drLoading}
              onClick={() => void handleRunDrDrill('SCENARIO_C_DB_PITR')}
            >
              演练场景 C：元数据 PITR 时间点对账
            </Button>
            <Button
              loading={drLoading}
              onClick={() => void handleRunDrDrill('SCENARIO_D_FULL_REGION_FAILOVER')}
            >
              演练场景 D：全可用区异地容灾接管
            </Button>
          </Space>
          <Table<FileDrDrillRecord>
            rowKey="id"
            size="small"
            loading={drLoading}
            dataSource={drDrills}
            pagination={{ pageSize: 5 }}
            columns={[
              { title: '演练场景', dataIndex: 'scenarioCode' },
              {
                title: 'RPO (实际/目标)',
                key: 'rpo',
                render: (_, r) => `${r.actualRpoSeconds}s / ${r.targetRpoSeconds}s`,
              },
              {
                title: 'RTO (实际/目标)',
                key: 'rto',
                render: (_, r) => `${r.actualRtoSeconds}s / ${r.targetRtoSeconds}s`,
              },
              { title: '校验对象数', dataIndex: 'verifiedObjects' },
              {
                title: '结果',
                dataIndex: 'status',
                render: (s: string) => (
                  <Tag color={s === 'PASSED' ? 'success' : 'error'}>{s}</Tag>
                ),
              },
            ]}
          />
        </div>
      </Modal>
    </div>
  )
}
