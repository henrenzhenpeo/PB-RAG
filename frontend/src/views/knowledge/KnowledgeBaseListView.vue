<script setup lang="ts">
// 当前 Workspace 的知识库列表页，负责加载与创建知识库。
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { MessagePlugin } from 'tdesign-vue-next'
import { createKnowledgeBase, listKnowledgeBases, type KnowledgeBase } from '@/api/knowledgeBases'
import { errorMessage } from '@/api/http'

const route = useRoute()
const workspaceId = computed(() => String(route.params.workspaceId ?? ''))
const knowledgeBases = ref<KnowledgeBase[]>([])
const loading = ref(false)
const creating = ref(false)
const dialogVisible = ref(false)
const form = ref({ name: '', description: '' })

async function load() {
  if (!workspaceId.value) return
  loading.value = true
  try {
    knowledgeBases.value = await listKnowledgeBases(workspaceId.value)
  } catch (error) {
    void MessagePlugin.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function submitCreate() {
  const name = form.value.name.trim()
  if (!name) return
  creating.value = true
  try {
    const created = await createKnowledgeBase(workspaceId.value, {
      name,
      description: form.value.description.trim() || undefined,
    })
    knowledgeBases.value.push(created)
    form.value = { name: '', description: '' }
    dialogVisible.value = false
    void MessagePlugin.success('知识库创建成功')
  } catch (error) {
    void MessagePlugin.error(errorMessage(error))
  } finally {
    creating.value = false
  }
}

onMounted(load)
watch(workspaceId, load)
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <span class="eyebrow">KNOWLEDGE LIBRARY</span>
        <h1>知识库</h1>
        <p>集中管理资料，进入知识库即可查看文档与入库进度。</p>
      </div>
      <t-button theme="primary" @click="dialogVisible = true">新建知识库</t-button>
    </header>

    <t-loading :loading="loading">
      <div v-if="knowledgeBases.length" class="grid">
        <router-link
          v-for="item in knowledgeBases"
          :key="item.id"
          class="knowledge-card"
          :to="`/workspaces/${workspaceId}/knowledge-bases/${item.id}`"
        >
          <div class="card-icon">▦</div>
          <h2>{{ item.name }} <span class="card-arrow">↗</span></h2>
          <p>{{ item.description || '暂无描述' }}</p>
          <span class="card-foot">查看文档与任务 <span>→</span></span>
        </router-link>
      </div>
      <t-empty v-else-if="!loading" description="当前 Workspace 还没有知识库" />
    </t-loading>

    <t-dialog
      v-model:visible="dialogVisible"
      header="新建知识库"
      :confirm-btn="{ content: '创建', loading: creating }"
      @confirm="submitCreate"
    >
      <div class="form">
        <t-input v-model="form.name" :maxlength="160" placeholder="知识库名称" />
        <t-textarea
          v-model="form.description"
          :maxlength="2000"
          placeholder="简要说明知识库内容（可选）"
        />
      </div>
    </t-dialog>
  </section>
</template>

<style scoped>
.page { max-width: 1280px; margin: 0 auto; }
.page-header { display: flex; align-items: start; justify-content: space-between; gap: 24px; margin-bottom: 24px; }
.eyebrow { color: #3159db; font-size: 11px; font-weight: 800; letter-spacing: .15em; }
.page-header h1 { margin: 8px 0; font-size: 30px; }
.page-header p { margin: 0; color: var(--td-text-color-secondary); }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(310px, 1fr)); gap: 18px; }
.knowledge-card { display: block; min-height: 218px; padding: 24px; color: inherit; text-decoration: none; background: #fff; border: 1px solid #e4eaf2; border-radius: 16px; transition: border-color .2s, transform .2s, box-shadow .2s; }
.knowledge-card:hover { border-color: #8da5ef; transform: translateY(-3px); box-shadow: 0 16px 32px #284c9c12; }
.card-icon { display: grid; place-items: center; width: 42px; height: 42px; margin-bottom: 18px; border-radius: 12px; background: #eaf0ff; color: #3159db; font-size: 20px; }
.knowledge-card h2 { display: flex; justify-content: space-between; margin: 0 0 9px; font-size: 18px; }.card-arrow { color: #a3b0c4; font-size: 19px; font-weight: 400; }
.knowledge-card p { min-height: 40px; margin: 0; color: var(--td-text-color-secondary); font-size: 13px; line-height: 1.6; }
.card-foot { display: flex; justify-content: space-between; margin-top: 20px; padding-top: 14px; border-top: 1px solid #edf0f5; color: #3159db; font-size: 12px; font-weight: 600; }
.form { display: grid; gap: 16px; }
@media (max-width: 600px) { .page-header { flex-direction: column; } }
</style>
