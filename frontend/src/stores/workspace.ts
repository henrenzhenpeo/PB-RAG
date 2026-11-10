import { ref } from 'vue'
import { defineStore } from 'pinia'

// 保存当前选择的逻辑知识分组，供布局与各业务页面共享。
export const useWorkspaceStore = defineStore('workspace', () => {
  const workspaceId = ref<string | null>(null)
  const workspaceName = ref<string | null>(null)

  function select(id: string, name?: string) {
    workspaceId.value = id
    workspaceName.value = name ?? null
  }

  return { workspaceId, workspaceName, select }
})
