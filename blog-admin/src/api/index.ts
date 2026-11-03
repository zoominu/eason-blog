import { get, post } from './http'
import type { DashboardData, User } from './types'

export const authApi = {
  login: (email: string, password: string) => post<User>('/auth/login', { email, password }),
  logout: () => post<void>('/auth/logout'),
  me: () => get<User>('/auth/me')
}

export const dashboardApi = {
  overview: () => get<DashboardData>('/admin/dashboard')
}
