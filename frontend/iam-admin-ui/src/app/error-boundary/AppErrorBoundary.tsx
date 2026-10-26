import type { PropsWithChildren } from 'react'
import { Button, Result } from 'antd'
import { ErrorBoundary } from 'react-error-boundary'
import type { FallbackProps } from 'react-error-boundary'

function ErrorFallback({ error, resetErrorBoundary }: FallbackProps) {
  return (
    <div className="flex h-screen items-center justify-center bg-[#f5f7fa]">
      <Result
        status="warning"
        title="页面出现了一点问题"
        subTitle={error instanceof Error ? error.message : '发生了未知错误，请重试'}
        extra={
          <Button type="primary" onClick={resetErrorBoundary}>
            重试
          </Button>
        }
      />
    </div>
  )
}

export function AppErrorBoundary({ children }: PropsWithChildren) {
  return (
    <ErrorBoundary
      fallbackRender={ErrorFallback}
      onReset={() => window.location.reload()}
    >
      {children}
    </ErrorBoundary>
  )
}
