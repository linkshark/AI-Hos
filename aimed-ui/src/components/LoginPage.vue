<template>
  <div class="auth-shell">
    <section class="auth-hero">
      <div class="hero-grid"></div>
      <div class="auth-brand">
        <img :src="shulanLogo" alt="杭州树兰医院" class="auth-logo" />
        <div>
          <p class="auth-eyebrow">Hangzhou Shulan Hospital</p>
          <h1>树兰智能服务平台</h1>
          <p class="auth-subtitle">面向院内问答、资料解读与知识管理的统一入口。</p>
        </div>
      </div>

      <div class="hero-panel">
        <div class="hero-panel-head">
          <strong>平台能力</strong>
          <span>Shulan AI Service</span>
        </div>
        <div class="hero-panel-grid">
          <div class="hero-metric">
            <span>智能问答</span>
            <strong>导诊 / 病历 / 图片</strong>
          </div>
          <div class="hero-metric">
            <span>知识引擎</span>
            <strong>RAG 检索增强</strong>
          </div>
          <div class="hero-metric">
            <span>管理权限</span>
            <strong>管理员可维护知识库</strong>
          </div>
          <div class="hero-metric">
            <span>登录方式</span>
            <strong>邮箱或用户名登录</strong>
          </div>
        </div>
      </div>

      <div class="hero-note">
        <span class="hero-note-tag">访问说明</span>
        <p>请使用已开通的账号登录。知识库维护权限仅向管理员开放。</p>
      </div>
    </section>

    <section class="auth-form-panel">
      <div class="auth-card">
        <div class="card-topline"></div>
        <p class="form-kicker">Welcome Back</p>
        <h2>登录树兰账号</h2>
        <p class="form-intro">支持邮箱或管理员用户名登录。登录后进入智能问答页。</p>

        <el-form :model="form" label-position="top" @submit.prevent="submit">
          <el-form-item label="账号">
            <el-input v-model.trim="form.account" placeholder="请输入邮箱或用户名" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="请输入登录密码"
              @keyup.enter="submit"
            />
          </el-form-item>
          <div class="forgot-row">
            <router-link to="/forgot-password">忘记密码？</router-link>
          </div>
          <el-button class="auth-submit" type="primary" :loading="submitting" @click="submit">
            登录进入系统
          </el-button>
        </el-form>

        <div class="form-hints">
          <div class="hint-chip">普通用户开放问答服务</div>
          <div class="hint-chip">知识库管理仅管理员可见</div>
        </div>

        <div class="auth-footer">
          <span>还没有账号？</span>
          <router-link to="/register">立即注册</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import shulanLogo from '@/assets/shulan-logo.png'
import { login } from '@/lib/auth'

const CHAT_MEMORY_STORAGE_KEY = 'chat_current_memory_id'
const router = useRouter()
const submitting = ref(false)
const form = reactive({
  account: '',
  password: '',
})

const submit = async () => {
  if (!form.account || !form.password) {
    ElMessage.warning('请完整填写账号和密码')
    return
  }
  submitting.value = true
  try {
    await login(form)
    sessionStorage.removeItem(CHAT_MEMORY_STORAGE_KEY)
    ElMessage.success('登录成功')
    await router.replace('/')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '登录失败，请检查账号和密码')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.auth-shell {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) minmax(360px, 468px);
  min-height: 100dvh;
  background:
    radial-gradient(circle at left top, rgba(15, 118, 110, 0.22), transparent 28%),
    radial-gradient(circle at right bottom, rgba(34, 197, 162, 0.18), transparent 32%),
    linear-gradient(145deg, #f3fbf8 0%, #eef7f4 46%, #f8fbfa 100%);
}

.auth-hero {
  position: relative;
  overflow: hidden;
  padding: 54px 48px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 24px;
  color: #103f39;
}

.hero-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(16, 63, 57, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(16, 63, 57, 0.05) 1px, transparent 1px);
  background-size: 32px 32px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.9), transparent);
  pointer-events: none;
}

.auth-brand,
.hero-panel,
.hero-note {
  position: relative;
  z-index: 1;
}

.auth-brand {
  display: flex;
  gap: 18px;
  align-items: center;
}

.auth-logo {
  width: 92px;
  max-width: 25vw;
}

.auth-eyebrow {
  margin: 0 0 10px;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  font-size: 12px;
  color: #238379;
}

.auth-brand h1 {
  margin: 0;
  font-size: 38px;
  line-height: 1.1;
}

.auth-subtitle {
  margin: 14px 0 0;
  max-width: 580px;
  font-size: 15px;
  line-height: 1.8;
  color: #50706b;
}

.hero-panel {
  max-width: 620px;
  padding: 24px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.74);
  backdrop-filter: blur(10px);
  box-shadow: 0 26px 48px rgba(16, 63, 57, 0.08);
}

.hero-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.hero-panel-head strong {
  font-size: 20px;
}

.hero-panel-head span {
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #5d807a;
}

.hero-panel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.hero-metric {
  padding: 16px 18px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(15, 118, 110, 0.08), rgba(34, 197, 162, 0.03));
  box-shadow: inset 0 0 0 1px rgba(44, 120, 111, 0.08);
}

.hero-metric span {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  color: #61837d;
}

.hero-metric strong {
  font-size: 16px;
  color: #173f3a;
}

.hero-note {
  max-width: 520px;
  padding: 18px 20px;
  border-left: 4px solid #16907f;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.56);
}

.hero-note-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: #12695f;
  background: rgba(18, 105, 95, 0.12);
}

.hero-note p {
  margin: 0;
  color: #4f706b;
  line-height: 1.7;
}

.auth-form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.auth-card {
  position: relative;
  width: min(100%, 432px);
  padding: 36px 32px 30px;
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 34px 70px rgba(13, 52, 48, 0.14);
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
  font-size: 31px;
  color: #123b36;
}

.form-intro {
  margin: 12px 0 24px;
  color: #5f7774;
  line-height: 1.7;
}

.auth-submit {
  width: 100%;
  height: 48px;
  margin-top: 12px;
  border-radius: 15px;
}

.forgot-row {
  margin: -6px 0 2px;
  display: flex;
  justify-content: flex-end;
}

.forgot-row a {
  color: #0f766e;
  text-decoration: none;
  font-weight: 600;
  font-size: 13px;
}

.form-hints {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.hint-chip {
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 12px;
  color: #2f615c;
  background: #eef7f4;
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

@media (max-width: 1024px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .auth-hero {
    padding: 32px 24px 8px;
  }

  .hero-panel-grid {
    grid-template-columns: 1fr;
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
    width: 68px;
  }

  .auth-brand h1 {
    font-size: 28px;
  }

  .auth-subtitle,
  .hero-note p {
    font-size: 14px;
  }

  .auth-card {
    padding: 28px 22px 24px;
    border-radius: 24px;
  }
}
</style>
