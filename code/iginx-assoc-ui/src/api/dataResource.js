import { request, BASE_URL } from './request'

const buildFormData = (payload, file) => {
  const formData = new FormData()
  formData.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }))
  formData.append('file', file)
  return formData
}

export function importData(payload, file) {
  return request('/api/v1/data/import', {
    method: 'POST',
    body: buildFormData(payload, file)
  })
}

export function exportData(payload) {
  return request('/api/v1/data/export', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function fetchResourceTree() {
  return request('/api/v1/data/resources/tree')
}

export function fetchExportTask(taskId) {
  return request(`/api/v1/data/export/tasks/${taskId}`)
}

export function queryTimeSeries(payload) {
  return request('/api/v1/data/query/ts', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function queryStructured(payload) {
  return request('/api/v1/data/query/struct', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

/**
 * 查询结构化表结构（仅列信息，不查数据）。
 * 后端会按 IGinX 语义执行 SHOW COLUMNS rt.xxx.*。
 */
export function queryStructuredSchema(tablePath) {
  const encodedPath = encodeURIComponent(String(tablePath || '').trim())
  return request(`/api/v1/data/query/struct/schema?tablePath=${encodedPath}`)
}

export function deleteTimeSeries(payload) {
  return request('/api/v1/data/ts/delete', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function createStructuredRow(payload) {
  return request('/api/v1/data/struct/rows', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateStructuredRow(payload) {
  return request('/api/v1/data/struct/rows', {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function deleteStructuredRow(payload) {
  return request('/api/v1/data/struct/rows', {
    method: 'DELETE',
    body: JSON.stringify(payload)
  })
}

export function deleteColumns(payload) {
  return request('/api/v1/data/columns/delete', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function buildDownloadUrl(path) {
  if (!path) return ''
  if (path.startsWith('http')) return path
  return `${BASE_URL}${path}`
}
