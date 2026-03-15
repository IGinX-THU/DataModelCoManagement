import { request } from './request'

export function fetchDataSourcePage(params = {}) {
  const query = new URLSearchParams()
  if (params.name) query.set('name', params.name)
  if (params.sourceType) query.set('sourceType', params.sourceType)
  query.set('pageNum', String(params.pageNum || 1))
  query.set('pageSize', String(params.pageSize || 100))
  return request(`/api/v1/data/sources?${query.toString()}`)
}

export function createDataSource(payload) {
  return request('/api/v1/data/sources', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function testDataSourceConnection(payload) {
  return request('/api/v1/data/sources/test-connection', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function fetchDataSourceStructure(id) {
  return request(`/api/v1/data/sources/${id}/structure`)
}

export function fetchDataSourceDetail(id, limit = 200) {
  return request(`/api/v1/data/sources/${id}/detail?limit=${limit}`)
}
