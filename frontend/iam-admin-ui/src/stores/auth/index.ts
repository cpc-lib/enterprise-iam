import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import { immer } from 'zustand/middleware/immer'
import * as authApi from '@/api/client/auth'
import type { ChangePasswordParams, LoginParams } from '@/api/client/auth'
import { resolveErrorMessage } from '@/api/client/errors'
import { useNavigationStore } from '@/stores/navigation'

export type AuthStatus = 'idle' | 'loading' | 'authenticated' | 'error'

/** 非敏感用户上下文（accessToken 仅存内存，会话恢复依赖 HttpOnly refresh cookie） */
export interface AuthUser {
  userId: string
  tenantId: string
  sessionId: string
  tenantCode?: string
  displayName?: string
}

export type PasswordChangeResult =
  | { outcome: 'changed' }
  | { outcome: 'failed'; message: string }
  | { outcome: 'reauth-required' }

interface AuthState {
  accessToken: string | null
  user: AuthUser | null
  status: AuthStatus
  error: string | null
  login: (params: LoginParams) => Promise<boolean>
  logout: () => Promise<void>
  /** 使用 HttpOnly refresh cookie 静默恢复会话（仅当本地存在用户上下文时接受） */
  refresh: () => Promise<boolean>
  changePassword: (params: ChangePasswordParams) => Promise<PasswordChangeResult>
  setAccessToken: (token: string) => void
  clearAuth: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    immer((set) => ({
      accessToken: null,
      user: null,
      status: 'idle',
      error: null,
      login: async (params) => {
        set((state) => {
          state.status = 'loading'
          state.error = null
        })
        try {
          const data = await authApi.login(params)
          await authApi.confirmLoginDelivery(data.sessionId, data.tenantId)
          set((state) => {
            state.accessToken = data.accessToken
            state.user = {
              userId: data.userId,
              tenantId: data.tenantId,
              sessionId: data.sessionId,
              tenantCode: params.tenantCode,
              displayName: params.identity,
            }
            state.status = 'authenticated'
          })
          useNavigationStore.getState().reset()
          void useNavigationStore.getState().load(true)
          return true
        } catch (error) {
          set((state) => {
            state.status = 'error'
            state.error = resolveErrorMessage(error)
          })
          return false
        }
      },
      logout: async () => {
        try {
          await authApi.logout()
        } catch {
          // 登出接口失败也继续清理本地状态
        }
        useNavigationStore.getState().reset()
        set((state) => {
          state.accessToken = null
          state.user = null
          state.status = 'idle'
          state.error = null
        })
      },
      refresh: async () => {
        try {
          const data = await authApi.refresh()
          let restored = false
          set((state) => {
            if (state.user) {
              state.accessToken = data.accessToken
              state.user.sessionId = data.sessionId
              state.status = 'authenticated'
              restored = true
            } else {
              state.status = 'idle'
            }
          })
          return restored
        } catch {
          set((state) => {
            state.status = 'idle'
          })
          return false
        }
      },
      changePassword: async (params) => {
        try {
          await authApi.changePassword(params)
        } catch (error) {
          const message = resolveErrorMessage(error)
          set((state) => {
            state.error = message
          })
          return { outcome: 'failed', message }
        }

        try {
          const data = await authApi.refresh()
          let refreshed = false
          set((state) => {
            if (state.user) {
              state.accessToken = data.accessToken
              state.user.sessionId = data.sessionId
              state.status = 'authenticated'
              state.error = null
              refreshed = true
            }
          })
          if (refreshed) {
            return { outcome: 'changed' }
          }
        } catch {
          // 密码已修改但当前 cookie 刷新失败，必须清空本地旧 access token。
        }

        useNavigationStore.getState().reset()
        set((state) => {
          state.accessToken = null
          state.user = null
          state.status = 'idle'
          state.error = null
        })
        return { outcome: 'reauth-required' }
      },
      setAccessToken: (token) =>
        set((state) => {
          state.accessToken = token
        }),
      clearAuth: () => {
        useNavigationStore.getState().reset()
        set((state) => {
          state.accessToken = null
          state.user = null
          state.status = 'idle'
          state.error = null
        })
      },
    })),
    {
      // 仅持久化非敏感用户上下文；accessToken 永不落盘
      name: 'iam-admin-user',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({ user: state.user }),
    },
  ),
)
