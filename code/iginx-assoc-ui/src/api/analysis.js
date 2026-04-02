import { request } from './request'

const buildQueryString = (options = {}) => {
  const params = new URLSearchParams()
  if (options.relative) {
    params.set('relative', 'true')
  }
  if (typeof options.downsample === 'boolean') {
    params.set('downsample', String(options.downsample))
  }
  if (options.aggregator) {
    params.set('aggregator', String(options.aggregator))
  }
  if (Number.isFinite(options.precisionMs) && Number(options.precisionMs) > 0) {
    params.set('precisionMs', String(Number(options.precisionMs)))
  }
  if (Number.isFinite(options.pageNum) && Number(options.pageNum) > 0) {
    params.set('pageNum', String(Number(options.pageNum)))
  }
  if (Number.isFinite(options.pageSize) && Number(options.pageSize) > 0) {
    params.set('pageSize', String(Number(options.pageSize)))
  }
  const query = params.toString()
  return query ? `?${query}` : ''
}

export const fetchTaskSeries = (taskId, options = {}) => {
  const query = buildQueryString(options)
  return request(`/api/v1/analysis/tasks/${taskId}/series${query}`)
}

export const compareTaskSeries = (taskIds, options = {}) => {
  return request('/api/v1/analysis/tasks/compare', {
    method: 'POST',
    body: JSON.stringify({
      taskIds,
      mode: options.relative ? 'relative' : 'absolute',
      downsample: options.downsample !== false,
      aggregator: options.aggregator || 'AVG',
      precisionMs: Number.isFinite(options.precisionMs) && Number(options.precisionMs) > 0
        ? Number(options.precisionMs)
        : undefined
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
