import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authState, initializeAuth } from '@/lib/auth'

const routes = [
  {
    path: '/',
    component: () => import('@/components/ChatWindow.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/knowledge',
    component: () => import('@/components/KnowledgeCenterPage.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/admin',
    component: () => import('@/components/AdminConsolePage.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/login',
    component: () => import('@/components/LoginPage.vue'),
    meta: { guestOnly: true },
  },
  {
    path: '/register',
    component: () => import('@/components/RegisterPage.vue'),
    meta: { guestOnly: true },
  },
  {
    path: '/forgot-password',
    component: () => import('@/components/ForgotPasswordPage.vue'),
    meta: { guestOnly: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  const hasStoredToken = Boolean(authState.accessToken || authState.refreshToken)

  if (!hasStoredToken && to.meta.guestOnly) {
    return true
  }

  if (!hasStoredToken && to.meta.requiresAuth) {
    return '/login'
  }

  await initializeAuth()

  if (to.meta.requiresAuth && !authState.user) {
    return '/login'
  }

  if (to.meta.requiresAdmin && authState.user?.role !== 'ADMIN') {
    ElMessage.warning('普通用户无此权限!')
    return '/'
  }

  if (to.meta.guestOnly && authState.user) {
    return '/'
  }

  return true
})

export default router
