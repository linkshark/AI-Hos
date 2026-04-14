<template>
  <div class="auth-shell">
    <section class="auth-hero">
      <div class="hero-rings"></div>
      <div class="auth-brand">
        <img :src="shulanLogo" alt="杭州树兰医院" class="auth-logo" />
        <div>
          <p class="auth-eyebrow">Hangzhou Shulan Hospital</p>
          <h1>创建树兰账号</h1>
          <p class="auth-subtitle">注册后即可进入智能问答系统。普通用户可使用问答能力，知识管理仅对管理员开放。</p>
        </div>
      </div>

      <div class="story-panel">
        <div class="story-item">
          <strong>01</strong>
          <div>
            <span>发送邮箱验证码</span>
            <p>先确认邮箱可用，再完成注册。</p>
          </div>
        </div>
        <div class="story-item">
          <strong>02</strong>
          <div>
            <span>设置登录密码</span>
            <p>注册完成后将自动签发登录态。</p>
          </div>
        </div>
        <div class="story-item">
          <strong>03</strong>
          <div>
            <span>进入智能服务</span>
            <p>后续问答、资料上传和会话记录都走当前账号。</p>
          </div>
        </div>
      </div>
    </section>

    <section class="auth-form-panel">
      <div class="auth-card">
        <div class="card-topline"></div>
        <p class="form-kicker">Register</p>
        <h2>邮箱验证码注册</h2>
        <p class="form-intro">请使用真实可用的邮箱完成注册，注册成功后将自动登录当前账号。</p>

        <el-form :model="form" label-position="top" @submit.prevent="submit">
          <el-form-item label="昵称">
            <el-input v-model.trim="form.nickname" placeholder="可填写姓名或常用昵称" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model.trim="form.email" placeholder="请输入邮箱地址" />
          </el-form-item>
          <button
            type="button"
            class="admin-role-option"
            :class="{ 'is-active': form.adminRequested }"
            :aria-checked="form.adminRequested"
            role="checkbox"
            @click="toggleAdminRequested"
          >
            <span class="admin-role-check">{{ form.adminRequested ? '✓' : '' }}</span>
            <span class="admin-role-copy">
              <strong>管理员权限</strong>
              <small>需管理员邀请码，注册后权限为管理员</small>
            </span>
          </button>
          <el-form-item label="验证码">
            <div class="code-row">
              <el-input v-model.trim="form.code" placeholder="请输入邮箱验证码" />
              <el-button class="code-button" :disabled="sendingCode || countdown > 0" @click="sendCodeAction">
                {{ countdown > 0 ? `${countdown}s` : sendingCode ? '发送中' : '发送验证码' }}
              </el-button>
            </div>
          </el-form-item>
          <div class="password-grid">
            <el-form-item label="密码">
              <el-input v-model="form.password" type="password" show-password placeholder="请输入 8-64 位密码" />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请再次输入密码" />
            </el-form-item>
          </div>
          <el-button class="auth-submit" type="primary" :loading="submitting" @click="submit">
            注册并进入系统
          </el-button>
        </el-form>

        <div class="auth-footer">
          <span>已有账号？</span>
          <router-link to="/login">返回登录</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import shulanLogo from '@/assets/shulan-logo.png'
import { register, sendRegisterCode } from '@/lib/auth'

const CHAT_MEMORY_STORAGE_KEY = 'chat_current_memory_id'
const router = useRouter()
const route = useRoute()
const submitting = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
let timer = null

const form = reactive({
  nickname: '',
  email: '',
  code: '',
  password: '',
  confirmPassword: '',
  adminRequested: false,
  adminInviteToken: '',
})

onBeforeUnmount(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})

const sendCodeAction = async () => {
  if (!form.email) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  sendingCode.value = true
  try {
    await sendRegisterCode({ email: form.email })
    ElMessage.success('验证码已发送，请查收邮箱')
    startCountdown()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '验证码发送失败')
  } finally {
    sendingCode.value = false
  }
}

const submit = async () => {
  if (!form.email || !form.code || !form.password || !form.confirmPassword) {
    ElMessage.warning('请完整填写注册信息')
    return
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  submitting.value = true
  try {
    await register(form)
    sessionStorage.removeItem(CHAT_MEMORY_STORAGE_KEY)
    ElMessage.success('注册成功')
    await router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '注册失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

const toggleAdminRequested = async () => {
  if (form.adminRequested) {
    form.adminRequested = false
    form.adminInviteToken = ''
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('token不易，请勿滥用🐶', '确认申请管理员权限', {
      inputPlaceholder: '请输入管理员邀请码(测试期间,随便输入即可)',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
      customClass: 'admin-role-confirm',
      distinguishCancelAndClose: true,
      inputValidator: (value) => Boolean(value && value.trim()),
      inputErrorMessage: '请输入管理员邀请码',
    })
    form.adminInviteToken = value.trim()
    form.adminRequested = true
  } catch {
    form.adminRequested = false
    form.adminInviteToken = ''
  }
}

const startCountdown = () => {
  countdown.value = 60
  if (timer) {
    window.clearInterval(timer)
  }
  timer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      window.clearInterval(timer)
      timer = null
      countdown.value = 0
    }
  }, 1000)
}
</script>

<style scoped>
.auth-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(360px, 500px);
  min-height: 100dvh;
  background:
    radial-gradient(circle at right top, rgba(15, 118, 110, 0.2), transparent 30%),
    radial-gradient(circle at left bottom, rgba(52, 211, 153, 0.14), transparent 32%),
    linear-gradient(145deg, #f7fbfa 0%, #edf7f3 54%, #f8faf9 100%);
}

.auth-hero {
  position: relative;
  overflow: hidden;
  padding: 54px 46px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 24px;
  color: #103f39;
}

.hero-rings {
  position: absolute;
  inset: auto -120px -120px auto;
  width: 420px;
  height: 420px;
  border-radius: 50%;
  background:
    radial-gradient(circle, rgba(15, 118, 110, 0.08) 0 24%, transparent 24% 34%, rgba(15, 118, 110, 0.06) 34% 44%, transparent 44% 54%, rgba(15, 118, 110, 0.04) 54% 64%, transparent 64%);
  pointer-events: none;
}

.auth-brand,
.story-panel {
  position: relative;
  z-index: 1;
}

.auth-brand {
  display: flex;
  gap: 18px;
  align-items: center;
}

.auth-logo {
  width: 88px;
  max-width: 24vw;
}

.auth-eyebrow {
  margin: 0 0 10px;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  font-size: 12px;
  color: #2a8179;
}

.auth-brand h1 {
  margin: 0;
  font-size: 38px;
  line-height: 1.12;
}

.auth-subtitle {
  margin: 14px 0 0;
  max-width: 580px;
  font-size: 15px;
  line-height: 1.8;
  color: #52736d;
}

.story-panel {
  display: grid;
  gap: 14px;
  max-width: 560px;
}

.story-item {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  gap: 14px;
  padding: 16px 18px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 22px 40px rgba(13, 52, 48, 0.08);
}

.story-item strong {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 18px;
  color: #ffffff;
  background: linear-gradient(135deg, #0f766e, #26a69a);
}

.story-item span {
  display: block;
  margin-bottom: 6px;
  font-weight: 700;
  color: #173f3a;
}

.story-item p {
  margin: 0;
  color: #5c7773;
  line-height: 1.7;
}

.auth-form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px 32px;
}

.auth-card {
  position: relative;
  width: min(100%, 450px);
  padding: 36px 32px 30px;
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 34px 72px rgba(13, 52, 48, 0.14);
}

.card-topline {
  position: absolute;
  top: 0;
  left: 24px;
  right: 24px;
  height: 5px;
  border-radius: 0 0 999px 999px;
  background: linear-gradient(90deg, #0f766e, #34d399);
}

.form-kicker {
  margin: 0 0 10px;
  color: #2a8179;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  font-size: 12px;
}

.auth-card h2 {
  margin: 0;
  font-size: 30px;
  color: #123b36;
}

.form-intro {
  margin: 12px 0 24px;
  color: #627b77;
  line-height: 1.75;
}

.code-row,
.password-grid {
  display: grid;
  gap: 12px;
}

.admin-role-option {
  width: 100%;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  margin: -6px 0 18px;
  padding: 13px 14px;
  border: 1px solid rgba(15, 118, 110, 0.16);
  border-radius: 16px;
  color: #16443e;
  text-align: left;
  background: linear-gradient(135deg, rgba(240, 253, 250, 0.92), rgba(255, 255, 255, 0.94));
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.admin-role-option:hover {
  border-color: rgba(15, 118, 110, 0.38);
  box-shadow: 0 14px 28px rgba(13, 52, 48, 0.08);
  transform: translateY(-1px);
}

.admin-role-option.is-active {
  border-color: rgba(15, 118, 110, 0.62);
  background: linear-gradient(135deg, rgba(15, 118, 110, 0.1), rgba(236, 253, 245, 0.96));
  box-shadow: inset 0 0 0 1px rgba(15, 118, 110, 0.12);
}

.admin-role-check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 13px;
  border: 1px solid rgba(15, 118, 110, 0.28);
  color: #ffffff;
  font-size: 20px;
  font-weight: 800;
  background: rgba(255, 255, 255, 0.88);
}

.admin-role-option.is-active .admin-role-check {
  border-color: transparent;
  background: linear-gradient(135deg, #0f766e, #34d399);
}

.admin-role-copy {
  display: grid;
  gap: 4px;
}

.admin-role-copy strong {
  font-size: 14px;
  color: #123b36;
}

.admin-role-copy small {
  color: #6a827e;
  font-size: 12px;
  line-height: 1.45;
}

.code-row {
  grid-template-columns: minmax(0, 1fr) 124px;
}

.password-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.code-button {
  height: 40px;
}

.auth-submit {
  width: 100%;
  height: 48px;
  margin-top: 12px;
  border-radius: 15px;
}

.auth-footer {
  margin-top: 22px;
  color: #5d7773;
}

.auth-footer a {
  margin-left: 8px;
  color: #0f766e;
  text-decoration: none;
  font-weight: 600;
}

:global(.admin-role-confirm) {
  border-radius: 20px;
  padding: 20px 20px 18px;
}

:global(.admin-role-confirm .el-message-box__title) {
  color: #123b36;
  font-weight: 800;
}

:global(.admin-role-confirm .el-message-box__content) {
  color: #516b66;
}

@media (max-width: 1024px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .auth-hero {
    padding: 32px 24px 10px;
  }

  .auth-form-panel {
    padding: 18px 18px 28px;
  }
}

@media (max-width: 640px) {
  .auth-brand {
    align-items: flex-start;
  }

  .auth-logo {
    width: 66px;
  }

  .auth-brand h1 {
    font-size: 28px;
  }

  .auth-card {
    padding: 28px 22px 24px;
    border-radius: 24px;
  }

  .code-row,
  .password-grid {
    grid-template-columns: 1fr;
  }
}
</style>
