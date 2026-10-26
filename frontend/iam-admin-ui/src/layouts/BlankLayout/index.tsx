import { Outlet } from 'react-router-dom'

/** 空白布局：直接透传子路由 */
export function BlankLayout() {
  return <Outlet />
}
