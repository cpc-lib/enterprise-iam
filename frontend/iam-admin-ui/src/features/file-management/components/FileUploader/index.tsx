import { useEffect, useRef, useState } from 'react'
import { CloudUploadOutlined } from '@ant-design/icons'
import { App, Alert, Button, Form, Input, Modal, Upload } from 'antd'
import type { UploadProps } from 'antd'
import type { RcFile } from 'antd/es/upload'
import { useFileUploader } from '../../hooks/useFileUploader'
import { useUploadQueue } from '../../store/uploadQueue'
import UploadQueue from '../UploadQueue'

const ID_PATTERN = /^[1-9][0-9]{0,18}$/

interface UploaderFormValues {
  resourceId: string
  resourceInstanceKey: string
}

interface FileUploaderProps {
  open: boolean
  onClose: () => void
  /** 任一文件完成上传（进入 VERIFYING）后回调，用于刷新列表 */
  onUploaded?: () => void
}

/** 上传弹窗：资源上下文 + 文件选择 + 队列进度 */
export default function FileUploader({ open, onClose, onUploaded }: FileUploaderProps) {
  const { message } = App.useApp()
  const [form] = Form.useForm<UploaderFormValues>()
  const { enqueueFiles } = useFileUploader()
  const [fileList, setFileList] = useState<UploadProps['fileList']>([])
  const enqueuedKeys = useRef(new Set<string>())

  const tasks = useUploadQueue((state) => state.tasks)
  const completedSignature = tasks
    .filter((task) => task.status === 'completed')
    .map((task) => task.id)
    .join(',')

  useEffect(() => {
    if (open && completedSignature) onUploaded?.()
  }, [open, completedSignature, onUploaded])

  const handleUploadChange: NonNullable<UploadProps['onChange']> = (info) => {
    setFileList(info.fileList)
    const values = form.getFieldsValue()
    if (!values.resourceId || !values.resourceInstanceKey?.trim()) {
      message.warning('请先填写资源 ID 与资源实例标识')
      return
    }
    const fresh = info.fileList
      .map((item) => item.originFileObj)
      .filter((file): file is RcFile => !!file)
      .filter((file) => {
        const key = `${file.name}-${file.size}-${file.lastModified}`
        if (enqueuedKeys.current.has(key)) return false
        enqueuedKeys.current.add(key)
        return true
      })
    if (fresh.length) {
      enqueueFiles(fresh, {
        resourceId: values.resourceId,
        resourceInstanceKey: values.resourceInstanceKey.trim(),
      })
    }
  }

  return (
    <Modal
      title="上传文件"
      open={open}
      onCancel={onClose}
      width={640}
      footer={[<Button key="close" onClick={onClose}>关闭</Button>]}
    >
      <div className="space-y-4">
        <Alert
          type="info"
          showIcon
          message="开发环境提示"
          description="存储网关未接入时分片直传会失败并标记为「失败」；可点击「开发模拟完成」，用合成 etag 走通合并与安全扫描流程。"
        />
        <Form<UploaderFormValues>
          form={form}
          layout="inline"
          requiredMark={false}
          initialValues={{ resourceId: '1001', resourceInstanceKey: 'demo-console' }}
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
            <Input className="w-32" placeholder="例如 1001" />
          </Form.Item>
          <Form.Item
            name="resourceInstanceKey"
            label="资源实例"
            rules={[{ required: true, message: '请输入资源实例标识' }]}
          >
            <Input className="w-48" placeholder="例如 demo-console" />
          </Form.Item>
        </Form>
        <Upload.Dragger
          multiple
          showUploadList={false}
          fileList={fileList}
          beforeUpload={() => false}
          onChange={handleUploadChange}
        >
          <p className="ant-upload-drag-icon">
            <CloudUploadOutlined className="text-blue-500" />
          </p>
          <p className="ant-upload-text">点击或拖拽文件到此处上传</p>
          <p className="ant-upload-hint">单文件最大 50GB，将自动计算 SHA-256 指纹</p>
        </Upload.Dragger>
        <UploadQueue />
      </div>
    </Modal>
  )
}
