import { Alert, Empty, Spin } from 'antd'
import { isImageContentType } from '../../utils/format'

interface FilePreviewProps {
  fileName?: string
  contentType?: string
  previewUrl?: string | null
  loading: boolean
  error?: string | null
}

/** 详情弹窗内的预览区：图片内嵌展示，其余类型给出下载引导 */
export default function FilePreview({ fileName, contentType, previewUrl, loading, error }: FilePreviewProps) {
  if (loading) {
    return (
      <div className="flex h-64 flex-col items-center justify-center gap-3 rounded-xl bg-slate-50">
        <Spin size="large" />
        <span className="text-sm text-slate-400">正在获取沙箱预览…</span>
      </div>
    )
  }
  if (error) {
    return <Alert type="warning" showIcon message="预览不可用" description={error} />
  }
  if (previewUrl && isImageContentType(contentType)) {
    return (
      <div className="flex justify-center rounded-xl bg-slate-50 p-3">
        <img
          src={previewUrl}
          alt={fileName ?? '文件预览'}
          className="max-h-96 rounded-lg object-contain"
        />
      </div>
    )
  }
  return (
    <div className="rounded-xl bg-slate-50 py-10">
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description={
          previewUrl
            ? '已获取预览授权，请点击下载查看内容'
            : '该文件类型暂不支持在线预览，可点击下载查看'
        }
      />
    </div>
  )
}
