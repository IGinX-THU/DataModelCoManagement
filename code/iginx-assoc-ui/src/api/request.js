export const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'

export async function request(path, options = {}) {
  const isFormData = options.body instanceof FormData
  const headers = {
    ...(options.headers || {})
  }
  if (!isFormData) {
    headers['Content-Type'] = 'application/json'
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    headers,
    ...options
  })

  const json = await response.json().catch(() => null)
  if (!json) {
    throw new Error('服务响应解析失败')
  }

  if (json.code !== 200) {
    throw new Error(json.msg || '请求失败')
  }
  return json.data
}
