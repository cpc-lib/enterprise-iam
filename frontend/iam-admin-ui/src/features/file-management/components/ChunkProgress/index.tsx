import { Progress } from 'antd'
import { cn } from '@/utils/cn'
import type { UploadTask } from '../../store/uploadQueue'

const MAX_VISIBLE_PARTS = 60

/** MULTIPART 分片进度：总进度条 + 分片块示意（失败块高亮） */
export default function ChunkProgress({ task }: { task: UploadTask }) {
  const partCount = task.partCount ?? 0
  if (!partCount) return null
  const visible = Math.min(partCount, MAX_VISIBLE_PARTS)
  const step = partCount / visible
  const doneParts = Math.min(task.nextPart - 1, partCount)

  return (
    <div className="mt-2">
      <Progress
        percent={Math.round((doneParts / partCount) * 100)}
        size="small"
        strokeColor="#2563EB"
        format={() => `分片 ${doneParts}/${partCount}`}
      />
      <div className="mt-1 flex flex-wrap gap-1">
        {Array.from({ length: visible }, (_, index) => {
          const partNumber = Math.min(Math.floor(index * step) + 1, partCount)
          const failed = task.failedPart === partNumber
          const done = partNumber < task.nextPart
          return (
            <span
              key={index}
              title={`分片 ${partNumber}${failed ? '（失败）' : ''}`}
              className={cn(
                'h-1.5 w-4 rounded-full transition-colors',
                failed ? 'bg-red-400' : done ? 'bg-blue-500' : 'bg-slate-200',
              )}
            />
          )
        })}
      </div>
      {partCount > visible && (
        <div className="mt-1 text-xs text-slate-400">分片较多，仅示意前 {visible} 个区块</div>
      )}
    </div>
  )
}
