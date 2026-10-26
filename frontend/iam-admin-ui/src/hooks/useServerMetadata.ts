import { useEffect, useState } from 'react'
import { fetchFieldSchema, fetchPageSchema } from '@/api/client/navigation'
import type { FieldDefinition, FieldSchema, PageSchema } from '@/api/client/navigation'

const pageSchemaCache = new Map<string, PageSchema | null>()
const fieldSchemaCache = new Map<string, FieldSchema | null>()

/**
 * 服务端驱动页面元数据 Hook（SPEC 20 & SPEC 47 §7）
 * 动态加载操作按钮集合与列配置，离线或非关键场景安全平滑降级（默认放行）。
 */
export function usePageSchema(pageKey: string) {
  const [schema, setSchema] = useState<PageSchema | null>(() => pageSchemaCache.get(pageKey) ?? null)
  const [loading, setLoading] = useState<boolean>(!pageSchemaCache.has(pageKey))

  useEffect(() => {
    if (pageSchemaCache.has(pageKey)) {
      setSchema(pageSchemaCache.get(pageKey) ?? null)
      setLoading(false)
      return
    }

    let isMounted = true
    setLoading(true)

    fetchPageSchema(pageKey)
      .then((res) => {
        pageSchemaCache.set(pageKey, res)
        if (isMounted) {
          setSchema(res)
          setLoading(false)
        }
      })
      .catch(() => {
        pageSchemaCache.set(pageKey, null)
        if (isMounted) {
          setSchema(null)
          setLoading(false)
        }
      })

    return () => {
      isMounted = false
    }
  }, [pageKey])

  /**
   * 检查指定 operationId 是否在服务端下发的页面操作集内。
   * 如果服务端元数据加载失败（降级模式），默认返回 true 以保证前端界面可用。
   */
  const hasOperation = (operationId: string): boolean => {
    if (!schema) return true
    return schema.operations.some((op) => op.operationId === operationId)
  }

  return {
    schema,
    loading,
    hasOperation,
    operations: schema?.operations ?? [],
    columns: schema?.columns ?? [],
  }
}

/**
 * 服务端驱动字段元数据 Hook（SPEC 20 & SPEC 47 §7）
 * 动态加载实体字段的 sensitive、maskable 等安全元数据。
 */
export function useFieldSchema(resourceType: string) {
  const [schema, setSchema] = useState<FieldSchema | null>(() => fieldSchemaCache.get(resourceType) ?? null)
  const [loading, setLoading] = useState<boolean>(!fieldSchemaCache.has(resourceType))

  useEffect(() => {
    if (fieldSchemaCache.has(resourceType)) {
      setSchema(fieldSchemaCache.get(resourceType) ?? null)
      setLoading(false)
      return
    }

    let isMounted = true
    setLoading(true)

    fetchFieldSchema(resourceType)
      .then((res) => {
        fieldSchemaCache.set(resourceType, res)
        if (isMounted) {
          setSchema(res)
          setLoading(false)
        }
      })
      .catch(() => {
        fieldSchemaCache.set(resourceType, null)
        if (isMounted) {
          setSchema(null)
          setLoading(false)
        }
      })

    return () => {
      isMounted = false
    }
  }, [resourceType])

  const getField = (fieldKey: string): FieldDefinition | undefined => {
    return schema?.fields.find((f) => f.fieldKey === fieldKey)
  }

  const isSensitive = (fieldKey: string): boolean => {
    return getField(fieldKey)?.sensitive ?? false
  }

  const isMaskable = (fieldKey: string): boolean => {
    return getField(fieldKey)?.maskable ?? false
  }

  return {
    schema,
    loading,
    getField,
    isSensitive,
    isMaskable,
    fields: schema?.fields ?? [],
  }
}
