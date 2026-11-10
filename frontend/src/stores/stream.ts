import { ref } from 'vue'
import { defineStore } from 'pinia'

// 统一跟踪 SSE 连接，避免同一 streamId 被重复打开并支持集中取消。
export const useStreamStore = defineStore('stream', () => {
  const activeStreamId = ref<string | null>(null)

  function start(id: string) {
    activeStreamId.value = id
  }

  function stop() {
    activeStreamId.value = null
  }

  return { activeStreamId, start, stop }
})
