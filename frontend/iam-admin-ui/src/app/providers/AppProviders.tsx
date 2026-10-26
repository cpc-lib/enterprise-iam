import type { PropsWithChildren } from 'react'
import { BrowserRouter } from 'react-router-dom'
import { App as AntdApp, ConfigProvider, theme as antdTheme } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { AppErrorBoundary } from '@/app/error-boundary/AppErrorBoundary'
import '@/utils/dayjs'

const FONT_FAMILY =
  "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif"

export function AppProviders({ children }: PropsWithChildren) {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#2563EB',
          borderRadius: 8,
          fontFamily: FONT_FAMILY,
          colorBgLayout: '#F5F7FA',
        },
        algorithm: antdTheme.defaultAlgorithm,
        components: {
          Layout: {
            headerBg: '#ffffff',
            siderBg: '#ffffff',
            headerHeight: 64,
            headerPadding: '0 16px',
          },
          Menu: {
            itemBg: 'transparent',
            itemSelectedBg: '#EFF6FF',
            itemSelectedColor: '#2563EB',
            itemBorderRadius: 8,
            itemMarginInline: 10,
            itemHeight: 40,
            groupTitleColor: '#94A3B8',
            groupTitleFontSize: 12,
          },
        },
      }}
    >
      <AntdApp>
        <AppErrorBoundary>
          <BrowserRouter>{children}</BrowserRouter>
        </AppErrorBoundary>
      </AntdApp>
    </ConfigProvider>
  )
}
