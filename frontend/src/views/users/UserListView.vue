<script setup lang="ts">
// 用户管理：查询、新增、编辑与删除 app_users 账号。
import { computed, onMounted, ref } from 'vue'
import { DialogPlugin, MessagePlugin } from 'tdesign-vue-next'
import { createUser, deleteUser, listUsers, updateUser, type ManagedUser } from '@/api/users'
import { errorMessage } from '@/api/http'

const users = ref<ManagedUser[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<string | null>(null)
const form = ref({ email: '', displayName: '', password: '' })

const dialogTitle = computed(() => (editingId.value ? '编辑用户' : '新增用户'))
const count = computed(() => users.value.length)

function initial(name: string) {
  return (name || 'U').trim().charAt(0).toUpperCase()
}

async function load() {
  loading.value = true
  try {
    users.value = await listUsers()
  } catch (error) {
    void MessagePlugin.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.value = { email: '', displayName: '', password: '' }
  dialogVisible.value = true
}

function openEdit(user: ManagedUser) {
  editingId.value = user.id
  form.value = { email: user.email, displayName: user.displayName, password: '' }
  dialogVisible.value = true
}

async function submit() {
  const email = form.value.email.trim()
  const displayName = form.value.displayName.trim()
  const password = form.value.password
  if (!email || !displayName) {
    void MessagePlugin.warning('请填写邮箱和名称')
    return
  }
  if (!editingId.value && password.length < 6) {
    void MessagePlugin.warning('新增用户密码至少 6 位')
    return
  }
  if (password && password.length < 6) {
    void MessagePlugin.warning('密码至少 6 位')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      const updated = await updateUser(editingId.value, {
        email,
        displayName,
        password: password || undefined,
      })
      const index = users.value.findIndex((item) => item.id === updated.id)
      if (index >= 0) users.value[index] = updated
      void MessagePlugin.success('用户已更新')
    } else {
      const created = await createUser({ email, displayName, password })
      users.value.push(created)
      void MessagePlugin.success('用户已创建')
    }
    dialogVisible.value = false
  } catch (error) {
    void MessagePlugin.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

function confirmDelete(user: ManagedUser) {
  const dialog = DialogPlugin.confirm({
    header: '删除用户',
    body: `确认删除「${user.displayName}（${user.email}）」？该用户的登录会话也会一并失效。`,
    theme: 'warning',
    confirmBtn: { content: '删除', theme: 'danger' },
    onConfirm: async () => {
      try {
        await deleteUser(user.id)
        users.value = users.value.filter((item) => item.id !== user.id)
        void MessagePlugin.success('用户已删除')
        dialog.destroy()
      } catch (error) {
        void MessagePlugin.error(errorMessage(error))
      }
    },
  })
}

onMounted(load)
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <span class="eyebrow">ACCOUNT DIRECTORY</span>
        <h1>用户管理</h1>
        <p>维护可登录平台的账号，共 {{ count }} 位用户。</p>
      </div>
      <t-button theme="primary" @click="openCreate">新增用户</t-button>
    </header>

    <t-loading :loading="loading">
      <div v-if="users.length" class="table-card">
        <table class="user-table">
          <thead>
            <tr>
              <th>用户</th>
              <th>邮箱</th>
              <th class="actions-col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in users" :key="item.id">
              <td>
                <div class="user-cell">
                  <span class="avatar">{{ initial(item.displayName) }}</span>
                  <span class="name">{{ item.displayName }}</span>
                </div>
              </td>
              <td class="email">{{ item.email }}</td>
              <td class="actions-col">
                <t-button variant="text" theme="primary" @click="openEdit(item)">编辑</t-button>
                <t-button variant="text" theme="danger" @click="confirmDelete(item)">删除</t-button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <t-empty v-else-if="!loading" description="还没有用户" />
    </t-loading>

    <t-dialog
      v-model:visible="dialogVisible"
      :header="dialogTitle"
      :confirm-btn="{ content: editingId ? '保存' : '创建', loading: saving }"
      @confirm="submit"
    >
      <div class="form">
        <label class="field">
          <span>邮箱</span>
          <t-input v-model="form.email" :maxlength="254" placeholder="name@example.com" />
        </label>
        <label class="field">
          <span>显示名称</span>
          <t-input v-model="form.displayName" :maxlength="120" placeholder="用户名称" />
        </label>
        <label class="field">
          <span>{{ editingId ? '重置密码（留空则不修改）' : '密码' }}</span>
          <t-input
            v-model="form.password"
            type="password"
            :maxlength="128"
            :placeholder="editingId ? '留空保持原密码' : '至少 6 位'"
          />
        </label>
      </div>
    </t-dialog>
  </section>
</template>

<style scoped>
.page { max-width: 1080px; margin: 0 auto; }
.page-header { display: flex; align-items: start; justify-content: space-between; gap: 24px; margin-bottom: 24px; }
.eyebrow { color: #3159db; font-size: 11px; font-weight: 800; letter-spacing: .15em; }
.page-header h1 { margin: 8px 0; font-size: 30px; }
.page-header p { margin: 0; color: var(--td-text-color-secondary); }
.table-card { overflow: hidden; background: #fff; border: 1px solid #e4eaf2; border-radius: 16px; }
.user-table { width: 100%; border-collapse: collapse; }
.user-table th { padding: 14px 24px; text-align: left; color: #8c9ab0; font-size: 12px; font-weight: 600; letter-spacing: .04em; background: #f7f9fd; border-bottom: 1px solid #edf0f5; }
.user-table td { padding: 14px 24px; border-bottom: 1px solid #f1f4f9; font-size: 14px; }
.user-table tbody tr:last-child td { border-bottom: 0; }
.user-table tbody tr:hover { background: #fafbff; }
.user-cell { display: flex; align-items: center; gap: 12px; }
.avatar { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 10px; background: #eaf0ff; color: #3159db; font-size: 13px; font-weight: 700; }
.name { font-weight: 600; }
.email { color: var(--td-text-color-secondary); }
.actions-col { width: 1%; white-space: nowrap; text-align: right; }
.form { display: grid; gap: 16px; }
.field { display: grid; gap: 8px; }
.field > span { color: var(--td-text-color-secondary); font-size: 13px; }
@media (max-width: 600px) { .page-header { flex-direction: column; } .user-table th, .user-table td { padding: 12px 14px; } }
</style>
