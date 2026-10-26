import { Outlet } from 'react-router-dom'

/** 认证页布局：渐变浅色背景 + 居中卡片 + 品牌区 */
export function AuthLayout() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-blue-50 via-white to-indigo-100 px-4">
      <div className="w-full max-w-[400px]">
        <div className="mb-6 flex flex-col items-center gap-2">
          <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br from-blue-500 to-indigo-600 text-white shadow-lg shadow-blue-200">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="h-6 w-6"
            >
              <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
              <path d="m9 12 2 2 4-4" />
            </svg>
          </div>
          <div className="text-center">
            <div className="text-xl font-semibold text-slate-800">IAM 控制台</div>
            <div className="text-sm text-slate-400">统一身份与访问管理平台</div>
          </div>
        </div>
        <div className="rounded-2xl bg-white p-8 shadow-xl shadow-blue-100/70">
          <Outlet />
        </div>
      </div>
    </div>
  )
}
