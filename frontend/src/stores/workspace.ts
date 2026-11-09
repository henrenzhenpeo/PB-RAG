import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useWorkspaceStore = defineStore('workspace', () => {
  const workspaceId = ref<string | null>(null)
  const workspaceName = ref<string | null>(null)

  function select(id: string, name?: string) {
    workspaceId.value = id
    workspaceName.value = name ?? null
  }

  return { workspaceId, workspaceName, select }
})
