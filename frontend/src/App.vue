<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { toast } from './game'

const visible = ref(false)
let hideTimer = null

watch(
  () => toast.at,
  () => {
    if (!toast.text) return
    visible.value = true
    clearTimeout(hideTimer)
    hideTimer = setTimeout(() => {
      visible.value = false
    }, 2400)
  }
)

onUnmounted(() => clearTimeout(hideTimer))

const text = computed(() => toast.text)
</script>

<template>
  <router-view />

  <transition name="toast">
    <div v-if="visible && text" class="toast">{{ text }}</div>
  </transition>
</template>
