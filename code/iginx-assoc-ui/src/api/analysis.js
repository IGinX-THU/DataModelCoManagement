import { request } from './request'

export const fetchTaskSeries = (taskId, relative = false) => {
  const query = relative ? '?relative=true' : ''
  return request(`/api/v1/analysis/tasks/${taskId}/series${query}`)
}

export const compareTaskSeries = (taskIds, relative = false) => {
  return request('/api/v1/analysis/tasks/compare', {
    method: 'POST',
    body: JSON.stringify({
      taskIds,
      mode: relative ? 'relative' : 'absolute'
    })
  })
}

export const exportTaskPackage = (taskId, payload) => {
  return request(`/api/v1/analysis/tasks/${taskId}/export`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export const exportTaskReport = (taskId, payload) => {
  return request(`/api/v1/analysis/tasks/${taskId}/report`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}
