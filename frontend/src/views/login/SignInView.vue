<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { currentUser, login, register } from '@/api/auth'
import { errorMessage } from '@/api/http'

const route = useRoute()
const router = useRouter()
const registering = ref(false)
const busy = ref(false)
const attempted = ref(false)
const serverError = ref('')
const email = ref('')
const displayName = ref('')
const password = ref('')
const emailInput = ref<HTMLInputElement | null>(null)

const normalizedEmail = computed(() => email.value.trim())
const emailError = computed(() => {
  if (!normalizedEmail.value) return '请输入邮箱地址'
  if (normalizedEmail.value.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail.value)) return '请输入有效的邮箱地址'
  return ''
})
const nameError = computed(() => {
  if (!registering.value) return ''
  const value = displayName.value.trim()
  if (!value) return '请输入显示名称'
  if (value.length > 120) return '显示名称不能超过 120 个字符'
  return ''
})
const passwordError = computed(() => {
  if (!password.value) return '请输入密码'
  if (registering.value && (password.value.length < 6 || password.value.length > 128)) return '密码长度需为 6 到 128 个字符'
  return ''
})
const formValid = computed(() => !emailError.value && !nameError.value && !passwordError.value)

function destination() {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  return redirect.startsWith('/') && !redirect.startsWith('//') && !redirect.startsWith('/sign-in') ? redirect : '/login'
}

function switchMode() {
  registering.value = !registering.value
  attempted.value = false
  serverError.value = ''
  password.value = ''
  void nextTick(() => emailInput.value?.focus())
}

async function submit() {
  attempted.value = true
  serverError.value = ''
  if (!formValid.value || busy.value) return
  busy.value = true
  try {
    if (registering.value) await register(normalizedEmail.value, displayName.value.trim(), password.value)
    else await login(normalizedEmail.value, password.value)
    await router.replace(destination())
  } catch (error) {
    serverError.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}

watch([email, displayName, password], () => { serverError.value = '' })

onMounted(async () => {
  emailInput.value?.focus()
  try {
    await currentUser()
    await router.replace(destination())
  } catch {
    // 未登录是此页面的正常状态。
  }
})
</script>

<template>
  <main class="auth">
    <div class="ambient ambient-one"></div><div class="ambient ambient-two"></div>
    <div class="shell">
      <header class="topbar" aria-label="PB-RAG"><span class="mark" aria-hidden="true">P</span><div class="brand"><strong>PB-RAG</strong><span>KNOWLEDGE STUDIO</span></div><span class="topbar-note"><i></i>团队知识工作台</span></header>
      <div class="content">
        <section class="intro" aria-labelledby="auth-title">
          <div class="eyebrow">ENTERPRISE KNOWLEDGE AGENT</div>
          <h1 id="auth-title">让团队知识<br /><span>真正可检索、可追溯。</span></h1>
          <p>统一管理知识库与文档处理任务，在一个清晰的工作台中查看进度、来源和运行状态。</p>
          <ul class="features" aria-label="产品能力">
            <li><span>01</span><div><strong>知识归档</strong><small>集中管理团队资料与版本</small></div></li>
            <li><span>02</span><div><strong>过程可见</strong><small>跟踪文档处理阶段与失败原因</small></div></li>
            <li><span>03</span><div><strong>答案可溯</strong><small>回答与原始文档位置保持关联</small></div></li>
          </ul>
        </section>
        <section class="panel" aria-labelledby="form-title">
          <div class="panel-heading"><span class="mode-label">{{ registering ? 'CREATE ACCOUNT' : 'WELCOME BACK' }}</span><h2 id="form-title">{{ registering ? '创建账号' : '欢迎回来' }}</h2><p>{{ registering ? '创建账号后即可进入共享工作区' : '使用邮箱和密码继续访问工作台' }}</p></div>
          <div v-if="serverError" class="error-banner" role="alert"><span aria-hidden="true">!</span><div><strong>{{ registering ? '注册未完成' : '登录未成功' }}</strong><p>{{ serverError }}</p></div></div>
          <form novalidate @submit.prevent="submit">
            <div class="field"><label for="auth-email">邮箱</label><input id="auth-email" ref="emailInput" v-model="email" type="email" inputmode="email" autocomplete="email" placeholder="name@example.com" :aria-invalid="attempted && !!emailError" :aria-describedby="attempted && emailError ? 'email-error' : undefined" /><small v-if="attempted && emailError" id="email-error" class="field-error">{{ emailError }}</small></div>
            <div v-if="registering" class="field"><label for="display-name">显示名称 <span>{{ displayName.trim().length }}/120</span></label><input id="display-name" v-model="displayName" type="text" autocomplete="name" maxlength="120" placeholder="团队成员如何称呼你" :aria-invalid="attempted && !!nameError" :aria-describedby="attempted && nameError ? 'name-error' : undefined" /><small v-if="attempted && nameError" id="name-error" class="field-error">{{ nameError }}</small></div>
            <div class="field"><label for="auth-password">密码 <span v-if="registering">6–128 个字符</span></label><input id="auth-password" v-model="password" type="password" :autocomplete="registering ? 'new-password' : 'current-password'" :placeholder="registering ? '设置登录密码' : '输入你的密码'" :aria-invalid="attempted && !!passwordError" :aria-describedby="attempted && passwordError ? 'password-error' : undefined" /><small v-if="attempted && passwordError" id="password-error" class="field-error">{{ passwordError }}</small></div>
            <button class="submit" type="submit" :disabled="busy" :aria-busy="busy"><span v-if="busy" class="spinner" aria-hidden="true"></span>{{ busy ? (registering ? '正在创建账号…' : '正在验证…') : (registering ? '创建账号并进入' : '登录工作台') }}<span v-if="!busy" aria-hidden="true">→</span></button>
          </form>
          <div class="switch-row">{{ registering ? '已经有账号？' : '第一次使用 PB-RAG？' }}<button type="button" @click="switchMode">{{ registering ? '返回登录' : '创建账号' }}</button></div>
          <div class="scope-note"><span aria-hidden="true">i</span><p><strong>当前访问范围</strong>所有已登录用户暂时共享 Workspace 与资料，尚未启用成员权限隔离。</p></div>
        </section>
      </div>
    </div>
  </main>
</template>

<style scoped>
.auth{position:relative;min-height:100%;overflow:hidden;background:#f5f7fb;color:#172033}.ambient{position:absolute;border-radius:999px;pointer-events:none}.ambient-one{top:-260px;right:-180px;width:600px;height:600px;background:radial-gradient(circle,#dfe7ff 0,#edf1ff80 45%,transparent 70%)}.ambient-two{bottom:-300px;left:-240px;width:680px;height:680px;background:radial-gradient(circle,#e2ecff 0,transparent 68%)}
.shell{position:relative;z-index:1;max-width:1180px;min-height:100vh;margin:auto;padding:0 34px}.topbar{display:flex;align-items:center;height:82px;border-bottom:1px solid #dfe4ee}.mark{display:grid;place-items:center;width:36px;height:36px;margin-right:11px;border-radius:10px;background:linear-gradient(145deg,#4269e8,#2547ba);box-shadow:0 8px 20px #3159db35;color:#fff;font-size:18px;font-weight:750}.brand{display:grid;line-height:1}.brand strong{font-size:16px;letter-spacing:.02em}.brand span{margin-top:5px;color:#8a95a6;font-size:8px;letter-spacing:.16em}.topbar-note{display:flex;align-items:center;gap:8px;margin-left:auto;color:#69778c;font-size:12px}.topbar-note i{width:6px;height:6px;border-radius:50%;background:#35a66f;box-shadow:0 0 0 4px #35a66f18}
.content{display:grid;grid-template-columns:minmax(0,1fr) 420px;align-items:center;gap:92px;min-height:calc(100vh - 82px);padding:64px 0 90px}.intro{padding-bottom:12px}.eyebrow{margin-bottom:22px;color:#3159db;font-size:11px;font-weight:700;letter-spacing:.14em}.intro h1{margin:0;font-size:clamp(38px,4vw,56px);line-height:1.18;letter-spacing:-.045em}.intro h1 span{color:#3159db}.intro>p{max-width:540px;margin:24px 0 0;color:#65748a;font-size:15px;line-height:1.85}.features{display:grid;grid-template-columns:repeat(3,1fr);gap:24px;margin:42px 0 0;padding:0;list-style:none}.features li{display:flex;gap:10px}.features li>span{padding-top:2px;color:#9ba6b6;font-size:10px;font-weight:700}.features div{display:grid;gap:5px}.features strong{font-size:13px}.features small{color:#8490a1;font-size:11px;line-height:1.5}
.panel{padding:38px 38px 30px;border:1px solid #dfe4ec;border-radius:18px;background:#ffffffeb;box-shadow:0 24px 70px #26364d12,0 2px 8px #26364d08;backdrop-filter:blur(12px)}.mode-label{display:block;margin-bottom:8px;color:#3159db;font-size:9px;font-weight:750;letter-spacing:.16em}.panel h2{margin:0;font-size:26px;letter-spacing:-.02em}.panel-heading>p{margin:8px 0 28px;color:#7c899b;font-size:13px}form{display:grid;gap:18px}.field{display:grid;gap:8px}.field label{display:flex;justify-content:space-between;color:#344055;font-size:12px;font-weight:650}.field label span{color:#9aa4b2;font-size:10px;font-weight:400}.field input{width:100%;height:45px;padding:0 13px;border:1px solid #d9dfe8;border-radius:8px;outline:none;background:#fbfcfe;color:#172033;font:inherit;font-size:14px;transition:border-color .18s,box-shadow .18s,background .18s}.field input::placeholder{color:#a4adba}.field input:hover{border-color:#aab5c5}.field input:focus{border-color:#3159db;background:#fff;box-shadow:0 0 0 3px #3159db16}.field input[aria-invalid="true"]{border-color:#d94b4b;box-shadow:0 0 0 3px #d94b4b10}.field-error{margin-top:-2px;color:#c53f3f;font-size:11px}
.submit{display:flex;align-items:center;justify-content:center;gap:9px;height:47px;margin-top:6px;border:0;border-radius:8px;background:linear-gradient(135deg,#3c64e4,#284fc8);box-shadow:0 8px 18px #3159db30;color:#fff;cursor:pointer;font-size:13px;font-weight:650;transition:transform .15s,box-shadow .15s,opacity .15s}.submit:hover:not(:disabled){transform:translateY(-1px);box-shadow:0 11px 24px #3159db3d}.submit:active:not(:disabled){transform:translateY(0)}.submit:disabled{cursor:wait;opacity:.72}.submit:focus-visible,.switch-row button:focus-visible{outline:3px solid #3159db32;outline-offset:2px}.spinner{width:14px;height:14px;border:2px solid #ffffff60;border-top-color:#fff;border-radius:50%;animation:spin .7s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
.switch-row{margin-top:22px;text-align:center;color:#7c899b;font-size:12px}.switch-row button{margin-left:6px;padding:2px;border:0;background:none;color:#3159db;cursor:pointer;font:inherit;font-weight:650}.scope-note{display:flex;gap:10px;margin-top:24px;padding-top:20px;border-top:1px solid #edf0f4;color:#8792a2}.scope-note>span{display:grid;flex:0 0 17px;place-items:center;width:17px;height:17px;border:1px solid #bdc5d0;border-radius:50%;font-size:10px;font-weight:700}.scope-note p{margin:0;font-size:10px;line-height:1.65}.scope-note strong{display:block;color:#647187;font-size:10px}.error-banner{display:flex;gap:10px;margin:-8px 0 20px;padding:11px 12px;border:1px solid #f0cece;border-radius:8px;background:#fff7f7;color:#8c3030}.error-banner>span{display:grid;flex:0 0 19px;place-items:center;width:19px;height:19px;border-radius:50%;background:#d94b4b;color:#fff;font-size:11px;font-weight:700}.error-banner strong{display:block;font-size:11px}.error-banner p{margin:3px 0 0;font-size:11px;line-height:1.45}
@media(max-width:900px){.content{grid-template-columns:minmax(0,1fr) 390px;gap:48px}.features{grid-template-columns:1fr;gap:14px}.features small{display:none}}@media(max-width:720px){.shell{padding:0 22px}.content{grid-template-columns:1fr;gap:34px;min-height:auto;padding:48px 0 64px}.intro h1{font-size:36px}.intro>p{margin-top:16px}.features{display:none}.panel{width:100%;max-width:460px}.eyebrow{margin-bottom:14px}}@media(max-width:460px){.shell{padding:0 16px}.topbar{height:70px}.topbar-note{display:none}.content{padding-top:38px}.intro h1{font-size:31px}.intro>p{font-size:13px}.panel{padding:29px 22px 24px;border-radius:14px}}@media(prefers-reduced-motion:reduce){.submit,.field input{transition:none}.spinner{animation-duration:1.4s}}
</style>
