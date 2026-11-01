<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createRoom, getRoom, joinRoom } from '../api'
import { disconnect, showToast } from '../game'
import { applyNormalized, normalizeRoomCode } from '../input'
import { clearSession, loadSession, saveSession } from '../session'

const route = useRoute()
const router = useRouter()
const nickname = ref(loadSession()?.nickname || '')
const maxRounds = ref(15)
const anchorMode = ref('RANDOM')
const startAnchor = ref('')
const code = ref('')
const loading = ref(false)
const lastRoom = ref(loadSession())

const roundOptions = [5, 10, 15, 20]
const anchorOptions = [
  { key: 'RANDOM', label: '随机' },
  { key: 'CUSTOM', label: '我来定' },
  { key: 'NONE', label: '无锚点' }
]

function ensureNickname() {
  const name = nickname.value.trim()
  if (!name) {
    showToast('先给自己取个昵称')
    return null
  }
  saveSession({ nickname: name })
  return name
}

async function create() {
  const name = ensureNickname()
  if (!name || loading.value) return
  loading.value = true
  try {
    const data = await createRoom({
      nickname: name,
      maxRounds: maxRounds.value,
      anchorMode: anchorMode.value,
      startAnchor: anchorMode.value === 'CUSTOM' ? startAnchor.value.trim() : null
    })
    persist(data, name)
    router.push(`/room/${data.code}`)
  } catch (e) {
    showToast(e.message)
  } finally {
    loading.value = false
  }
}

async function join() {
  const name = ensureNickname()
  if (!name || loading.value) return
  const roomCode = code.value.trim().toUpperCase()
  if (roomCode.length !== 6) {
    showToast('房间码是 6 位')
    return
  }
  loading.value = true
  try {
    const info = await getRoom(roomCode)
    if (info.status === 'NOT_FOUND') {
      showToast('房间不存在或已过期')
      return
    }
    if (info.full) {
      showToast('房间已满（2/2）')
      return
    }
    const data = await joinRoom(roomCode, name)
    persist(data, name)
    router.push(`/room/${data.code}`)
  } catch (e) {
    showToast(e.message)
  } finally {
    loading.value = false
  }
}

function persist(data, name) {
  disconnect()
  saveSession({
    code: data.code,
    playerId: data.playerId,
    token: data.token,
    nickname: name,
    slot: data.slot
  })
}

function onCodeInput(event) {
  // 统一收口：过滤非法字符 + 转大写 + 截断 6 位，并在真的改动时把光标放回原处
  code.value = applyNormalized(event.target, normalizeRoomCode)
}

function backToHome() {
  disconnect()
  clearSession()
  lastRoom.value = null
  showToast('已离开房间')
}

function resume() {
  if (!lastRoom.value?.code) return
  router.push(`/room/${lastRoom.value.code}`)
}

onMounted(() => {
  const invited = route.query.code
  if (typeof invited === 'string' && invited.trim()) {
    code.value = invited.trim().toUpperCase().slice(0, 6)
    showToast('填好昵称就能进房间')
  }
})
</script>

<template>
  <div class="page">
    <header class="hero">
      <div class="brand-mark">接词</div>
      <h1>接词挑战</h1>
      <p>两个人各自写下一个词，撞上了就算赢</p>
    </header>

    <div v-if="lastRoom" class="card" style="display: flex; align-items: center; gap: 12px">
      <div style="flex: 1">
        <div style="font-weight: 600">上一局还在</div>
        <div class="muted" style="font-size: 13px">房间 {{ lastRoom.code }} · {{ lastRoom.nickname }}</div>
      </div>
      <button class="btn btn-ghost" style="height: 38px" @click="resume">回到房间</button>
      <button class="btn btn-danger-text" @click="backToHome">离开</button>
    </div>

    <section class="card">
      <div class="field">
        <label class="label">你的昵称</label>
        <input v-model="nickname" class="input" maxlength="8" placeholder="随便取一个" />
      </div>

      <div class="field">
        <label class="label">回合上限</label>
        <div class="segmented">
          <button
            v-for="item in roundOptions"
            :key="item"
            :class="{ active: maxRounds === item }"
            @click="maxRounds = item"
          >
            {{ item }} 轮
          </button>
        </div>
      </div>

      <div class="field">
        <label class="label">起始锚点</label>
        <div class="segmented">
          <button
            v-for="item in anchorOptions"
            :key="item.key"
            :class="{ active: anchorMode === item.key }"
            @click="anchorMode = item.key"
          >
            {{ item.label }}
          </button>
        </div>
        <input
          v-if="anchorMode === 'CUSTOM'"
          v-model="startAnchor"
          class="input"
          style="margin-top: 10px"
          maxlength="12"
          placeholder="比如：夏天"
        />
      </div>

      <button class="btn btn-primary btn-block" :disabled="loading" @click="create">
        创建房间
      </button>
    </section>

    <section class="card">
      <label class="label">有房间码？直接进</label>
      <div class="row" style="align-items: center">
        <input
          :value="code"
          class="input code-input"
          inputmode="latin"
          placeholder="ABC123"
          @input="onCodeInput"
          @keyup.enter="join"
        />
        <button class="btn btn-ghost" style="flex: 0 0 96px" :disabled="loading" @click="join">加入</button>
      </div>
    </section>

    <details class="rules">
      <summary>怎么玩？</summary>
      <ol>
        <li>每轮双方各写一个词，<b>互相看不见</b>。</li>
        <li>两人都提交后立即揭晓：<b>词一样就赢了</b>。</li>
        <li>不一样则两个词都成为下一轮的锚点，继续接。</li>
        <li>每轮限时 20 秒，超时按弃权算。</li>
        <li>本局出现过的词不能再用——所以别想着抄上家的答案。</li>
      </ol>
    </details>
  </div>
</template>
