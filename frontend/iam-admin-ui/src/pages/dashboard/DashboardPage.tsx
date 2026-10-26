import {
  ArrowRightOutlined,
  AuditOutlined,
  SafetyCertificateOutlined,
  ScheduleOutlined,
  ShareAltOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Card, Col, Row, Statistic } from 'antd'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getRoles } from '@/api/identity/roles'
import { getUserCount } from '@/api/identity/users'
import { useAuthStore } from '@/stores/auth'
import { cn } from '@/utils/cn'
import { dayjs } from '@/utils/dayjs'

const STATS = [
  {
    key: 'users',
    title: '用户',
    icon: UserOutlined,
    path: '/identity/users',
    color: 'from-blue-500 to-sky-400',
  },
  {
    key: 'roles',
    title: '角色',
    icon: SafetyCertificateOutlined,
    path: '/identity/roles',
    color: 'from-violet-500 to-fuchsia-400',
  },
  {
    key: 'teams',
    title: '团队',
    icon: TeamOutlined,
    path: '/organization/teams',
    color: 'from-emerald-500 to-teal-400',
  },
  {
    key: 'shares',
    title: '分享',
    icon: ShareAltOutlined,
    path: '/sharing/shares',
    color: 'from-amber-500 to-orange-400',
  },
]

const QUICK_LINKS = [
  {
    path: '/identity/users',
    title: '创建用户',
    description: '为成员开通账号身份',
    icon: UserOutlined,
    color: 'bg-blue-50 text-blue-500',
  },
  {
    path: '/identity/roles',
    title: '配置角色',
    description: '定义角色与授权边界',
    icon: SafetyCertificateOutlined,
    color: 'bg-violet-50 text-violet-500',
  },
  {
    path: '/organization/teams',
    title: '组建团队',
    description: '按团队协作与共享资源',
    icon: TeamOutlined,
    color: 'bg-emerald-50 text-emerald-500',
  },
  {
    path: '/audit/logs',
    title: '查看审计',
    description: '追踪关键操作记录',
    icon: AuditOutlined,
    color: 'bg-amber-50 text-amber-500',
  },
  {
    path: '/system/jobs',
    title: '调度中心',
    description: '管理后台作业与对账执行',
    icon: ScheduleOutlined,
    color: 'bg-indigo-50 text-indigo-500',
  },
]

export default function DashboardPage() {
  const navigate = useNavigate()
  const user = useAuthStore((s) => s.user)
  const today = dayjs().format('YYYY年MM月DD日 dddd')
  const [statsCount, setStatsCount] = useState<Record<string, number>>({
    users: 0,
    roles: 0,
    teams: 0,
    shares: 0,
  })

  useEffect(() => {
    async function loadStats() {
      const tenantId = user?.tenantId
      if (!tenantId) return
      try {
        const isSuperAdmin = tenantId === '10001'
        const targetTenant = isSuperAdmin ? undefined : tenantId
        const [usersCount, rolesList] = await Promise.allSettled([
          getUserCount(targetTenant),
          getRoles(targetTenant),
        ])
        setStatsCount((prev) => ({
          ...prev,
          users: usersCount.status === 'fulfilled' ? usersCount.value : prev.users,
          roles: rolesList.status === 'fulfilled' ? rolesList.value.length : prev.roles,
        }))
      } catch {
        // safe fallback
      }
    }
    void loadStats()
  }, [user?.tenantId])

  return (
    <div className="space-y-6">
      <div className="rounded-2xl bg-gradient-to-r from-blue-500 to-indigo-500 px-8 py-7 text-white shadow-lg shadow-blue-100">
        <div className="text-2xl font-semibold">你好，{user?.tenantCode ?? '用户'}</div>
        <div className="mt-1 text-sm text-blue-100">
          欢迎回到 {user?.tenantCode ? `${user.tenantCode} 租户` : 'IAM 控制台'} · {today}
        </div>
      </div>

      <Row gutter={[16, 16]}>
        {STATS.map((stat) => {
          const Icon = stat.icon
          const count = statsCount[stat.key] ?? 0
          return (
            <Col key={stat.key} xs={24} sm={12} lg={6}>
              <Card
                hoverable
                className="rounded-2xl"
                onClick={() => navigate(stat.path)}
                aria-label={`查看${stat.title}`}
              >
                <div className="flex items-center justify-between">
                  <Statistic title={stat.title} value={count} />
                  <div
                    className={cn(
                      'flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br text-white',
                      stat.color,
                    )}
                  >
                    <Icon className="text-xl" />
                  </div>
                </div>
              </Card>
            </Col>
          )
        })}
      </Row>

      <Card title="快速开始" className="rounded-2xl">
        <div className="divide-y divide-slate-50">
          {QUICK_LINKS.map((link) => {
            const Icon = link.icon
            return (
              <button
                key={link.path}
                type="button"
                onClick={() => navigate(link.path)}
                className="flex w-full cursor-pointer items-center gap-4 rounded-lg px-2 py-3 text-left transition hover:bg-slate-50"
              >
                <span
                  className={cn('flex h-10 w-10 items-center justify-center rounded-xl', link.color)}
                >
                  <Icon className="text-lg" />
                </span>
                <span className="flex-1">
                  <span className="block text-sm font-medium text-slate-700">{link.title}</span>
                  <span className="block text-xs text-slate-400">{link.description}</span>
                </span>
                <ArrowRightOutlined className="text-slate-300" />
              </button>
            )
          })}
        </div>
      </Card>
    </div>
  )
}
