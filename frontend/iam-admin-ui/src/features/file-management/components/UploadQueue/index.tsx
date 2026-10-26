import { useState } from 'react'
import { Button, Empty } from 'antd'
import { resolveErrorMessage } from '@/api/client/errors'
import { useFileUploader } from '../../hooks/useFileUploader'
import { useUploadQueue } from '../../store/uploadQueue'
import UploadProgress from '../UploadProgress'

/** 上传任务队列：逐文件进度卡片（暂停/取消/重试/开发模拟完成） */
export default function UploadQueue() {
  const tasks = useUploadQueue((state) => state.tasks)
  const clearFinished = useUploadQueue((state) => state.clearFinished)
  const { pauseTask, resumeTask, cancelTask, retryTask, simulateComplete } = useFileUploader()
  const [simulatingId, setSimulatingId] = useState<string | null>(null)

  const handleSimulate = async (id: string): Promise<void> => {
    setSimulatingId(id)
    try {
      await simulateComplete(id)
    } catch (error) {
      useUploadQueue.getState().patchTask(id, { error: resolveErrorMessage(error) })
    } finally {
      setSimulatingId(null)
    }
  }

  if (!tasks.length) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无上传任务，选择文件即可开始" />
  }

  return (
    <div className="space-y-3">
      <div className="flex justify-end">
        <Button type="text" size="small" onClick={clearFinished}>
          清理已完成
        </Button>
      </div>
      {tasks.map((task) => (
        <UploadProgress
          key={task.id}
          task={task}
          simulating={simulatingId === task.id}
          onPause={pauseTask}
          onResume={resumeTask}
          onCancel={cancelTask}
          onRetry={retryTask}
          onSimulate={(id) => void handleSimulate(id)}
        />
      ))}
    </div>
  )
}
