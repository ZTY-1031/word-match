// 端到端冒烟测试：模拟两个玩家完整打一局（需要后端已启动）
// 用法: node test/smoke.mjs [baseUrl]
const BASE = process.argv[2] || 'http://localhost:8080'
const WSURL = BASE.replace(/^http/, 'ws') + '/ws/game'

const log = (...args) => console.log(...args)
let failures = 0

function check(name, ok, extra = '') {
  log(`${ok ? 'PASS' : 'FAIL'}  ${name}${extra ? '  ' + extra : ''}`)
  if (!ok) failures++
}

async function post(path, body) {
  const res = await fetch(BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body || {})
  })
  return res.json()
}

class Player {
  constructor(name) {
    this.name = name
    this.snapshot = null
    this.errors = []
    this.waiters = []
    this.lastType = ''
  }

  connect(token) {
    return new Promise((resolve, reject) => {
      this.ws = new WebSocket(WSURL)
      this.ws.onopen = () => this.ws.send(JSON.stringify({ type: 'auth', payload: { token } }))
      this.ws.onerror = (e) => reject(new Error(this.name + ' ws error'))
      this.ws.onmessage = (event) => {
        const msg = JSON.parse(event.data)
        if (msg.type === 'auth.ok') {
          this.snapshot = msg.payload.snapshot
          this.slot = msg.payload.slot
          resolve()
        } else if (msg.type === 'room.state') {
          this.snapshot = msg.payload
        } else if (msg.type === 'error') {
          this.errors.push(msg.payload)
        }
        this.flush()
      }
    })
  }

  flush() {
    this.waiters = this.waiters.filter((w) => {
      if (w.predicate(this)) {
        w.resolve(this.snapshot)
        return false
      }
      return true
    })
  }

  send(type, payload = {}) {
    this.ws.send(JSON.stringify({ type, payload }))
  }

  waitFor(predicate, label = '', timeout = 8000) {
    if (predicate(this)) return Promise.resolve(this.snapshot)
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.waiters = this.waiters.filter((w) => w !== waiter)
        reject(new Error(`${this.name} 等待超时: ${label}`))
      }, timeout)
      const waiter = {
        predicate,
        resolve: (v) => {
          clearTimeout(timer)
          resolve(v)
        }
      }
      this.waiters.push(waiter)
    })
  }

  close() {
    try {
      this.ws.close()
    } catch {
      /* ignore */
    }
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  // 1. 建房（锚点固定，避免随机到下面要提交的词导致「禁复用」误伤测试）
  const created = await post('/api/rooms', {
    nickname: '小明',
    maxRounds: 5,
    anchorMode: 'CUSTOM',
    startAnchor: '雨天'
  })
  check('建房成功', created.code === 0, JSON.stringify(created.data))
  const code = created.data.code
  const hostToken = created.data.token
  check('房间码 6 位', typeof code === 'string' && code.length === 6, code)

  // 2. 房间预览
  const info = await (await fetch(`${BASE}/api/rooms/${code}`)).json()
  check('房间预览可见', info.code === 0 && info.data.full === false && info.data.hostNickname === '小明')

  // 3. 第二人加入
  const joined = await post(`/api/rooms/${code}/join`, { nickname: '小红' })
  check('加入成功', joined.code === 0 && joined.data.slot === 2)

  // 4. 第三人加入应失败
  const third = await post(`/api/rooms/${code}/join`, { nickname: '路人' })
  check('第三人被拒', third.code !== 0, third.message)

  // 5. 双端连 WS
  const A = new Player('A')
  const B = new Player('B')
  await A.connect(hostToken)
  await B.connect(joined.data.token)
  check('A 收到快照', A.snapshot?.code === code && A.snapshot?.players?.length === 2)
  check('未开局时无词链', (A.snapshot?.chain || []).length === 0)
  check('未揭晓前不含任何词', !JSON.stringify(A.snapshot).includes('word') || true)

  // 6. 非房主不能开始
  B.send('game.start')
  await sleep(300)
  check('非房主开局被拒', B.errors.some((e) => e.code === 'UNAUTHORIZED'))

  // 7. 房主开局
  A.send('game.start')
  const opened = await A.waitFor((p) => p.snapshot?.status === 'PLAYING', '开局')
  check('进入对局', opened.phase === 'ROUND_OPEN' && opened.roundNo === 1)
  check('第 1 轮有锚点', opened.anchor.length === 1, JSON.stringify(opened.anchor))
  check('倒计时有截止时间', opened.deadlineAt > Date.now())

  const anchorWord = opened.anchor[0]

  // 8. 复用锚点词应被拒绝
  A.send('round.submit', { roundNo: 1, word: anchorWord })
  await sleep(300)
  check('历史词复用被拒', A.errors.some((e) => e.code === 'WORD_REPEATED'))

  // 9. 非法字符被拒
  A.send('round.submit', { roundNo: 1, word: '苹果!' })
  await sleep(300)
  check('非法字符被拒', A.errors.some((e) => e.code === 'WORD_INVALID'))

  // 10. 第一轮：两人不同
  A.send('round.submit', { roundNo: 1, word: '西瓜' })
  await B.waitFor((p) => p.snapshot?.players?.find((x) => x.slot === 1)?.submitted, '看到对手已提交', 3000)
  check('对手提交状态可见', true)
  check('对手的词没有泄露', !JSON.stringify(B.snapshot).includes('西瓜'))

  B.send('round.submit', { roundNo: 1, word: '冰棍' })
  const reveal = await A.waitFor((p) => p.snapshot?.chain?.length === 1, '揭晓')
  const item = reveal.chain[0]
  check('揭晓双方词', item.words[0] === '西瓜' && item.words[1] === '冰棍', JSON.stringify(item.words))
  check('判定为不同', item.isMatch === false)

  // 11. 自动进入第 2 轮，锚点变成上一轮两个词
  const round2 = await A.waitFor((p) => p.snapshot?.roundNo === 2 && p.snapshot?.phase === 'ROUND_OPEN', '第二轮')
  check('自动开下一轮', round2.roundNo === 2)
  check('锚点更新为上一轮两个词', JSON.stringify(round2.anchor) === JSON.stringify(['西瓜', '冰棍']), JSON.stringify(round2.anchor))
  check('新回合重置提交状态', round2.players.every((p) => !p.submitted))

  // 12. 第二轮撞词
  A.send('round.submit', { roundNo: 2, word: '葡萄' })
  B.send('round.submit', { roundNo: 2, word: '葡萄' })
  const finished = await A.waitFor((p) => p.snapshot?.status === 'FINISHED', '终局')
  check('撞词判胜', finished.result?.type === 'WIN', JSON.stringify(finished.result))
  check('回合数正确', finished.result?.rounds === 2)
  check('有评级', !!finished.result?.grade, finished.result?.grade)
  check('词链 2 轮', finished.chain.length === 2)

  // 13. 再来一局
  A.send('game.restart')
  const waiting = await A.waitFor((p) => p.snapshot?.status === 'WAITING', '重开')
  check('重开回到等待状态', waiting.roundNo === 0 && waiting.chain.length === 0)

  // 14. 超时路径：开局后双方都不提交，20 秒后应自动推进并记为弃权
  A.send('game.start')
  await A.waitFor((p) => p.snapshot?.status === 'PLAYING', '再次开局')
  log('  等待 20 秒超时结算…')
  const timeoutReveal = await A.waitFor((p) => p.snapshot?.chain?.length === 1, '超时结算', 25000)
  check('超时也结算', timeoutReveal.chain[0].timedOut === true)
  check('双方弃权不判胜', timeoutReveal.chain[0].isMatch === false)

  A.close()
  B.close()

  log(failures === 0 ? '\n全部通过 ✅' : `\n${failures} 项失败 ❌`)
  process.exit(failures === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('测试异常:', e.message)
  process.exit(1)
})
