import http from '@/api/client/http'
import { newIdempotencyKey } from '@/api/identity/idempotency'
import type { JobDefinition, JobExecutionLog } from '@/types/job'

/** 查询全部注册后台作业定义：GET /admin/v1/jobs */
export async function listJobs(): Promise<JobDefinition[]> {
  const { data } = await http.get<JobDefinition[]>('/admin/v1/jobs')
  return data
}

/** 手动触发作业执行：POST /admin/v1/jobs/{jobCode}/trigger */
export async function triggerJob(jobCode: string): Promise<JobExecutionLog> {
  const { data } = await http.post<JobExecutionLog>(
    `/admin/v1/jobs/${encodeURIComponent(jobCode)}/trigger`,
    undefined,
    {
      headers: { 'Idempotency-Key': newIdempotencyKey() },
    },
  )
  return data
}

/** 暂停后台作业调度：POST /admin/v1/jobs/{jobCode}/pause */
export async function pauseJob(jobCode: string): Promise<JobDefinition> {
  const { data } = await http.post<JobDefinition>(
    `/admin/v1/jobs/${encodeURIComponent(jobCode)}/pause`,
    undefined,
    {
      headers: { 'Idempotency-Key': newIdempotencyKey() },
    },
  )
  return data
}

/** 恢复后台作业调度：POST /admin/v1/jobs/{jobCode}/resume */
export async function resumeJob(jobCode: string): Promise<JobDefinition> {
  const { data } = await http.post<JobDefinition>(
    `/admin/v1/jobs/${encodeURIComponent(jobCode)}/resume`,
    undefined,
    {
      headers: { 'Idempotency-Key': newIdempotencyKey() },
    },
  )
  return data
}

/** 查询作业历史执行记录：GET /admin/v1/jobs/{jobCode}/executions */
export async function listJobExecutions(jobCode: string): Promise<JobExecutionLog[]> {
  const { data } = await http.get<JobExecutionLog[]>(
    `/admin/v1/jobs/${encodeURIComponent(jobCode)}/executions`,
  )
  return data
}
