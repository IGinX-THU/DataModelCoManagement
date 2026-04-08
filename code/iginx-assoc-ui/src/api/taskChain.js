import { request } from './request'

export const fetchTaskChains = () => request('/api/v1/task-chains')

export const fetchTaskChainDetail = (id) => request(`/api/v1/task-chains/${id}`)

export const createTaskChain = (payload) => request('/api/v1/task-chains', {
  method: 'POST',
  body: JSON.stringify(payload)
})

export const updateTaskChain = (id, payload) => request(`/api/v1/task-chains/${id}`, {
  method: 'PUT',
  body: JSON.stringify(payload)
})

export const deleteTaskChain = (id) => request(`/api/v1/task-chains/${id}`, {
  method: 'DELETE'
})

export const fetchTaskChainRuleOptions = () => request('/api/v1/task-chains/compatible-rules')

export const submitTaskChainRun = (id, payload) => request(`/api/v1/task-chains/${id}/runs`, {
  method: 'POST',
  body: JSON.stringify(payload)
})

export const fetchTaskChainRuns = (chainId) => {
  const query = chainId ? `?chainId=${chainId}` : ''
  return request(`/api/v1/task-chains/runs${query}`)
}

export const fetchTaskChainRunDetail = (runId) => request(`/api/v1/task-chains/runs/${runId}`)

export const stopTaskChainRun = (runId) => request(`/api/v1/task-chains/runs/${runId}/stop`, {
  method: 'POST'
})

export const deleteTaskChainRun = (runId) => request(`/api/v1/task-chains/runs/${runId}`, {
  method: 'DELETE'
})
