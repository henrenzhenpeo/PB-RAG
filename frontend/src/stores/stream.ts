import { ref } from 'vue'
import { defineStore } from 'pinia'

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
