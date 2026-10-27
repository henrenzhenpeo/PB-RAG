import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useWorkspaceStore = defineStore('workspace', () => {
  const workspaceId = ref<string | null>(null)

  function select(id: string) {
    workspaceId.value = id
  }

  return { workspaceId, select }
})
