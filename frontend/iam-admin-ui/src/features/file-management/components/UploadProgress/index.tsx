import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  FileOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  RedoOutlined,
} from '@ant-design/icons'
import { Button, Progress, Tag, Tooltip } from 'antd'
import type { UploadTask, UploadTaskStatus } from '../../store/uploadQueue'
import { humanFileSize } from '../../utils/format'
import { fileStateMeta } from '../../utils/status'
import ChunkProgress from '../ChunkProgress'

const STATUS_TEXT: Record<UploadTaskStatus, string> = {
  hashing: '计算指纹中…',
  creating: '创建上传会话…',
  uploading: '上传中',
  paused: '已暂停',
  completed: '已完成',
  failed: '失败',
  canceled: '已取消',
}

const STATUS_COLOR: Record<UploadTaskStatus, string> = {
  hashing: 'text-slate-400',
  creating: 'text-slate-400',
  uploading: 'text-blue-500',
  paused: 'text-amber-500',
  completed: 'text-emerald-500',
  failed: 'text-red-500',
  canceled: 'text-slate-400',
}

interface UploadProgressProps {
  task: UploadTask
  simulating: boolean
  onPause: (id: string) => void
  onResume: (id: string) => void
  onCancel: (id: string) => void
  onRetry: (id: string) => void
  onSimulate: (id: string) => void
}

/** 单个上传任务卡片：进度、状态与操作（暂停/继续/取消/重试/开发模拟完成） */
export default function UploadProgress({
  task,
  simulating,
  onPause,
  onResume,
  onCancel,
  onRetry,
  onSimulate,
}: UploadProgressProps) {
  const percent = task.status === 'hashing' ? task.hashProgress : task.progress
  const active = task.status === 'hashing' || task.status === 'creating' || task.status === 'uploading'
  const resultMeta = task.status === 'completed' ? fileStateMeta(task.result?.status) : undefined

  return (
    <div className="rounded-xl border border-slate-100 bg-white px-4 py-3 shadow-sm">
      <div className="flex items-center gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-500">
          <FileOutlined />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="truncate text-sm font-medium text-slate-700">{task.fileName}</span>
            <span className="shrink-0 text-xs text-slate-400">{humanFileSize(task.fileSize)}</span>
          </div>
          <div className="mt-0.5 flex items-center gap-2 text-xs">
            <span className={STATUS_COLOR[task.status]}>{STATUS_TEXT[task.status]}</span>
            {resultMeta && <Tag color={resultMeta.color}>{resultMeta.label}</Tag>}
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          {task.status === 'uploading' && (
            <Tooltip title="暂停">
              <Button type="text" size="small" icon={<PauseCircleOutlined />} onClick={() => onPause(task.id)} />
            </Tooltip>
          )}
          {task.status === 'paused' && (
            <Tooltip title="继续">
              <Button type="text" size="small" icon={<PlayCircleOutlined />} onClick={() => onResume(task.id)} />
            </Tooltip>
          )}
          {task.status === 'failed' && (
            <Button type="link" size="small" icon={<RedoOutlined />} onClick={() => onRetry(task.id)}>
              重试
            </Button>
          )}
          {task.status === 'failed' && task.mode === 'MULTIPART' && (
            <Tooltip title="用合成 etag 直接请求合并，仅用于开发环境走通流程">
              <Button type="link" size="small" loading={simulating} onClick={() => onSimulate(task.id)}>
                开发模拟完成
              </Button>
            </Tooltip>
          )}
          {active && (
            <Tooltip title="取消">
              <Button type="text" size="small" danger icon={<CloseCircleOutlined />} onClick={() => onCancel(task.id)} />
            </Tooltip>
          )}
          {task.status === 'completed' && <CheckCircleOutlined className="text-emerald-500" />}
        </div>
      </div>
      <Progress
        className="mt-1"
        percent={percent}
        size="small"
        strokeColor="#2563EB"
        status={task.status === 'failed' ? 'exception' : task.status === 'completed' ? 'success' : 'active'}
      />
      {task.status === 'failed' && task.error && (
        <div className="mt-1 rounded-lg bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-600">
          {task.error}
        </div>
      )}
      {task.mode === 'MULTIPART' && task.partCount ? <ChunkProgress task={task} /> : null}
    </div>
  )
}
