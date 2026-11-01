/**
 * 复制文本。
 * 优先用 navigator.clipboard，但它在非安全上下文（比如手机访问 http://192.168.x.x）不可用，
 * 所以保留 textarea + execCommand 兜底，否则局域网里点「复制」会静默失败。
 */
export async function copyText(text) {
  if (!text) return false

  if (window.isSecureContext && navigator.clipboard) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // 落到下面的兜底
    }
  }

  try {
    const el = document.createElement('textarea')
    el.value = text
    el.setAttribute('readonly', '')
    el.style.position = 'fixed'
    el.style.top = '-9999px'
    el.style.opacity = '0'
    document.body.appendChild(el)
    el.select()
    el.setSelectionRange(0, text.length)
    const ok = document.execCommand('copy')
    document.body.removeChild(el)
    return ok
  } catch {
    return false
  }
}
