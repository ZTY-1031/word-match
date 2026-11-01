<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { connect, disconnect, game, send } from '../game'
import { clearSession, loadSession } from '../session'

const route = useRoute()
const router = useRouter()
const session = loadSession()

const code = computed(() => String(route.params.code || '').toUpperCase())
const snap = computed(() => game.snapshot)
const result = computed(() => snap.value?.result)
const isWin = computed(() => result.value?.type === 'WIN')
const chain = computed(() => (snap.value?.chain ? [...snap.value.chain].reverse() : []))
const mySlot = computed(() => game.slot || snap.value?.mySlot || session?.slot || 1)
const myName = computed(() => snap.value?.players?.find((p) => p.slot === mySlot.value)?.nickname || '你')
const otherName = computed(
  () => snap.value?.players?.find((p) => p.slot !== mySlot.value)?.nickname || '对方'
)

const headline = computed(() => {
  if (!result.value) return '本局未完成'
  if (result.value.type === 'WIN') return `第 ${result.value.rounds} 轮，你们想到了一起`
  if (result.value.type === 'ROUND_LIMIT') return '差一点点，下次一定'
  return `${result.value.byNickname || '对方'} 放弃了本局`
})

const subline = computed(() => {
  if (!result.value) return ''
  if (result.value.type === 'WIN') return `一共接了 ${result.value.rounds} 轮就撞上了`
  if (result.value.type === 'ROUND_LIMIT') return `已经接了 ${result.value.rounds} 轮，还是没撞上`
  return `停在 ${result.value.rounds} 轮`
})

onMounted(() => {
  if (!session || session.code !== code.value) {
    router.replace('/')
    return
  }
  connect(session.token)
})

watch(
  () => game.snapshot,
  (value) => {
    if (!value) return
    if (value.status === 'PLAYING') {
      router.replace(`/room/${value.code}/play`)
    } else if (value.status === 'WAITING') {
      router.replace(`/room/${value.code}`)
    }
  }
)

function again() {
  send('game.restart')
}

function home() {
  disconnect()
  clearSession()
  router.push('/')
}

function wordOf(item, slot) {
  const index = slot === 1 ? 0 : 1
  return item.words?.[index] || '弃权'
}
</script>

<template>
  <div v-if="snap" class="page">
    <header class="result-head">
      <div class="result-badge" :class="{ lose: !isWin }">
        {{ isWin ? result?.grade || 'S' : '—' }}
      </div>
      <h2>{{ headline }}</h2>
      <div class="result-meta">{{ subline }}</div>
    </header>

    <section class="card">
      <div class="section-title">这一局的词链</div>
      <div class="chain-list">
        <div v-for="item in chain" :key="item.roundNo" class="chain-row" :class="{ match: item.isMatch }">
          <span class="rno">R{{ item.roundNo }}</span>
          <div class="words">
            <span>{{ wordOf(item, 1) }}</span>
            <i>{{ item.isMatch ? '=' : '≠' }}</i>
            <span>{{ wordOf(item, 2) }}</span>
          </div>
          <span class="flag">
            {{ item.isMatch ? '撞词' : item.timedOut ? '超时' : '继续' }}
          </span>
        </div>
      </div>
      <p class="muted" style="font-size: 12.5px; margin: 12px 0 0">
        {{ mySlot === 1 ? `左边是 ${myName}，右边是 ${otherName}` : `左边是 ${otherName}，右边是 ${myName}` }}
      </p>
    </section>

    <div class="row">
      <button class="btn btn-primary" @click="again">再来一局</button>
      <button class="btn btn-ghost" @click="home">回首页</button>
    </div>
  </div>
</template>
