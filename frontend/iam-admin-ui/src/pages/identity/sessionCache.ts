/**
 * 会话级记录缓存：Phase-01/02 后端仅开放命令接口（无列表查询端点），
 * 页面用 sessionStorage 保存本次会话内创建/变更的实体，便于操作员回查 ID 与版本。
 */

/** 读取会话列表（损坏或缺失时返回空数组） */
export function loadSessionList<T>(key: string): T[] {
  try {
    const raw = sessionStorage.getItem(key)
    const parsed: unknown = raw ? JSON.parse(raw) : null
    return Array.isArray(parsed) ? (parsed as T[]) : []
  } catch {
    return []
  }
}

/** 覆盖保存会话列表（存储不可用时静默忽略，仅影响回查展示） */
export function saveSessionList<T>(key: string, items: T[]): void {
  try {
    sessionStorage.setItem(key, JSON.stringify(items))
  } catch {
    // 忽略写入失败（隐私模式 / 容量超限）
  }
}
