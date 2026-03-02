import { request } from './request'

export const fetchDashboardSummary = () => {
  return request('/api/v1/dashboard/summary')
}
