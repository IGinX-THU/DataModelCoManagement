import { request } from './request'

export function fetchSystemLogs(params = {}) {
  const query = new URLSearchParams()
  if (params.limit) query.set('limit', params.limit)
  if (params.level) query.set('level', params.level)
  if (params.keyword) query.set('keyword', params.keyword)
  const queryString = query.toString()
  return request(`/api/v1/sys/logs${queryString ? `?${queryString}` : ''}`)
}

export function executeSqlConsole(payload) {
  return request('/api/v1/sys/sql', {
    method: 'POST',
    body: JSON.stringify(payload || {})
  })
}
