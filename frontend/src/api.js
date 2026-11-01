const BASE = import.meta.env.VITE_API_BASE || ''

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  })
  if (!res.ok) {
    throw new Error(`请求失败（${res.status}）`)
  }
  const body = await res.json()
  if (body.code !== 0) {
    throw new Error(body.message || '操作失败')
  }
  return body.data
}

export function createRoom(payload) {
  return request('/api/rooms', { method: 'POST', body: JSON.stringify(payload) })
}

export function getRoom(code) {
  return request(`/api/rooms/${encodeURIComponent(code)}`)
}

export function joinRoom(code, nickname) {
  return request(`/api/rooms/${encodeURIComponent(code)}/join`, {
    method: 'POST',
    body: JSON.stringify({ nickname })
  })
}
