<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { connect, game, send } from '../game'
import { loadSession } from '../session'

const route = useRoute()
const router = useRouter()
const session = loadSession()

const snap = computed(() => game.snapshot)
const code = computed(() => String(route.params.code || '').toUpperCase())
const players = computed(() => snap.value?.players ?? [])
const mySlot = computed(() => game.slot || snap.value?.mySlot || session?.slot || 1)
const me = computed(() => players.value.find((p) => p.slot === mySlot.value))
const opponent = computed(() => players.value.find((p) => p.slot !== mySlot.value))
const opponentName = computed(() => opponent.value?.nickname || '对方')
const opponentSubmitted = computed(() => !!opponent.value?.submitted)

const word = ref('')
const now = ref(Date.now())
const clockOffset = ref(0)
const pending = ref(false)
const autoSubmitted = ref(false)
const reveal = ref(null)
const inputEl = ref(null)

let ticker = null
let revealTimer = null
let lastRevealedRound = 0
let lastRoundSeen = 0
let firstSync = true

const submitted = computed(() => pending.value || !!me.value?.submitted)

const remainMs = computed(() => {
  const s = snap.value
  if (!s || s.phase !== 'ROUND_OPEN') return 0
  return Math.max(0, s.deadlineAt - (now.value + clockOffset.value))
})
const secondsLeft = computed(() => Math.ceil(remainMs.value / 1000))
const urgent = computed(() => secondsLeft.value <= 5 && secondsLeft.value > 0)

const CIRC = 2 * Math.PI * 19
const dashOffset = computed(() => {
  const total = (snap.value?.roundSeconds || 20) * 1000
  const ratio = total > 0 ? Math.min(1, remainMs.value / total) : 0
  return CIRC * (1 - ratio)
})

const wordLength = computed(() => [...word.value].length)
const maxLen = computed(() => {
  const chars = [...word.value]
  if (!chars.length) return 12
  const allHan = chars.every((c) => /[\u4e00-\u9fa5]/.test(c))
  const allAscii = chars.every((c) => /[a-zA-Z0-9]/.test(c))
  if (allHan) return 12
  if (allAscii) return 20
  return 16
})
const repeated = computed(() => {
  const norm = word.value.trim().toLowerCase()
  if (!norm) return false
  return (snap.value?.usedWords || []).includes(norm)
})
const canSubmit = computed(
  () => snap.value?.phase === 'ROUND_OPEN' && !submitted.value && wordLength.value > 0 && !repeated.value
)
const hint = computed(() => {
  if (!word.value) return snap.value?.phase === 'ROUND_OPEN' ? '回车也能提交' : ''
  if (repeated.value) return '这个词本局出现过了，换一个'
  return `${wordLength.value} / ${maxLen.value}`
})

onMounted(() => {
  if (!session || session.code !== code.value) {
    router.replace('/')
    return
  }
  connect(session.token)
  ticker = setInterval(() => {
    now.value = Date.now()
  }, 120)
  lastRoundSeen = snap.value?.roundNo || 0
  nextTick(() => inputEl.value?.focus())
})

onUnmounted(() => {
  clearInterval(ticker)
  clearTimeout(revealTimer)
})

watch(
  () => game.snapshot,
  (value) => {
    if (!value) return
    clockOffset.value = (value.serverNow || Date.now()) - Date.now()

    if (value.status === 'FINISHED') {
      router.replace(`/room/${value.code}/result`)
      return
    }
    if (value.status === 'WAITING') {
      router.replace(`/room/${value.code}`)
      return
    }

    if (value.roundNo !== lastRoundSeen) {
      lastRoundSeen = value.roundNo
      word.value = ''
      pending.value = false
      autoSubmitted.value = false
      nextTick(() => inputEl.value?.focus())
    }

    if (pending.value && me.value?.submitted) {
      pending.value = false
    }

    if (firstSync) {
      firstSync = false
      lastRevealedRound = value.chain?.length ? value.chain[value.chain.length - 1].roundNo : 0
      // 断线重连回正在揭晓的回合时，也要补上揭晓层
      if (value.phase === 'ROUND_REVEAL' && value.chain?.length) {
        showReveal(value.chain[value.chain.length - 1])
      }
    } else {
      syncReveal(value)
    }
  }
)

// 提交被服务端拒绝时，解除锁定让玩家改词
watch(
  () => game.lastError,
  (err) => {
    if (err && err.at) {
      pending.value = false
      autoSubmitted.value = false
    }
  }
)

watch(secondsLeft, (value) => {
  if (value > 0 || !snap.value || snap.value.phase !== 'ROUND_OPEN') return
  if (submitted.value || autoSubmitted.value) return
  autoSubmitted.value = true
  if (word.value.trim()) {
    pending.value = true
    send('round.submit', { roundNo: snap.value.roundNo, word: word.value.trim() })
  }
})

function syncReveal(value) {
  const chain = value.chain || []
  const last = chain[chain.length - 1]
  if (!last || last.roundNo === lastRevealedRound) return
  lastRevealedRound = last.roundNo
  if (value.phase !== 'ROUND_REVEAL') return
  showReveal(last)
}

function showReveal(item) {
  reveal.value = item
  clearTimeout(revealTimer)
  revealTimer = setTimeout(() => {
    reveal.value = null
  }, 2000)
}

function onInput(event) {
  let next = (event.target.value || '').replace(/[^\u4e00-\u9fa5a-zA-Z0-9]/g, '')
  const chars = [...next]
  if (chars.length > maxLen.value) {
    next = chars.slice(0, maxLen.value).join('')
  }
  word.value = next
  event.target.value = next
}

function submit() {
  if (!canSubmit.value) return
  pending.value = true
  send('round.submit', { roundNo: snap.value.roundNo, word: word.value.trim() })
}

function wordOf(item, slot) {
  if (!item) return '—'
  const index = slot === 1 ? 0 : 1
  return item.words?.[index] || '弃权'
}

function giveUp() {
  if (window.confirm('确定放弃这一局吗？')) {
    send('game.giveup')
  }
}

const myName = computed(() => me.value?.nickname || '你')
</script>

<template>
  <div v-if="snap" class="page">
    <header class="play-top">
      <div class="round-no">
        第 {{ snap.roundNo }}<i>/ {{ snap.maxRounds }} 轮</i>
      </div>
      <div class="row" style="flex: 0 0 auto; gap: 8px">
        <div class="timer" :class="{ urgent }">
          <svg viewBox="0 0 44 44">
            <circle class="ring-bg" cx="22" cy="22" r="19" />
            <circle
              class="ring-fg"
              cx="22"
              cy="22"
              r="19"
              :stroke-dasharray="CIRC"
              :stroke-dashoffset="dashOffset"
            />
          </svg>
          <span>{{ snap.phase === 'ROUND_OPEN' ? secondsLeft : '—' }}</span>
        </div>
        <button class="btn btn-danger-text" style="font-size: 18px; padding: 0 6px" @click="giveUp">⋯</button>
      </div>
    </header>

    <div v-if="game.status !== 'online'" class="conn-banner">网络波动，正在重连…</div>

    <section class="anchor">
      <div class="section-title">锚点</div>
      <div class="anchor-words">
        <span v-for="w in snap.anchor" :key="w" class="anchor-chip">{{ w }}</span>
        <span v-if="!snap.anchor.length" class="muted" style="font-size: 13.5px">
          这局没有锚点，随便想一个词
        </span>
      </div>
    </section>

    <section class="chain">
      <div class="section-title">词链</div>
      <div class="chain-scroll">
        <div v-for="item in snap.chain" :key="item.roundNo" class="chain-item" :class="{ match: item.isMatch }">
          <div class="chain-round">R{{ item.roundNo }}</div>
          <div class="chain-words">
            <span>{{ wordOf(item, 1) }}</span>
            <span>{{ wordOf(item, 2) }}</span>
          </div>
        </div>
        <div v-if="!snap.chain.length" class="muted chain-empty">还没有历史，第一轮全凭直觉</div>
      </div>
    </section>

    <div class="think-hint">
      <p class="think-main">写一个你觉得<br />对方也会想到的词</p>
      <p class="muted think-sub">
        {{ snap.chain.length ? '不一样也没关系，两个词会成为下一轮的锚点' : '越常规的思路越容易撞上' }}
      </p>
    </div>

    <section class="input-area">
      <input
        ref="inputEl"
        class="word-input"
        :value="word"
        :disabled="submitted || snap.phase !== 'ROUND_OPEN'"
        :placeholder="submitted ? '已提交，等对方…' : '你想到了什么？'"
        maxlength="20"
        autocomplete="off"
        @input="onInput"
        @keyup.enter="submit"
      />
      <div class="hint" :class="{ error: repeated }">{{ hint }}</div>
      <button class="btn btn-primary btn-block" :disabled="!canSubmit" @click="submit">
        {{ submitted ? '已提交' : '提交' }}
      </button>
    </section>

    <footer class="status-row">
      <span class="dot" :class="{ on: submitted }"></span>
      <span>{{ me?.nickname || '你' }}{{ submitted ? ' 已提交' : ' 在输入' }}</span>
      <span class="sep">·</span>
      <span class="dot" :class="{ on: opponentSubmitted }"></span>
      <span>{{ opponentName }}{{ opponentSubmitted ? ' 已提交' : ' 思考中' }}</span>
    </footer>

    <transition name="fade">
      <div v-if="reveal" class="reveal-overlay">
        <div class="reveal-card" :class="reveal.isMatch ? 'match' : 'diff'">
          <div class="reveal-title">{{ reveal.isMatch ? '撞词了！' : '差一点' }}</div>
          <div class="reveal-words">
            <div class="reveal-word">
              <small>{{ myName }}</small>
              <b>{{ wordOf(reveal, mySlot) }}</b>
            </div>
            <div class="vs">{{ reveal.isMatch ? '=' : '≠' }}</div>
            <div class="reveal-word">
              <small>{{ opponentName }}</small>
              <b>{{ wordOf(reveal, mySlot === 1 ? 2 : 1) }}</b>
            </div>
          </div>
          <div class="reveal-sub">
            {{ reveal.isMatch ? `第 ${reveal.roundNo} 轮，你们想到了一起` : '两个词都成了下一轮的锚点' }}
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>
