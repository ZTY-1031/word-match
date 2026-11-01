import { reactive } from 'vue'

/** 全局对局状态：服务端快照的唯一投影 */
export const game = reactive({
  status: 'idle', // idle | connecting | online | offline
  snapshot: null,
  playerId: '',
  slot: 0,
  /** 最近一次服务端错误，页面可据此解除提交锁定 */
  lastError: null
})

/** 轻量提示条 */
export const toast = reactive({ text: '', at: 0 })

export function showToast(text) {
  toast.text = text
  toast.at = Date.now()
}

let socket = null
let token = ''
let manualClose = false
let attempts = 0
let retryTimer = null
let heartbeat = null

function wsUrl() {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${location.host}/ws/game`
}

function raw(message) {
  if (!socket || socket.readyState !== WebSocket.OPEN) return false
  socket.send(JSON.stringify(message))
  return true
}

export function connect(nextToken) {
  if (!nextToken) return
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) && token === nextToken) {
    return
  }
  token = nextToken
  manualClose = false
  attempts = 0
  open()
}

function open() {
  clearTimeout(retryTimer)
  game.status = 'connecting'
  try {
    socket = new WebSocket(wsUrl())
  } catch {
    game.status = 'offline'
    scheduleRetry()
    return
  }

  socket.onopen = () => {
    attempts = 0
    game.status = 'online'
    raw({ type: 'auth', payload: { token } })
    startHeartbeat()
  }

  socket.onmessage = (event) => {
    let message
    try {
      message = JSON.parse(event.data)
    } catch {
      return
    }
    handle(message)
  }

  socket.onclose = () => {
    stopHeartbeat()
    if (manualClose) {
      game.status = 'idle'
      return
    }
    game.status = 'offline'
    scheduleRetry()
  }

  socket.onerror = () => {}
}

function handle(message) {
  const payload = message.payload || {}
  if (message.type === 'auth.ok') {
    game.playerId = payload.playerId
    game.slot = payload.slot
    game.snapshot = payload.snapshot
    game.status = 'online'
    return
  }
  if (message.type === 'room.state') {
    game.snapshot = payload
    return
  }
  if (message.type === 'error') {
    game.lastError = { code: payload.code || 'ERROR', message: payload.message || '操作失败', at: Date.now() }
    showToast(payload.message || '操作失败')
  }
}

function scheduleRetry() {
  if (manualClose) return
  attempts += 1
  if (attempts > 5) {
    showToast('连接断开，请刷新页面重试')
    return
  }
  const delay = [1000, 2000, 4000, 8000, 8000][attempts - 1]
  retryTimer = setTimeout(open, delay)
}

function startHeartbeat() {
  stopHeartbeat()
  heartbeat = setInterval(() => raw({ type: 'ping', payload: {} }), 15000)
}

function stopHeartbeat() {
  if (heartbeat) {
    clearInterval(heartbeat)
    heartbeat = null
  }
}

export function send(type, payload = {}) {
  if (!raw({ type, payload })) {
    showToast('连接中断，正在重连…')
    return false
  }
  return true
}

export function disconnect() {
  manualClose = true
  clearTimeout(retryTimer)
  stopHeartbeat()
  if (socket) {
    socket.close()
    socket = null
  }
  token = ''
  game.snapshot = null
  game.status = 'idle'
  game.playerId = ''
  game.slot = 0
}
