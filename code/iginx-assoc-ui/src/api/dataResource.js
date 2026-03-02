import { request, BASE_URL } from './request'

const buildFormData = (payload, file) => {
  const formData = new FormData()
  formData.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }))
  formData.append('file', file)
  return formData
}

export function importTimeSeries(payload, file) {
  return request('/api/v1/data/import/ts', {
    method: 'POST',
    body: buildFormData(payload, file)
  })
}

export function importStructured(payload, file) {
  return request('/api/v1/data/import/struct', {
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

export function createStorageGroup(payload) {
  return request('/api/v1/data/structures/storage-groups', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function dropStorageGroup(payload) {
  return request('/api/v1/data/structures/storage-groups/drop', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function createMeasurement(payload) {
  return request('/api/v1/data/structures/measurements', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function dropMeasurement(payload) {
  return request('/api/v1/data/structures/measurements/drop', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function createTable(payload) {
  return request('/api/v1/data/structures/tables', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function dropTable(payload) {
  return request('/api/v1/data/structures/tables/drop', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function fetchTableColumns(sourceId, schema, table) {
  return request(`/api/v1/data/sources/${sourceId}/tables/${schema}/${table}/columns`)
}

export function buildDownloadUrl(path) {
  if (!path) return ''
  if (path.startsWith('http')) return path
  return `${BASE_URL}${path}`
}
