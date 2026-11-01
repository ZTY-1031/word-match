// 输入归一化与光标的单元测试：node test/input.test.mjs
import {
  MAX_ASCII,
  MAX_HAN,
  MAX_MIXED,
  applyNormalized,
  normalizeRoomCode,
  normalizeWord,
  wordLimit
} from '../frontend/src/input.js'

let failures = 0
function eq(name, actual, expected) {
  const ok = JSON.stringify(actual) === JSON.stringify(expected)
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${ok ? '' : `\n      实际=${JSON.stringify(actual)} 期望=${JSON.stringify(expected)}`}`)
  if (!ok) failures++
}


// ---------------- 上限规则 ----------------
eq('空串上限为中文上限', wordLimit(''), MAX_HAN)
eq('纯中文上限 12', wordLimit('夏天'), MAX_HAN)
eq('纯英文上限 20', wordLimit('hello'), MAX_ASCII)
eq('纯数字上限 20', wordLimit('2024'), MAX_ASCII)
eq('中英混合上限 16', wordLimit('hello夏'), MAX_MIXED)

eq('13 个汉字被截到 12', normalizeWord('一二三四五六七八九十一二三'), '一二三四五六七八九十一二')
eq('21 个字母被截到 20', normalizeWord('a'.repeat(21)), 'a'.repeat(20))
eq('17 个混合字符被截到 16', normalizeWord('a'.repeat(10) + '一二三四五六七'), 'a'.repeat(10) + '一二三四五六')

// ---------------- 回归：上限曾按「旧值」计算 ----------------
// 12 个汉字（此时上限 12）后再打一个字母：内容变成混合，上限应为 16，字母必须留下
eq('12 汉字后追加字母不被吃掉', normalizeWord('一二三四五六七八九十一二' + 'a'), '一二三四五六七八九十一二a')
// 20 个字母后再打汉字：混合上限 16，超额部分才截断（而不是整段丢弃）
eq('20 字母后追加汉字按混合规则截断', normalizeWord('a'.repeat(20) + '一'), 'a'.repeat(MAX_MIXED))

// ---------------- 字符过滤 ----------------
eq('空格被过滤', normalizeWord('西 瓜'), '西瓜')
eq('标点被过滤', normalizeWord('苹果！'), '苹果')
eq('emoji 被过滤', normalizeWord('猫🐱'), '猫')
eq('换行被过滤', normalizeWord('hello\nworld'), 'helloworld')

// ---------------- 房间码 ----------------
eq('小写转大写', normalizeRoomCode('abc123'), 'ABC123')
eq('过滤非字母数字', normalizeRoomCode('ab-c 12'), 'ABC12')
eq('截断 6 位', normalizeRoomCode('abcdefgh'), 'ABCDEF')


// ---------------- 光标保持 ----------------
// 模拟浏览器的行为：给 value 赋值时光标会跑到末尾
function fakeInput(value, caret, focused = true) {
  const el = {
    _value: value,
    selectionStart: caret,
    writes: 0,
    ownerDocument: null,
    setSelectionRange(start) {
      this.selectionStart = start
    }
  }
  Object.defineProperty(el, 'value', {
    get() {
      return this._value
    },
    set(v) {
      this._value = v
      this.writes++
      this.selectionStart = v.length
    }
  })
  if (!focused) {
    el.ownerDocument = { activeElement: {} }
  }
  return el
}

// 内容不需要修正时：一次 DOM 都不写，光标原地不动（否则中间插入会跳到末尾）
let el = fakeInput('夏天', 1)
let out = applyNormalized(el, normalizeWord)
eq('无需修正时返回原值', out, '夏天')
eq('无需修正时不写 DOM', el.writes, 0)
eq('无需修正时光标不动', el.selectionStart, 1)

// 内容需要修正时：写回，并把光标按「前面少了几个字符」平移
el = fakeInput('西 瓜', 2) // 光标在空格后面
out = applyNormalized(el, normalizeWord)
eq('过滤空白后的值', out, '西瓜')
eq('有改动时写了一次 DOM', el.writes, 1)
eq('光标平移到过滤后的位置', el.selectionStart, 1)

// 超长截断：光标在末尾时保持末尾
el = fakeInput('一二三四五六七八九十一二三', 15)
out = applyNormalized(el, normalizeWord)
eq('超长截断后的值', out, '一二三四五六七八九十一二')
eq('超长截断后光标在末尾', el.selectionStart, 12)

// 非聚焦状态下不碰选区（避免抢焦点）
el = fakeInput('西 瓜', 2, false)
applyNormalized(el, normalizeWord)
eq('未聚焦时也修正内容', el._value, '西瓜')

console.log(failures === 0 ? '\n全部通过 ✅' : `\n${failures} 项失败 ❌`)
process.exit(failures === 0 ? 0 : 1)
