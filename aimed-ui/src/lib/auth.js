import { computed, reactive, watch } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const ACCESS_TOKEN_KEY = 'aimed_access_token'
const REFRESH_TOKEN_KEY = 'aimed_refresh_token'
const USER_KEY = 'aimed_auth_user'
const IDLE_TIMEOUT_MS = 5 * 60 * 1000
const IDLE_ACTIVITY_EVENTS = ['pointerdown', 'wheel', 'touchstart', 'scroll', 'keydown', 'focus']

let runtimeRouter = null
let idleTimer = null
let idleListenersAttached = false
let authExpiryInProgress = false
let lastErrorToastAt = 0

export const authState = reactive({
  accessToken: localStorage.getItem(ACCESS_TOKEN_KEY) || '',
  refreshToken: localStorage.getItem(REFRESH_TOKEN_KEY) || '',
  user: parseStoredUser(),
  refreshPromise: null,
  initialized: false,
})

export const apiClient = axios.create()

apiClient.interceptors.request.use((config) => {
  if (authState.accessToken) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${authState.accessToken}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error?.config || {}
    if (
      error?.response?.status === 401 &&
      authState.refreshToken &&
      !originalRequest._retry &&
      !isExcludedRefreshPath(originalRequest.url)
    ) {
      originalRequest._retry = true
      try {
        await refreshAccessToken()
        originalRequest.headers = originalRequest.headers || {}
        originalRequest.headers.Authorization = `Bearer ${authState.accessToken}`
        return apiClient(originalRequest)
      } catch (refreshError) {
        clearAuthState()
        await expireAuthSession('登录状态已失效，请重新登录。')
        throw refreshError
      }
    }

    if (
      error?.response?.status === 401 &&
      !originalRequest._skipUnauthorizedHandling &&
      !isExcludedRefreshPath(originalRequest.url)
    ) {
      await expireAuthSession('登录状态已失效，请重新登录。')
    } else if (error?.response?.status === 403 && !originalRequest._skipDefaultErrorToast) {
      showAuthToast(resolveErrorMessage(error, '当前操作无权限，请联系管理员。'), 'error')
      error._uiToastShown = true
    }

    throw error
  }
)

export const isAuthenticated = computed(() => Boolean(authState.accessToken && authState.user))
export const isAdmin = computed(() => authState.user?.role === 'ADMIN')

watch(
  () => [authState.accessToken, authState.user?.role],
  () => {
    syncAuthIdleTimeoutPolicy()
  },
  { immediate: true }
)

export async function initializeAuth() {
  if (authState.initialized) {
    return
  }
  if (!authState.refreshToken && !authState.accessToken) {
    authState.initialized = true
    return
  }
  try {
    if (authState.accessToken) {
      try {
        await fetchCurrentUser()
      } catch (error) {
        if (authState.refreshToken) {
          await refreshAccessToken()
        } else {
          throw error
        }
      }
    } else if (authState.refreshToken) {
      await refreshAccessToken()
    }
  } catch {
    clearAuthState()
  } finally {
    authState.initialized = true
  }
}

export async function login(payload) {
  const { data } = await apiClient.post('/api/aimed/auth/login', payload)
  applyAuthPayload(data)
  return data
}

export async function register(payload) {
  const { data } = await apiClient.post('/api/aimed/auth/register', payload)
  applyAuthPayload(data)
  return data
}

export async function sendRegisterCode(payload) {
  const { data } = await apiClient.post('/api/aimed/auth/register/send-code', payload)
  return data
}

export async function sendPasswordResetCode(payload) {
  const { data } = await apiClient.post('/api/aimed/auth/password/send-code', payload)
  return data
}

export async function resetPassword(payload) {
  const { data } = await apiClient.post('/api/aimed/auth/password/reset', payload)
  return data
}

export async function fetchCurrentUser() {
  const { data } = await apiClient.get('/api/aimed/auth/me')
  authState.user = data
  persistState()
  return data
}

export async function refreshAccessToken() {
  if (!authState.refreshToken) {
    throw new Error('当前没有可用的登录状态')
  }
  if (!authState.refreshPromise) {
    authState.refreshPromise = apiClient
      .post('/api/aimed/auth/refresh', { refreshToken: authState.refreshToken })
      .then(({ data }) => {
        applyAuthPayload(data)
        return data
      })
      .finally(() => {
        authState.refreshPromise = null
      })
  }
  return authState.refreshPromise
}

export async function logout() {
  try {
    if (authState.accessToken || authState.refreshToken) {
      await apiClient.post(
        '/api/aimed/auth/logout',
        { refreshToken: authState.refreshToken || undefined },
        {
          _skipUnauthorizedHandling: true,
          _skipDefaultErrorToast: true,
        }
      )
    }
  } finally {
    clearAuthState()
  }
}

export function setupAuthRuntime(router) {
  runtimeRouter = router
  syncAuthIdleTimeoutPolicy()
}

export function markAuthActivity() {
  restartIdleTimer()
}

export function syncAuthIdleTimeoutPolicy() {
  if (!authState.accessToken || !authState.user || isAdmin.value) {
    detachIdleActivityListeners()
    clearIdleTimer()
    return
  }
  attachIdleActivityListeners()
  restartIdleTimer()
}

export async function authorizedFetch(input, init = {}) {
  const perform = async () => {
    const headers = new Headers(init.headers || {})
    if (authState.accessToken) {
      headers.set('Authorization', `Bearer ${authState.accessToken}`)
    }
    return fetch(input, { ...init, headers })
  }

  let response = await perform()
  if (response.status === 401 && authState.refreshToken) {
    try {
      await refreshAccessToken()
    } catch (error) {
      await expireAuthSession('登录状态已失效，请重新登录。')
      throw error
    }
    response = await perform()
  }
  if (response.status === 401) {
    await expireAuthSession('登录状态已失效，请重新登录。')
  } else if (response.status === 403 && !init._skipDefaultErrorToast) {
    showAuthToast('当前操作无权限，请联系管理员。', 'error')
  }
  return response
}

function applyAuthPayload(data) {
  authState.accessToken = data?.accessToken || ''
  authState.refreshToken = data?.refreshToken || ''
  authState.user = data?.user || null
  persistState()
}

function clearAuthState() {
  authState.accessToken = ''
  authState.refreshToken = ''
  authState.user = null
  detachIdleActivityListeners()
  clearIdleTimer()
  persistState()
}

function persistState() {
  if (authState.accessToken) {
    localStorage.setItem(ACCESS_TOKEN_KEY, authState.accessToken)
  } else {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
  }
  if (authState.refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, authState.refreshToken)
  } else {
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  }
  if (authState.user) {
    localStorage.setItem(USER_KEY, JSON.stringify(authState.user))
  } else {
    localStorage.removeItem(USER_KEY)
  }
}

function parseStoredUser() {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw)
  } catch {
    localStorage.removeItem(USER_KEY)
    return null
  }
}

function isExcludedRefreshPath(url) {
  const value = String(url || '')
  return [
    '/aimed/auth/login',
    '/aimed/auth/register',
    '/aimed/auth/register/send-code',
    '/aimed/auth/password/send-code',
    '/aimed/auth/password/reset',
    '/aimed/auth/refresh',
    '/aimed/auth/logout',
  ].some((path) => value.includes(path))
}

async function expireAuthSession(message) {
  if (authExpiryInProgress) {
    return
  }
  authExpiryInProgress = true
  try {
    clearAuthState()
    showAuthToast(message, 'warning')
    if (runtimeRouter && runtimeRouter.currentRoute.value.path !== '/login') {
      await runtimeRouter.replace('/login')
    }
  } finally {
    authExpiryInProgress = false
  }
}

function attachIdleActivityListeners() {
  if (idleListenersAttached || typeof window === 'undefined') {
    return
  }
  IDLE_ACTIVITY_EVENTS.forEach((eventName) => {
    if (eventName === 'keydown' || eventName === 'focus') {
      window.addEventListener(eventName, handleIdleActivity)
    } else {
      window.addEventListener(eventName, handleIdleActivity, { passive: true })
    }
  })
  document.addEventListener('visibilitychange', handleIdleActivity)
  idleListenersAttached = true
}

function detachIdleActivityListeners() {
  if (!idleListenersAttached || typeof window === 'undefined') {
    return
  }
  IDLE_ACTIVITY_EVENTS.forEach((eventName) => {
    window.removeEventListener(eventName, handleIdleActivity)
  })
  document.removeEventListener('visibilitychange', handleIdleActivity)
  idleListenersAttached = false
}

function handleIdleActivity() {
  if (typeof document !== 'undefined' && document.hidden) {
    return
  }
  restartIdleTimer()
}

function restartIdleTimer() {
  if (!authState.accessToken || !authState.user || isAdmin.value) {
    clearIdleTimer()
    return
  }
  clearIdleTimer()
  idleTimer = window.setTimeout(() => {
    void expireIdleSession()
  }, IDLE_TIMEOUT_MS)
}

function clearIdleTimer() {
  if (!idleTimer) {
    return
  }
  window.clearTimeout(idleTimer)
  idleTimer = null
}

async function expireIdleSession() {
  if (!authState.accessToken || !authState.user || isAdmin.value) {
    clearIdleTimer()
    return
  }
  try {
    if (authState.accessToken || authState.refreshToken) {
      await apiClient.post(
        '/api/aimed/auth/logout',
        { refreshToken: authState.refreshToken || undefined },
        {
          _skipUnauthorizedHandling: true,
          _skipDefaultErrorToast: true,
        }
      )
    }
  } catch {
    // 空闲超时以本地登出为准，不阻塞前端跳回登录页。
  } finally {
    await expireAuthSession('登录已超时，请重新登录。')
  }
}

function resolveErrorMessage(error, fallback) {
  const traceId = error?.response?.headers?.['x-trace-id'] || error?.response?.data?.traceId
  const responseMessage =
    error?.response?.data?.message || error?.response?.data?.error || error?.message
  if (responseMessage && traceId) {
    return `${responseMessage}（traceId: ${traceId}）`
  }
  return responseMessage || fallback
}

function showAuthToast(message, type = 'warning') {
  const now = Date.now()
  if (!message || now - lastErrorToastAt < 800) {
    return
  }
  lastErrorToastAt = now
  ElMessage({
    type,
    message,
    grouping: true,
  })
}
