export type JobStatus = 'ACTIVE' | 'PAUSED'

export type JobExecutionStatus = 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface JobDefinition {
  id: string
  jobCode: string
  jobName: string
  cronExpression: string
  status: JobStatus
  description: string
  createdAt: string
  updatedAt: string
  lastExecutionTime?: string
  nextExecutionTime?: string
}

export interface JobExecutionLog {
  id: string
  jobCode: string
  executionId: string
  status: JobExecutionStatus
  startTime: string
  endTime?: string
  itemsProcessed: number
  itemsConverged: number
  errorMessage?: string
  createdAt: string
}
