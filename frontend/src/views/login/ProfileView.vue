<script setup lang="ts">
// 当前账号资料页：加载本人信息并只允许修改显示名称。
import { onMounted, ref } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import { currentUser, updateProfile, type User } from '@/api/auth'
import { errorMessage } from '@/api/http'

const user = ref<User | null>(null)
const name = ref('')
const saving = ref(false)
onMounted(async () => { try { user.value = await currentUser(); name.value = user.value.displayName } catch (error) { void MessagePlugin.error(errorMessage(error)) } })
async function save() {
  saving.value = true
  try { user.value = await updateProfile(name.value); void MessagePlugin.success('已保存') }
  catch (error) { void MessagePlugin.error(errorMessage(error)) }
  finally { saving.value = false }
}
</script>
<template><main class="profile"><router-link to="/login">← 返回工作区</router-link><h1>个人资料</h1><div v-if="user" class="card"><label>邮箱<t-input :value="user.email" disabled /></label><label>显示名称<t-input v-model="name" :maxlength="120" /></label><t-button theme="primary" :loading="saving" @click="save">保存</t-button></div></main></template>
<style scoped>.profile { max-width: 720px; margin: 60px auto; padding: 0 24px; }.profile > a { color: #3159db; text-decoration: none; }.profile h1 { margin: 28px 0; }.card { display: grid; gap: 20px; padding: 28px; border: 1px solid #e1e5eb; border-radius: 10px; background: #fff; }label { display: grid; gap: 8px; }</style>
