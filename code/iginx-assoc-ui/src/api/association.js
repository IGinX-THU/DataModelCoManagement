import { request } from './request'

export const fetchRuleList = () => request('/api/v1/rules')

export const fetchRuleDetail = (id) => request(`/api/v1/rules/${id}`)

export const createRule = (payload) => request('/api/v1/rules', {
  method: 'POST',
  body: JSON.stringify(payload)
})

export const updateRule = (id, payload) => request(`/api/v1/rules/${id}`, {
  method: 'PUT',
  body: JSON.stringify(payload)
})

export const updateRuleStatus = (id, enabled) => request(`/api/v1/rules/${id}/status`, {
  method: 'PUT',
  body: JSON.stringify({ enabled })
})

export const deleteRule = (id) => request(`/api/v1/rules/${id}`, {
  method: 'DELETE'
})

export const submitTask = (payload) => request('/api/v1/tasks/submit', {
  method: 'POST',
  body: JSON.stringify(payload)
})

export const stopTask = (id) => request(`/api/v1/tasks/${id}/stop`, {
  method: 'POST'
})

export const fetchTasks = (ruleId) => {
  const query = ruleId ? `?ruleId=${ruleId}` : ''
  return request(`/api/v1/tasks${query}`)
}
