/**
 * 输入归一化：只保留「中文 / 英文字母 / 数字」，并按内容类型限制长度。
 *
 * 规则与服务端 JudgeService 保持一致：
 *   纯中文 1–12 字，纯英文数字 1–20 字符，中英混合 ≤16 字符。
 */

const WORD_PATTERN = /[^\u4e00-\u9fa5a-zA-Z0-9]/g
const HAN_ONLY = /^[\u4e00-\u9fa5]+$/
const ASCII_ONLY = /^[a-zA-Z0-9]+$/

export const MAX_HAN = 12
export const MAX_ASCII = 20
export const MAX_MIXED = 16

export function sanitizeWord(raw) {
  return (raw || '').replace(WORD_PATTERN, '')
}

/** 上限取决于「这段内容本身」，不能拿上一次的值来算 */
export function wordLimit(text) {
  if (!text) return MAX_HAN
  if (HAN_ONLY.test(text)) return MAX_HAN
  if (ASCII_ONLY.test(text)) return MAX_ASCII
  return MAX_MIXED
}

export function normalizeWord(raw) {
  const clean = sanitizeWord(raw)
  const limit = wordLimit(clean)
  return clean.length > limit ? clean.slice(0, limit) : clean
}

export function normalizeRoomCode(raw) {
  return (raw || '').replace(/[^a-zA-Z0-9]/g, '').toUpperCase().slice(0, 6)
}

/**
 * 把输入框里的值收敛成 normalize 之后的结果。
 *
 * 两个要点：
 * 1. 只有内容真的需要修正时才写 DOM —— 否则每敲一个字都赋值，光标会被甩到末尾，
 *    在中间插入字符就会跑到文末去（表现为「输入的东西被覆盖」）。
 * 2. 修正时按「光标之前被改动了多少」把光标放回去，而不是简单丢到末尾。
 *
 * @returns 归一化之后的值
 */
export function applyNormalized(el, normalize) {
  const raw = el.value
  const clean = normalize(raw)
  if (clean === raw) {
    return clean
  }

  const caret = typeof el.selectionStart === 'number' ? el.selectionStart : raw.length
  const caretClean = Math.min(normalize(raw.slice(0, caret)).length, clean.length)

  el.value = clean

  const doc = el.ownerDocument
  const focused = !doc || doc.activeElement === el
  if (focused && typeof el.setSelectionRange === 'function') {
    el.setSelectionRange(caretClean, caretClean)
  }
  return clean
}
