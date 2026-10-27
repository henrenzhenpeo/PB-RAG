import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/login' },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/login/LoginView.vue'),
    },
    {
      path: '/workspaces/:workspaceId',
      component: () => import('@/layouts/PlatformLayout.vue'),
      children: [
        {
          path: '',
          redirect: (to) => `/workspaces/${String(to.params.workspaceId)}/knowledge-bases`,
        },
        {
          path: 'knowledge-bases',
          name: 'knowledge-bases',
          component: () => import('@/views/knowledge/KnowledgeBaseListView.vue'),
        },
        {
          path: 'knowledge-bases/:kbId',
          name: 'knowledge-base',
          component: () => import('@/views/knowledge/KnowledgeBaseDetailView.vue'),
        },
        {
          path: 'chats/:chatId',
          name: 'chat',
          component: () => import('@/views/chat/ChatView.vue'),
        },
        {
          path: 'runs/:runId',
          name: 'run',
          component: () => import('@/views/run/RunView.vue'),
        },
        {
          path: 'evaluations',
          name: 'evaluations',
          component: () => import('@/views/evaluation/EvaluationView.vue'),
        },
      ],
    },
  ],
})

export default router
