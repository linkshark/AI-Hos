<template>
  <div class="auth-shell">
    <section class="auth-hero">
      <div class="hero-rings"></div>
      <div class="auth-brand">
        <img :src="shulanLogo" alt="杭州树兰医院" class="auth-logo" />
        <div>
          <p class="auth-eyebrow">Hangzhou Shulan Hospital</p>
          <h1>重置登录密码</h1>
          <p class="auth-subtitle">通过邮箱验证码重置密码，重置完成后使用新密码重新登录。</p>
        </div>
      </div>

      <div class="story-panel">
        <div class="story-item">
          <strong>01</strong>
          <div>
            <span>输入注册邮箱</span>
            <p>系统将向已注册邮箱发送密码重置验证码。</p>
          </div>
        </div>
        <div class="story-item">
          <strong>02</strong>
          <div>
            <span>校验验证码</span>
            <p>验证码 10 分钟内有效，仅可用于本次密码重置。</p>
          </div>
        </div>
        <div class="story-item">
          <strong>03</strong>
          <div>
            <span>设置新密码</span>
            <p>重置成功后，请返回登录页使用新密码继续访问系统。</p>
          </div>
        </div>
      </div>
    </section>

    <section class="auth-form-panel">
      <div class="auth-card">
        <div class="card-topline"></div>
        <p class="form-kicker">Reset Password</p>
        <h2>邮箱验证码重置</h2>
        <p class="form-intro">请输入已注册邮箱、验证码和新密码。密码重置后，旧密码将立即失效。</p>

        <el-form :model="form" label-position="top" @submit.prevent="submit">
          <el-form-item label="邮箱">
            <el-input v-model.trim="form.email" placeholder="请输入注册邮箱" />
          </el-form-item>
          <el-form-item label="验证码">
            <div class="code-row">
              <el-input v-model.trim="form.code" placeholder="请输入邮箱验证码" />
              <el-button class="code-button" :disabled="sendingCode || countdown > 0" @click="sendCodeAction">
                {{ countdown > 0 ? `${countdown}s` : sendingCode ? '发送中' : '发送验证码' }}
              </el-button>
            </div>
          </el-form-item>
          <div class="password-grid">
            <el-form-item label="新密码">
              <el-input v-model="form.password" type="password" show-password placeholder="请输入 8-64 位新密码" />
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
            </el-form-item>
          </div>
          <el-button class="auth-submit" type="primary" :loading="submitting" @click="submit">
            重置密码
          </el-button>
        </el-form>

        <div class="auth-footer">
          <span>想起密码了？</span>
          <router-link to="/login">返回登录</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import shulanLogo from '@/assets/shulan-logo.png'
import { resetPassword, sendPasswordResetCode } from '@/lib/auth'

const router = useRouter()
const submitting = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
let timer = null

const form = reactive({
  email: '',
  code: '',
  password: '',
  confirmPassword: '',
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
    await sendPasswordResetCode({ email: form.email })
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
    ElMessage.warning('请完整填写重置信息')
    return
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  submitting.value = true
  try {
    const response = await resetPassword(form)
    ElMessage.success(response?.message || '密码已重置')
    await router.replace('/login')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '密码重置失败，请稍后重试')
  } finally {
    submitting.value = false
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
  background: linear-gradient(160deg, #1d9c8a, #13665c);
  font-size: 18px;
}

.story-item span {
  display: block;
  margin-top: 4px;
  margin-bottom: 8px;
  font-weight: 700;
  color: #173f3a;
}

.story-item p {
  margin: 0;
  color: #597973;
  line-height: 1.7;
}

.auth-form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(249, 252, 251, 0.98));
}

.auth-card {
  width: min(100%, 430px);
  padding: 32px 30px;
  border-radius: 32px;
  background: #ffffff;
  box-shadow: 0 28px 48px rgba(15, 43, 39, 0.12);
}

.card-topline {
  width: 76px;
  height: 5px;
  border-radius: 999px;
  background: linear-gradient(90deg, #1d9c8a, #3ec8b2);
  margin-bottom: 18px;
}

.form-kicker {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #2b867c;
}

.auth-card h2 {
  margin: 0;
  font-size: 30px;
  color: #103f39;
}

.form-intro {
  margin: 12px 0 24px;
  line-height: 1.7;
  color: #5f7e79;
}

.code-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 132px;
  gap: 12px;
}

.password-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.auth-submit {
  width: 100%;
  height: 46px;
  margin-top: 8px;
  border: none;
  border-radius: 16px;
  background: linear-gradient(135deg, #177d71, #0e5d54);
  box-shadow: 0 18px 28px rgba(15, 91, 82, 0.2);
}

.code-button {
  border-radius: 14px;
}

.auth-footer {
  margin-top: 22px;
  display: flex;
  justify-content: center;
  gap: 8px;
  color: #5b7873;
}

.auth-footer a {
  color: #147a6d;
  font-weight: 700;
  text-decoration: none;
}

@media (max-width: 1100px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .auth-hero {
    padding: 40px 24px 12px;
  }

  .auth-form-panel {
    padding: 12px 20px 28px;
  }
}

@media (max-width: 640px) {
  .auth-brand {
    flex-direction: column;
    align-items: flex-start;
  }

  .auth-brand h1 {
    font-size: 32px;
  }

  .story-item {
    grid-template-columns: 46px minmax(0, 1fr);
    padding: 14px 16px;
  }

  .story-item strong {
    width: 46px;
    height: 46px;
    border-radius: 14px;
    font-size: 16px;
  }

  .auth-card {
    padding: 26px 20px;
    border-radius: 24px;
  }

  .password-grid,
  .code-row {
    grid-template-columns: 1fr;
  }
}
</style>
