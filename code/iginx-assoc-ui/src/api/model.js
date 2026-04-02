import { request, BASE_URL } from './request'

export const fetchModelList = () => request('/api/v1/models')

export const fetchModelDetail = (id) => request(`/api/v1/models/${id}`)

export const uploadModel = (formData) => request('/api/v1/models/upload', {
  method: 'POST',
  body: formData
})

export const updateModelProfile = (id, payload) => request(`/api/v1/models/${id}`, {
  method: 'PUT',
  body: JSON.stringify(payload)
})

export const deleteModelProfile = (id) => request(`/api/v1/models/${id}`, {
  method: 'DELETE'
})

export const deleteModelVersion = (assetId) => request(`/api/v1/models/assets/${assetId}`, {
  method: 'DELETE'
})

export const parseModelSchema = (formData) => request('/api/v1/models/parse', {
  method: 'POST',
  body: formData
})

export const listModelFunctions = (formData) => request('/api/v1/models/parse/functions', {
  method: 'POST',
  body: formData
})

export const parseModelSchemaByFunction = (formData) => request('/api/v1/models/parse/schema', {
  method: 'POST',
  body: formData
})

export const listAssetFunctions = (assetId) => request(`/api/v1/models/assets/${assetId}/functions`)

export const parseAssetSchemaByFunction = (assetId, functionName) =>
  request(`/api/v1/models/assets/${assetId}/functions/schema?functionName=${encodeURIComponent(functionName)}`)

export const getModelDownloadUrl = (assetId) => `${BASE_URL}/api/v1/models/assets/${assetId}/download`
