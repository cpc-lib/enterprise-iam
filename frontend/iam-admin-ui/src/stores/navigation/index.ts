import { createElement } from 'react'
import { create } from 'zustand'
import type { MenuProps } from 'antd'
import { fetchNavigationSchema } from '@/api/client/navigation'
import type { NavItem, NavSchema } from '@/constants/navigation'
import { NAV_ICON_MAP } from '@/constants/navigation'

export type MenuListItem = NavItem & { groupTitle?: string }

export type AntdMenuItem = Required<MenuProps>['items'][number]

interface NavigationState {
  schema: NavSchema
  status: 'idle' | 'loading' | 'ready' | 'error'
  load: (force?: boolean) => Promise<void>
  reset: () => void
}

export const useNavigationStore = create<NavigationState>()((set, get) => ({
  schema: [],
  status: 'idle',
  reset: () => set({ schema: [], status: 'idle' }),
  load: async (force = false) => {
    const { status } = get()
    if (!force && (status === 'loading' || status === 'ready')) return
    set({ status: 'loading' })
    try {
      const schema = await fetchNavigationSchema()
      set({ schema, status: 'ready' })
    } catch {
      set({ status: 'error' })
    }
  },
}))

/** 展平导航树（用于面包屑与 activeKey 匹配） */
export function flattenNavItems(schema: NavSchema): MenuListItem[] {
  const result: MenuListItem[] = []
  for (const node of schema) {
    if ('items' in node) {
      for (const item of node.items) {
        result.push({ ...item, groupTitle: node.title })
      }
    } else {
      result.push({ ...node })
    }
  }
  return result
}

export function buildMenuItems(schema: NavSchema): AntdMenuItem[] {
  return schema.map((node) => {
    if ('items' in node) {
      return {
        key: node.key,
        type: 'group' as const,
        label: node.title,
        children: node.items.map(toMenuItem),
      }
    }
    return toMenuItem(node)
  })
}

function toMenuItem(item: NavItem): AntdMenuItem {
  const Icon = item.icon ? NAV_ICON_MAP[item.icon] : undefined
  return {
    key: item.key,
    icon: Icon ? createElement(Icon) : undefined,
    label: item.title,
  }
}

/** 根据当前路径推导 activeKey（精确匹配优先，其次最长前缀匹配） */
export function getActiveKey(schema: NavSchema, pathname: string): string | undefined {
  const items = flattenNavItems(schema)
  const exact = items.find((item) => item.path === pathname)
  if (exact) return exact.key
  const prefixes = items.filter((item) => pathname.startsWith(`${item.path}/`))
  if (prefixes.length === 0) return undefined
  return prefixes.reduce((best, current) =>
    current.path.length > best.path.length ? current : best,
  ).key
}
