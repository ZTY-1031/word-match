<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { connect, disconnect, game, send, showToast } from '../game'
import { copyText } from '../clipboard'
import { clearSession, loadSession } from '../session'

const route = useRoute()
const router = useRouter()
const session = loadSession()

const code = computed(() => String(route.params.code || '').toUpperCase())
const snap = computed(() => game.snapshot)
const players = computed(() => (snap.value ? snap.value.players : []))
const me = computed(() => players.value.find((p) => p.slot === session?.slot))
const isHost = computed(() => (snap.value ? snap.value.host : false))
const ready = computed(() => players.value.length >= 2)

const inviteLink = computed(() => `${location.origin}${location.pathname}#/?code=${code.value}`)
const copied = ref('')

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
    } else if (value.status === 'FINISHED') {
      router.replace(`/room/${value.code}/result`)
    }
  }
)

function startGame() {
  send('game.start')
}

async function copyCode() {
  const ok = await copyText(code.value)
  if (ok) {
    copied.value = 'code'
    showToast(`房间码已复制：${code.value}`)
  } else {
    showToast(`复制失败，请手动记下房间码：${code.value}`)
  }
}

async function copyInvite() {
  const ok = await copyText(inviteLink.value)
  if (ok) {
    copied.value = 'link'
    showToast('邀请链接已复制')
  } else {
    showToast(`复制失败，手动发一下：${inviteLink.value}`)
  }
}

function leave() {
  disconnect()
  clearSession()
  router.push('/')
}
</script>

<template>
  <div class="page">
    <header style="display: flex; align-items: center; justify-content: space-between; padding: 4px 2px">
      <button class="btn btn-ghost" style="height: 34px; padding: 0 12px; font-size: 13px" @click="leave">
        ← 离开
      </button>
      <span class="muted" style="font-size: 12.5px">{{ ready ? '两人已就位' : '等待朋友加入' }}</span>
    </header>

    <div v-if="game.status !== 'online'" class="conn-banner">正在连接服务端…</div>

    <section class="card" style="text-align: center">
      <div class="label" style="margin-bottom: 0">房间码</div>
      <button class="room-code" type="button" title="点击复制房间码" @click="copyCode">{{ code }}</button>
      <p class="muted" style="font-size: 13px; margin: 0 0 14px">
        点房间码可直接复制，也可以把邀请链接发给朋友
      </p>
      <div class="row">
        <button class="btn btn-ghost" @click="copyCode">
          {{ copied === 'code' ? '已复制 ✓' : '复制房间码' }}
        </button>
        <button class="btn btn-primary" @click="copyInvite">
          {{ copied === 'link' ? '已复制 ✓' : '复制邀请链接' }}
        </button>
      </div>
    </section>

    <section class="card">
      <div class="section-title">席位</div>
      <div class="seats">
        <div class="seat">
          <div class="avatar">{{ (me?.nickname || session?.nickname || '你')[0] }}</div>
          <div>
            <div class="seat-name">{{ me?.nickname || session?.nickname || '你' }}</div>
            <div class="muted" style="font-size: 12px">{{ isHost ? '房主' : '玩家' }}</div>
          </div>
          <span class="seat-tag">{{ game.status === 'online' ? '在线' : '连接中' }}</span>
        </div>

        <div v-if="players.length >= 2" class="seat">
          <div class="avatar" style="background: linear-gradient(135deg, #ff8a9b, #ff6fae)">
            {{ players.find((p) => p.slot !== session?.slot)?.nickname?.[0] }}
          </div>
          <div>
            <div class="seat-name">{{ players.find((p) => p.slot !== session?.slot)?.nickname }}</div>
            <div class="muted" style="font-size: 12px">刚才进来</div>
          </div>
          <span class="seat-tag">就位</span>
        </div>
        <div v-else class="seat empty">
          <div class="avatar ghost">?</div>
          <div>等待朋友加入…</div>
        </div>
      </div>
    </section>

    <section class="card">
      <div class="section-title">本局设置</div>
      <div class="row" style="font-size: 13.5px; color: var(--ink-soft)">
        <div>回合上限 <b>{{ snap?.maxRounds ?? '—' }}</b></div>
        <div>
          起始锚点
          <b v-if="snap?.anchorMode === 'RANDOM'">随机</b>
          <b v-else-if="snap?.anchorMode === 'CUSTOM'">自定义</b>
          <b v-else>无</b>
        </div>
      </div>
    </section>

    <button v-if="isHost" class="btn btn-primary btn-block" :disabled="!ready" @click="startGame">
      {{ ready ? '开始游戏' : '等待朋友加入' }}
    </button>
    <div v-else class="muted" style="text-align: center; font-size: 13.5px">
      等房主点开始，马上就好
    </div>
  </div>
</template>
