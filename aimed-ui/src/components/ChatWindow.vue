<template>
  <div class="page-shell">
    <aside class="brand-panel">
      <div class="brand-card">
        <div class="brand-lockup">
          <img :src="shulanLogo" alt="杭州树兰医院" class="brand-logo" />
          <div class="brand-copy">
            <p class="brand-eyebrow">Hangzhou Shulan Hospital</p>
            <h1 class="brand-title">杭州树兰医院智能导诊台</h1>
            <p class="brand-subtitle">本地知识问答与导诊工作台</p>
          </div>
        </div>
        <div class="brand-status-grid">
          <div class="brand-status-item">
            <span>引擎</span>
            <strong>{{ currentModelShortLabel }}</strong>
          </div>
          <div class="brand-status-item">
            <span>检索</span>
            <strong>RAG</strong>
          </div>
          <div class="brand-status-item">
            <span>知识</span>
            <strong>{{ knowledgeCount }}</strong>
          </div>
          <div class="brand-status-item">
            <span>状态</span>
            <strong>{{ isSending ? '处理中' : '在线' }}</strong>
          </div>
        </div>
      </div>

      <div class="control-card">
        <div class="control-header">
          <span>会话与知识库</span>
          <strong class="session-badge">#{{ uuid || '----' }}</strong>
        </div>
        <div class="control-actions">
          <el-button class="primary-action" @click="newChat">
            新建会话
          </el-button>
          <el-button
            class="secondary-action"
            @click="openKnowledgeCenter"
          >
            知识库管理
          </el-button>
        </div>
        <div class="knowledge-mini-grid">
          <button class="knowledge-mini-item knowledge-mini-item-button" type="button" @click="openKnowledgeCenter">
            <span>文件数</span>
            <strong>{{ knowledgeCount }}</strong>
          </button>
          <div class="knowledge-mini-item">
            <span>格式</span>
            <strong>PDF/Office</strong>
          </div>
        </div>
        <p class="knowledge-summary">点击进入独立知识库页，支持上传、查看、删除、编辑文本文件并查看 RAG 切分结果。</p>
      </div>

      <div class="suggestion-card">
        <div class="section-heading">
          <span>快捷指令</span>
          <small>双列速用</small>
        </div>
        <div class="prompt-grid">
          <button
            v-for="prompt in quickPrompts"
            :key="prompt.title"
            class="prompt-chip"
            type="button"
            @click="sendQuickPrompt(prompt.content)"
          >
            <span class="prompt-title">{{ prompt.title }}</span>
            <span class="prompt-description">{{ prompt.description }}</span>
          </button>
        </div>
      </div>
    </aside>

    <main class="workspace">
      <section class="hero-panel">
        <div class="hero-bar">
          <div class="hero-identity">
            <span class="hero-kicker">树兰状态栏</span>
            <strong>杭州树兰医院智能问答工作台</strong>
          </div>
          <div class="hero-summary">
            默认使用本地 Ollama 离线问答，可随时切到千问在线模式。
          </div>
        </div>
        <div class="hero-stats">
          <div class="stat-card">
            <span>知识库文件</span>
            <strong>{{ knowledgeCount }}</strong>
          </div>
          <div class="stat-card">
            <span>当前会话</span>
            <strong>#{{ uuid || '--' }}</strong>
          </div>
          <div class="stat-card">
            <span>当前模型</span>
            <strong>{{ currentModelShortLabel }}</strong>
          </div>
        </div>
      </section>

      <section class="chat-panel">
        <header class="chat-header">
          <div>
            <p class="chat-label">智能问答窗口</p>
            <h3>杭州树兰医院 AI 陪诊助手</h3>
          </div>
          <span class="status-pill">
            <span class="status-dot"></span>
            {{ isSending ? '正在生成回复' : '可开始提问' }}
          </span>
        </header>

        <div
          class="message-list"
          ref="messageListRef"
          @scroll="handleMessageListScroll"
        >
          <div
            v-for="(message, index) in messages"
            :key="index"
            :class="messageClass(message)"
          >
            <div class="message-avatar">
              <i :class="messageIcon(message)"></i>
            </div>
            <div class="message-body">
              <span class="message-role">{{ messageRole(message) }}</span>
              <div class="message-content" v-html="message.content"></div>
              <span
                class="loading-dots"
                v-if="message.isThinking || message.isTyping"
              >
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="dot"></span>
              </span>
            </div>
          </div>
        </div>
        <button
          v-if="showScrollToBottom"
          class="scroll-bottom-button"
          type="button"
          @click="scrollToBottom(true)"
        >
          查看最新回复
        </button>

        <footer class="composer">
          <div class="composer-hint">
            可直接提问医院信息，或附加病历/报告文件让系统结合内容回答问题。
          </div>
          <div class="model-switcher">
            <span class="model-switcher-label">模型入口</span>
            <button
              v-for="option in modelProviderOptions"
              :key="option.value"
              :class="['model-switch-button', { active: selectedModelProvider === option.value }]"
              type="button"
              @click="setModelProvider(option.value)"
            >
              <strong>{{ option.label }}</strong>
              <small>{{ option.description }}</small>
            </button>
          </div>
          <div v-if="chatAttachments.length" class="chat-attachment-list">
            <button
              v-for="(file, index) in chatAttachments"
              :key="`${file.name}-${file.size}-${index}`"
              :class="chatAttachmentClass(file)"
              type="button"
              @click="removeChatAttachment(index)"
            >
              <span class="chat-attachment-kind">{{ chatAttachmentKind(file) }}</span>
              <span class="chat-attachment-name">{{ file.name }}</span>
              <span class="chat-attachment-remove">移除</span>
            </button>
          </div>
          <div class="composer-row">
            <el-input
              v-model="inputMessage"
              class="composer-input"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 5 }"
              placeholder="例如：请根据最新上传资料，告诉我某门诊开放时间和注意事项"
              @compositionstart="isComposing = true"
              @compositionend="isComposing = false"
              @keydown="handleComposerKeydown"
            />
            <div class="composer-actions">
              <el-button
                class="ghost-action"
                @click="openChatAttachmentUpload"
                :disabled="isSending"
              >
                附加文件
              </el-button>
              <el-button
                class="send-action"
                type="primary"
                @click="sendMessage"
                :disabled="isSending"
              >
                发送
              </el-button>
            </div>
          </div>
          <div class="composer-tip">
            支持图片、PDF、Word、Excel、PPT、Markdown 等文件。<strong>Enter</strong> 发送，<strong>Shift + Enter</strong> 换行。{{ currentModelProviderTip }}
          </div>
          <input
            ref="chatAttachmentInputRef"
            class="knowledge-input"
            type="file"
            multiple
            accept=".png,.jpg,.jpeg,.webp,.gif,.bmp,.pdf,.doc,.docx,.md,.markdown,.txt,.csv,.rtf,.html,.htm,.xml,.odt,.ods,.odp,.xls,.xlsx,.ppt,.pptx"
            @change="handleChatAttachmentChange"
          />
        </footer>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import axios from 'axios'
import MarkdownIt from 'markdown-it'
import { v4 as uuidv4 } from 'uuid'
import shulanLogo from '@/assets/shulan-logo.png'

const LOCAL_OLLAMA = 'LOCAL_OLLAMA'
const QWEN_ONLINE = 'QWEN_ONLINE'

const quickPrompts = [
  {
    title: '门诊',
    description: '介绍门诊服务能力',
    content: '请介绍杭州树兰医院的门诊服务能力',
  },
  {
    title: '预约',
    description: '总结当前预约规则',
    content: '帮我总结当前知识库里与预约相关的规则',
  },
  {
    title: '上传',
    description: '说明支持的资料格式',
    content: '我想上传一份院内 PDF 做知识库，请告诉我支持哪些格式',
  },
  {
    title: '演示',
    description: '推荐一个展示问题',
    content: '请根据现有知识库推荐一个适合演示的问答问题',
  },
]

const messageListRef = ref()
const chatAttachmentInputRef = ref()
const isSending = ref(false)
const isComposing = ref(false)
const shouldStickToBottom = ref(true)
const uuid = ref()
const inputMessage = ref('')
const messages = ref([])
const knowledgeCount = ref(0)
const chatAttachments = ref([])
const selectedModelProvider = ref(LOCAL_OLLAMA)
const showScrollToBottom = computed(
  () => !shouldStickToBottom.value && messages.value.length > 1
)
const modelProviderOptions = [
  {
    value: LOCAL_OLLAMA,
    label: '本地 Ollama',
    shortLabel: 'Ollama',
    description: '默认离线，轻量文本+图片',
  },
  {
    value: QWEN_ONLINE,
    label: '千问在线',
    shortLabel: '千问',
    description: '需要联网，支持图片分析',
  },
]
const currentModelOption = computed(
  () => modelProviderOptions.find((option) => option.value === selectedModelProvider.value) || modelProviderOptions[0]
)
const currentModelShortLabel = computed(() => currentModelOption.value.shortLabel)
const currentModelProviderTip = computed(() =>
  selectedModelProvider.value === LOCAL_OLLAMA
    ? '当前为本地离线模式，文本问答走 qwen2.5:3b，图片分析走 qwen2.5vl:7b。'
    : '当前为千问在线模式，可继续使用图片分析。'
)

const medicalCalloutRules = [
  {
    pattern: /^(重点提示|重要提醒|特别提醒|温馨提示|注意|警示|风险提示|禁忌|危险信号|急诊指征)[：:]/,
    className: 'medical-callout-warning',
    label: '风险提示',
  },
  {
    pattern: /^(建议|就诊建议|处理建议|复诊建议|用药建议|护理建议)[：:]/,
    className: 'medical-callout-advice',
    label: '就诊建议',
  },
  {
    pattern: /^(检查建议|建议检查|推荐检查|检查项目|下一步检查)[：:]/,
    className: 'medical-callout-check',
    label: '检查建议',
  },
  {
    pattern: /^(结论|总结|核心结论|答复要点|要点|简要结论)[：:]/,
    className: 'medical-callout-info',
    label: '答复要点',
  },
]

const markdown = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: true,
  typographer: false,
})

const defaultLinkRender =
  markdown.renderer.rules.link_open ||
  ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))

markdown.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  token.attrSet('target', '_blank')
  token.attrSet('rel', 'noopener noreferrer')
  return defaultLinkRender(tokens, idx, options, env, self)
}

onMounted(async () => {
  initUUID()
  initModelProvider()
  await refreshKnowledgeFiles()
  showWelcomeMessage()
})

watch(
  messages,
  async () => {
    await scrollToBottom()
  },
  { deep: true }
)

const messageClass = (message) => {
  if (message.kind === 'system') {
    return 'message message-system'
  }
  return message.isUser ? 'message message-user' : 'message message-assistant'
}

const messageIcon = (message) => {
  if (message.kind === 'system') {
    return 'fa-solid fa-folder-plus'
  }
  return message.isUser ? 'fa-solid fa-user' : 'fa-solid fa-stethoscope'
}

const messageRole = (message) => {
  if (message.kind === 'system') {
    return '知识库更新'
  }
  return message.isUser ? '用户' : '树兰智能助手'
}

const pushSystemMessage = (content) => {
  messages.value.push({
    kind: 'system',
    isUser: false,
    content: convertStreamOutput(content),
    isTyping: false,
    isThinking: false,
  })
  shouldStickToBottom.value = true
}

const handleMessageListScroll = () => {
  const container = messageListRef.value
  if (!container) {
    return
  }

  const distanceToBottom =
    container.scrollHeight - container.scrollTop - container.clientHeight
  shouldStickToBottom.value = distanceToBottom < 48
}

const scrollToBottom = async (force = false) => {
  const container = messageListRef.value
  if (!container || (!force && !shouldStickToBottom.value)) {
    return
  }

  if (force) {
    shouldStickToBottom.value = true
  }

  await nextTick()
  requestAnimationFrame(() => {
    container.scrollTop = container.scrollHeight
  })
}

const showWelcomeMessage = () => {
  messages.value = [
    {
      isUser: false,
      content: convertStreamOutput(
        '你好，我是**杭州树兰医院智能问答助手**。\n\n我可以帮你做这几类事情：\n- 结合医院知识库回答就医流程、科室和院内信息\n- 结合你上传的文档或图片做辅助解读\n- 提供风险提示、检查建议和就诊建议\n\n提示：本地模式更适合离线基础问答；如果你需要更高质量回复，可以切换到**千问在线**。'
      ),
      isTyping: false,
      isThinking: false,
    },
  ]
  shouldStickToBottom.value = true
}

const initModelProvider = () => {
  const storedProvider = localStorage.getItem('chat_model_provider')
  selectedModelProvider.value =
    storedProvider === QWEN_ONLINE ? QWEN_ONLINE : LOCAL_OLLAMA
}

const setModelProvider = (provider) => {
  selectedModelProvider.value = provider === QWEN_ONLINE ? QWEN_ONLINE : LOCAL_OLLAMA
  localStorage.setItem('chat_model_provider', selectedModelProvider.value)
}

const sendQuickPrompt = (prompt) => {
  inputMessage.value = prompt
  sendMessage()
}

const sendMessage = () => {
  if (isSending.value || (!inputMessage.value.trim() && chatAttachments.value.length === 0)) {
    return
  }
  sendRequest(inputMessage.value.trim())
  inputMessage.value = ''
}

const handleComposerKeydown = (event) => {
  if (event.key !== 'Enter' || event.shiftKey) {
    return
  }

  if (event.isComposing || isComposing.value) {
    return
  }

  event.preventDefault()
  sendMessage()
}

const openKnowledgeCenter = () => {
  window.open('/knowledge', '_blank', 'noopener')
}

const openChatAttachmentUpload = () => {
  chatAttachmentInputRef.value?.click()
}

const handleChatAttachmentChange = (event) => {
  const files = Array.from(event.target.files || [])
  if (files.length === 0) {
    return
  }

  const mergedFiles = [...chatAttachments.value, ...files]
  const uniqueFiles = []
  const seenKeys = new Set()

  mergedFiles.forEach((file) => {
    const fileKey = `${file.name}-${file.size}-${file.lastModified}`
    if (!seenKeys.has(fileKey)) {
      seenKeys.add(fileKey)
      uniqueFiles.push(file)
    }
  })

  chatAttachments.value = uniqueFiles.slice(0, 5)
  event.target.value = ''
}

const removeChatAttachment = (index) => {
  chatAttachments.value.splice(index, 1)
}

const isImageFile = (file) => {
  const filename = file?.name?.toLowerCase?.() || ''
  return ['.png', '.jpg', '.jpeg', '.webp', '.gif', '.bmp'].some((ext) =>
    filename.endsWith(ext)
  )
}

const chatAttachmentKind = (file) => (isImageFile(file) ? '图片' : '文件')

const chatAttachmentClass = (file) =>
  isImageFile(file)
    ? 'chat-attachment-chip chat-attachment-chip-image'
    : 'chat-attachment-chip'

const refreshKnowledgeFiles = async () => {
  try {
    const { data } = await axios.get('/api/aimed/knowledge/files')
    knowledgeCount.value = Array.isArray(data) ? data.length : 0
  } catch (error) {
    console.error('获取知识库列表失败:', error)
  }
}

const buildChatRequest = (message, attachmentsSnapshot) => {
  if (attachmentsSnapshot.length) {
    const formData = new FormData()
    formData.append('memoryId', String(uuid.value))
    formData.append('message', message || '请结合我上传的文件回答。')
    formData.append('modelProvider', selectedModelProvider.value)
    attachmentsSnapshot.forEach((file) => formData.append('files', file))
    return { body: formData }
  }

  return {
    body: JSON.stringify({
      memoryId: uuid.value,
      message,
      modelProvider: selectedModelProvider.value,
    }),
    headers: {
      'Content-Type': 'application/json',
    },
  }
}

const streamChatResponse = async (message, attachmentsSnapshot, lastMsg) => {
  const { body, headers } = buildChatRequest(message, attachmentsSnapshot)
  const response = await fetch('/api/aimed/chat', {
    method: 'POST',
    headers,
    body,
  })

  if (!response.ok) {
    const errorText = await response.text()
    const traceId = response.headers.get('x-trace-id')
    throw new Error(buildTraceableMessage(errorText || `HTTP ${response.status}`, traceId))
  }

  if (!response.body) {
    throw new Error('响应流为空')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      const finalText = decoder.decode()
      if (finalText) {
        lastMsg.rawContent += finalText
      }
      break
    }

    const chunkText = decoder.decode(value, { stream: true })
    if (!chunkText) {
      continue
    }

    lastMsg.rawContent += chunkText
    lastMsg.content = convertStreamOutput(lastMsg.rawContent)
    scrollToBottom()
  }

  lastMsg.content = convertStreamOutput(lastMsg.rawContent)
}

const sendRequest = (message) => {
  isSending.value = true
  const attachmentsSnapshot = [...chatAttachments.value]
  const attachmentSummary = attachmentsSnapshot.length
    ? `\n\n**附加文件：**\n${attachmentsSnapshot.map((file) => `- ${file.name}`).join('\n')}`
    : ''
  const userMsg = {
    isUser: true,
    content: convertStreamOutput((message || '请结合我上传的文件回答。') + attachmentSummary),
    isTyping: false,
    isThinking: false,
  }

  if (messages.value.length > 0) {
    messages.value.push(userMsg)
  }

  const botMsg = {
    isUser: false,
    content: '',
    rawContent: '',
    isTyping: true,
    isThinking: false,
  }
  messages.value.push(botMsg)
  const lastMsg = messages.value[messages.value.length - 1]
  shouldStickToBottom.value = true
  scrollToBottom(true)

  streamChatResponse(message, attachmentsSnapshot, lastMsg)
    .then(() => {
      messages.value.at(-1).isTyping = false
      isSending.value = false
      chatAttachments.value = []
    })
    .catch((error) => {
      console.error('流式错误:', error)
      messages.value.at(-1).content = convertStreamOutput(resolveRequestError(error))
      messages.value.at(-1).isTyping = false
      isSending.value = false
      shouldStickToBottom.value = true
      scrollToBottom(true)
    })
}

const initUUID = () => {
  let storedUUID = localStorage.getItem('user_uuid')
  if (!storedUUID) {
    storedUUID = uuidToNumber(uuidv4())
    localStorage.setItem('user_uuid', storedUUID)
  }
  uuid.value = storedUUID
}

const uuidToNumber = (uuidValue) => {
  let number = 0
  for (let i = 0; i < uuidValue.length && i < 6; i++) {
    const hexValue = uuidValue[i]
    number = number * 16 + (parseInt(hexValue, 16) || 0)
  }
  return number % 1000000
}

const convertStreamOutput = (output) => {
  if (!output) {
    return ''
  }

  const normalizedOutput = output.replace(/\t/g, '    ')
  return enhanceMedicalMarkup(markdown.render(normalizedOutput))
}

const resolveRequestError = (error) => {
  const serverMessage =
    error?.response?.data?.message || error?.response?.data?.error || error?.message
  if (serverMessage) {
    return `当前服务暂时不可用：${serverMessage}`
  }

  return '当前模型服务暂时不稳定，请稍后重试。若持续失败，请检查本机 Ollama 或在线模型连通性。'
}

const buildTraceableMessage = (message, traceId) => {
  if (!traceId) {
    return message
  }
  return `${message}（traceId: ${traceId}）`
}

const enhanceMedicalMarkup = (renderedHtml) => {
  if (typeof DOMParser === 'undefined') {
    return renderedHtml
  }

  const parser = new DOMParser()
  const documentNode = parser.parseFromString(
    `<div class="markdown-root">${renderedHtml}</div>`,
    'text/html'
  )
  const root = documentNode.body.firstElementChild

  if (!root) {
    return renderedHtml
  }

  root.querySelectorAll('table').forEach((table) => {
    if (table.parentElement?.classList.contains('table-scroll')) {
      return
    }

    const wrapper = documentNode.createElement('div')
    wrapper.className = 'table-scroll'
    table.parentNode?.insertBefore(wrapper, table)
    wrapper.appendChild(table)
  })

  root.querySelectorAll('p').forEach((paragraph) => {
    if (paragraph.closest('blockquote, li, td, th, pre, .medical-callout')) {
      return
    }

    const text = paragraph.textContent?.trim() || ''
    const matchedRule = medicalCalloutRules.find((rule) => rule.pattern.test(text))
    if (!matchedRule) {
      return
    }

    const wrapper = documentNode.createElement('div')
    wrapper.className = `medical-callout ${matchedRule.className}`
    const header = documentNode.createElement('div')
    header.className = 'medical-callout-header'

    const icon = documentNode.createElement('span')
    icon.className = 'medical-callout-icon'
    icon.setAttribute('aria-hidden', 'true')

    const label = documentNode.createElement('span')
    label.className = 'medical-callout-label'
    label.textContent = matchedRule.label

    const body = documentNode.createElement('div')
    body.className = 'medical-callout-body'

    paragraph.parentNode?.insertBefore(wrapper, paragraph)
    header.appendChild(icon)
    header.appendChild(label)
    wrapper.appendChild(header)
    wrapper.appendChild(body)
    body.appendChild(paragraph)
  })

  return root.innerHTML
}

const newChat = () => {
  localStorage.removeItem('user_uuid')
  window.location.reload()
}
</script>

<style scoped>
.page-shell {
  display: grid;
  height: 100dvh;
  min-height: 100dvh;
  grid-template-columns: 224px minmax(0, 1fr);
  gap: 16px;
  padding: 16px;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(111, 194, 190, 0.24), transparent 26%),
    radial-gradient(circle at bottom right, rgba(25, 83, 92, 0.18), transparent 30%),
    linear-gradient(135deg, #eef7f6 0%, #f6fbfb 55%, #eef3f8 100%);
}

.brand-panel,
.workspace {
  min-width: 0;
  min-height: 0;
}

.brand-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
  padding-right: 4px;
}

.brand-card,
.control-card,
.suggestion-card,
.hero-panel,
.chat-panel {
  border: 1px solid rgba(60, 115, 122, 0.12);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 18px 40px rgba(24, 73, 83, 0.08);
  backdrop-filter: blur(14px);
}

.brand-card,
.control-card,
.suggestion-card {
  padding: 14px;
}

.brand-lockup {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.brand-copy {
  min-width: 0;
}

.brand-logo {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #eef8f7 100%);
  padding: 7px;
  box-shadow: inset 0 0 0 1px rgba(79, 160, 160, 0.12);
}

.brand-eyebrow,
.chat-label,
.hero-kicker {
  margin: 0 0 6px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #5c8d8f;
}

.brand-title {
  margin: 0;
  font-size: 17px;
  line-height: 1.2;
  color: #16363c;
}

.brand-subtitle {
  margin: 6px 0 0;
  font-size: 11px;
  line-height: 1.5;
  color: #4e6f74;
}

.brand-status-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 12px;
}

.brand-status-item {
  padding: 9px 10px;
  border-radius: 12px;
  background: rgba(108, 189, 180, 0.12);
  box-shadow: inset 0 0 0 1px rgba(79, 160, 160, 0.1);
}

.brand-status-item span {
  display: block;
  margin-bottom: 4px;
  font-size: 10px;
  color: #5c8d8f;
}

.brand-status-item strong {
  font-size: 13px;
  color: #19535c;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border-radius: 999px;
  padding: 8px 12px;
  font-size: 11px;
  font-weight: 700;
  color: #19535c;
  background: rgba(108, 189, 180, 0.14);
}

.control-header,
.section-heading,
.chat-header,
.hero-stats,
.composer-actions {
  display: flex;
  align-items: center;
}

.control-header,
.section-heading {
  justify-content: space-between;
  margin-bottom: 10px;
  color: #183f45;
}

.section-heading small {
  font-size: 11px;
  color: #6d8a90;
}

.session-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  padding: 4px 8px;
  font-size: 11px;
  color: #1d5962;
  background: rgba(108, 189, 180, 0.14);
}

.control-actions {
  display: grid;
  gap: 8px;
}

.primary-action,
.secondary-action,
.ghost-action,
.send-action {
  width: 100%;
  min-height: 38px;
  border-radius: 12px;
  font-weight: 700;
}

.primary-action,
.send-action {
  border: none;
  background: linear-gradient(135deg, #2e888c 0%, #57ada3 100%);
  box-shadow: 0 16px 28px rgba(56, 139, 140, 0.22);
}

.secondary-action,
.ghost-action {
  border-color: rgba(46, 136, 140, 0.22);
  color: #226b73;
  background: rgba(227, 244, 242, 0.72);
}

.knowledge-mini-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 10px;
}

.knowledge-mini-item {
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(227, 244, 242, 0.72);
}

.knowledge-mini-item-button {
  border: none;
  text-align: left;
  cursor: pointer;
}

.knowledge-mini-item span {
  display: block;
  margin-bottom: 4px;
  font-size: 10px;
  color: #6d8a90;
}

.knowledge-mini-item strong {
  font-size: 12px;
  color: #1f5e67;
}

.knowledge-summary {
  margin: 10px 0 0;
  font-size: 11px;
  line-height: 1.5;
  color: #4d6f74;
}

.knowledge-input {
  display: none;
}

.prompt-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.prompt-chip {
  width: 100%;
  min-height: 86px;
  padding: 10px 10px 12px;
  border: 1px solid rgba(79, 160, 160, 0.16);
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(247, 252, 251, 0.96) 0%, rgba(239, 248, 247, 0.9) 100%);
  text-align: left;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  font: inherit;
  color: #17393f;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.prompt-title {
  display: block;
  font-size: 12px;
  font-weight: 700;
  color: #184047;
}

.prompt-description {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  line-height: 1.4;
  color: #5a7b80;
}

.prompt-chip:hover {
  transform: translateY(-1px);
  border-color: rgba(46, 136, 140, 0.32);
  box-shadow: 0 16px 28px rgba(39, 105, 111, 0.08);
}

.workspace {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  overflow: hidden;
}

.hero-panel {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 10px 16px;
}

.hero-bar {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 14px;
}

.hero-identity {
  display: inline-flex;
  min-width: 0;
  flex-shrink: 0;
  align-items: center;
  gap: 10px;
}

.hero-identity strong {
  font-size: 14px;
  color: #14373e;
  white-space: nowrap;
}

.hero-summary {
  min-width: 0;
  font-size: 12px;
  line-height: 1.4;
  color: #4f7076;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.hero-stats {
  display: grid;
  grid-auto-flow: column;
  gap: 8px;
  justify-content: end;
  align-items: center;
}

.stat-card {
  min-width: 88px;
  padding: 8px 10px;
  border-radius: 12px;
  background: linear-gradient(180deg, rgba(238, 247, 246, 0.96) 0%, rgba(226, 241, 241, 0.82) 100%);
  box-shadow: inset 0 0 0 1px rgba(90, 152, 156, 0.12);
}

.stat-card span {
  display: block;
  margin-bottom: 6px;
  font-size: 11px;
  color: #65878d;
}

.stat-card strong {
  font-size: 16px;
  color: #17424a;
}

.chat-panel {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px 14px;
  border-bottom: 1px solid rgba(70, 120, 126, 0.08);
}

.chat-header h3 {
  margin: 0;
  font-size: 22px;
  color: #173a40;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #49b7a7;
  box-shadow: 0 0 0 6px rgba(73, 183, 167, 0.12);
}

.message-list {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 18px 20px;
}

.scroll-bottom-button {
  position: absolute;
  right: 36px;
  bottom: 126px;
  z-index: 2;
  border: 1px solid rgba(46, 136, 140, 0.14);
  border-radius: 999px;
  padding: 10px 14px;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  color: #19535c;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 16px 30px rgba(28, 79, 88, 0.12);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.scroll-bottom-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 18px 34px rgba(28, 79, 88, 0.16);
}

.message {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.message-user {
  flex-direction: row-reverse;
}

.message-system {
  align-self: center;
  width: min(760px, 100%);
}

.message-avatar {
  display: flex;
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  color: #ffffff;
  background: linear-gradient(135deg, #2e888c 0%, #5db6a7 100%);
  box-shadow: 0 12px 24px rgba(46, 136, 140, 0.18);
}

.message-user .message-avatar {
  background: linear-gradient(135deg, #17474f 0%, #2f6f78 100%);
}

.message-system .message-avatar {
  background: linear-gradient(135deg, #7faeb0 0%, #4e8d93 100%);
}

.message-body {
  max-width: min(76%, 760px);
  padding: 14px 16px;
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 14px 30px rgba(33, 74, 83, 0.08);
}

.message-assistant .message-body {
  border-top-left-radius: 8px;
  background: linear-gradient(180deg, #ffffff 0%, #f6fbfb 100%);
}

.message-user .message-body {
  border-top-right-radius: 8px;
  background: linear-gradient(180deg, #1d5158 0%, #2f7580 100%);
  color: #ffffff;
}

.message-system .message-body {
  width: 100%;
  max-width: none;
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(231, 245, 242, 0.92) 0%, rgba(246, 252, 251, 0.96) 100%);
  box-shadow: inset 0 0 0 1px rgba(79, 160, 160, 0.12);
}

.message-role {
  display: inline-block;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #6d8a90;
}

.message-user .message-role {
  color: rgba(255, 255, 255, 0.76);
}

.message-content {
  font-size: 15px;
  line-height: 1.8;
  color: #23474d;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.message-content :deep(*) {
  max-width: 100%;
}

.message-content :deep(p),
.message-content :deep(ul),
.message-content :deep(ol),
.message-content :deep(pre),
.message-content :deep(blockquote),
.message-content :deep(table),
.message-content :deep(.table-scroll),
.message-content :deep(h1),
.message-content :deep(h2),
.message-content :deep(h3),
.message-content :deep(h4),
.message-content :deep(.medical-callout) {
  margin: 0 0 12px;
}

.message-content :deep(p:last-child),
.message-content :deep(ul:last-child),
.message-content :deep(ol:last-child),
.message-content :deep(pre:last-child),
.message-content :deep(blockquote:last-child),
.message-content :deep(table:last-child),
.message-content :deep(.medical-callout:last-child),
.message-content :deep(.table-scroll:last-child) {
  margin-bottom: 0;
}

.message-content :deep(h1),
.message-content :deep(h2),
.message-content :deep(h3),
.message-content :deep(h4) {
  line-height: 1.35;
  color: #173a40;
  letter-spacing: 0.01em;
}

.message-content :deep(h1) {
  font-size: 22px;
}

.message-content :deep(h2) {
  font-size: 19px;
}

.message-content :deep(h3) {
  font-size: 17px;
}

.message-content :deep(ul),
.message-content :deep(ol) {
  padding-left: 20px;
}

.message-content :deep(li + li) {
  margin-top: 6px;
}

.message-content :deep(blockquote) {
  border-left: 3px solid rgba(46, 136, 140, 0.26);
  padding-left: 12px;
  color: #55757b;
}

.message-content :deep(pre) {
  overflow-x: auto;
  border-radius: 14px;
  padding: 12px 14px;
  background: #eff6f6;
  box-shadow: inset 0 0 0 1px rgba(73, 124, 128, 0.08);
}

.message-content :deep(code) {
  border-radius: 8px;
  padding: 2px 6px;
  font-size: 0.92em;
  background: rgba(37, 97, 105, 0.08);
}

.message-content :deep(pre code) {
  padding: 0;
  background: transparent;
}

.message-content :deep(a) {
  color: #1f7a81;
  text-decoration: underline;
}

.message-content :deep(.table-scroll) {
  overflow-x: auto;
}

.message-content :deep(table) {
  min-width: 100%;
  border-collapse: collapse;
  border-radius: 12px;
  box-shadow: inset 0 0 0 1px rgba(75, 125, 129, 0.12);
}

.message-content :deep(th),
.message-content :deep(td) {
  padding: 8px 10px;
  border: 1px solid rgba(75, 125, 129, 0.12);
  text-align: left;
}

.message-content :deep(th) {
  background: rgba(227, 244, 242, 0.72);
}

.message-content :deep(strong) {
  color: #15393f;
}

.message-content :deep(.medical-callout) {
  border-radius: 16px;
  padding: 12px 14px 14px;
  box-shadow: inset 0 0 0 1px rgba(80, 132, 136, 0.12);
}

.message-content :deep(.medical-callout-header) {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.message-content :deep(.medical-callout-icon) {
  position: relative;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: inset 0 0 0 1px rgba(72, 120, 124, 0.12);
}

.message-content :deep(.medical-callout-icon::before) {
  content: '';
  position: absolute;
  inset: 6px;
  background-repeat: no-repeat;
  background-position: center;
  background-size: contain;
}

.message-content :deep(.medical-callout-label) {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.message-content :deep(.medical-callout-body) {
  font-size: inherit;
  line-height: inherit;
}

.message-content :deep(.medical-callout-body > *:last-child) {
  margin-bottom: 0;
}

.message-content :deep(.medical-callout p) {
  margin: 0;
}

.message-content :deep(.medical-callout-warning) {
  background: linear-gradient(180deg, rgba(255, 246, 231, 0.96) 0%, rgba(255, 251, 241, 0.92) 100%);
  box-shadow: inset 0 0 0 1px rgba(214, 157, 61, 0.18);
}

.message-content :deep(.medical-callout-warning .medical-callout-label) {
  color: #8c5d10;
}

.message-content :deep(.medical-callout-warning .medical-callout-icon::before) {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23b97712' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M12 3 2.8 19a1.2 1.2 0 0 0 1 2h16.4a1.2 1.2 0 0 0 1-2L12 3Z'/%3E%3Cpath d='M12 9v5'/%3E%3Cpath d='M12 17h.01'/%3E%3C/svg%3E");
}

.message-content :deep(.medical-callout-advice) {
  background: linear-gradient(180deg, rgba(231, 247, 242, 0.96) 0%, rgba(242, 251, 248, 0.92) 100%);
}

.message-content :deep(.medical-callout-advice .medical-callout-label) {
  color: #1f6b5f;
}

.message-content :deep(.medical-callout-advice .medical-callout-icon::before) {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23217367' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m5 12 4 4L19 6'/%3E%3C/svg%3E");
}

.message-content :deep(.medical-callout-check) {
  background: linear-gradient(180deg, rgba(236, 244, 255, 0.96) 0%, rgba(245, 249, 255, 0.92) 100%);
  box-shadow: inset 0 0 0 1px rgba(86, 123, 191, 0.14);
}

.message-content :deep(.medical-callout-check .medical-callout-label) {
  color: #315d9a;
}

.message-content :deep(.medical-callout-check .medical-callout-icon::before) {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23315d9a' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Crect x='4' y='3' width='16' height='18' rx='2'/%3E%3Cpath d='M8 7h8'/%3E%3Cpath d='M8 11h8'/%3E%3Cpath d='M8 15h5'/%3E%3C/svg%3E");
}

.message-content :deep(.medical-callout-info) {
  background: linear-gradient(180deg, rgba(233, 244, 249, 0.96) 0%, rgba(244, 250, 252, 0.92) 100%);
}

.message-content :deep(.medical-callout-info .medical-callout-label) {
  color: #2c6b78;
}

.message-content :deep(.medical-callout-info .medical-callout-icon::before) {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%232c6b78' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Ccircle cx='12' cy='12' r='9'/%3E%3Cpath d='M12 10v6'/%3E%3Cpath d='M12 7h.01'/%3E%3C/svg%3E");
}

.message-user .message-content {
  color: #ffffff;
}

.message-user .message-content :deep(h1),
.message-user .message-content :deep(h2),
.message-user .message-content :deep(h3),
.message-user .message-content :deep(h4),
.message-user .message-content :deep(a),
.message-user .message-content :deep(blockquote) {
  color: #ffffff;
}

.message-user .message-content :deep(pre) {
  background: rgba(255, 255, 255, 0.12);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.12);
}

.message-user .message-content :deep(code) {
  background: rgba(255, 255, 255, 0.14);
}

.message-user .message-content :deep(th) {
  background: rgba(255, 255, 255, 0.12);
}

.message-user .message-content :deep(.medical-callout) {
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.14);
}

.message-user .message-content :deep(.medical-callout-warning),
.message-user .message-content :deep(.medical-callout-advice),
.message-user .message-content :deep(.medical-callout-check),
.message-user .message-content :deep(.medical-callout-info) {
  background: rgba(255, 255, 255, 0.1);
}

.message-user .message-content :deep(.medical-callout-label) {
  color: #ffffff;
}

.message-user .message-content :deep(.medical-callout-icon) {
  background: rgba(255, 255, 255, 0.14);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.14);
}

.message-user .message-content :deep(.medical-callout-warning .medical-callout-icon::before),
.message-user .message-content :deep(.medical-callout-advice .medical-callout-icon::before),
.message-user .message-content :deep(.medical-callout-check .medical-callout-icon::before),
.message-user .message-content :deep(.medical-callout-info .medical-callout-icon::before) {
  filter: brightness(0) invert(1);
}

.loading-dots {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  margin-top: 10px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: currentColor;
  animation: pulse 1.2s infinite ease-in-out both;
}

.dot:nth-child(2) {
  animation-delay: -0.4s;
}

.dot:nth-child(3) {
  animation-delay: -0.8s;
}

.composer {
  flex-shrink: 0;
  padding: 16px 20px 18px;
  border-top: 1px solid rgba(70, 120, 126, 0.08);
  background: linear-gradient(180deg, rgba(248, 252, 252, 0.95) 0%, rgba(241, 248, 248, 0.85) 100%);
}

.composer-hint,
.composer-tip {
  font-size: 13px;
  color: #608188;
}

.model-switcher {
  display: grid;
  grid-template-columns: auto repeat(2, minmax(0, 1fr));
  gap: 10px;
  align-items: stretch;
  margin: 12px 0 0;
}

.model-switcher-label {
  display: inline-flex;
  align-items: center;
  padding: 0 4px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #5b7c81;
}

.model-switch-button {
  border: 1px solid rgba(79, 160, 160, 0.16);
  border-radius: 14px;
  background: rgba(246, 251, 251, 0.92);
  padding: 10px 12px;
  text-align: left;
  color: #19454d;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.model-switch-button strong,
.model-switch-button small {
  display: block;
}

.model-switch-button strong {
  font-size: 12px;
}

.model-switch-button small {
  margin-top: 4px;
  font-size: 11px;
  color: #64848a;
}

.model-switch-button.active {
  border-color: rgba(46, 136, 140, 0.32);
  background: linear-gradient(180deg, rgba(230, 245, 242, 0.96) 0%, rgba(244, 251, 250, 0.98) 100%);
  box-shadow: 0 14px 26px rgba(28, 79, 88, 0.08);
}

.model-switch-button:hover {
  transform: translateY(-1px);
}

.chat-attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 10px 0 0;
}

.chat-attachment-chip {
  display: inline-flex;
  max-width: 100%;
  align-items: center;
  gap: 8px;
  border: 1px solid rgba(79, 160, 160, 0.16);
  border-radius: 999px;
  padding: 7px 10px;
  background: rgba(236, 246, 245, 0.92);
  color: #1d5962;
  cursor: pointer;
}

.chat-attachment-chip-image {
  background: rgba(232, 240, 255, 0.9);
  border-color: rgba(89, 127, 194, 0.18);
}

.chat-attachment-kind {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  min-width: 34px;
  border-radius: 999px;
  padding: 3px 6px;
  font-size: 10px;
  font-weight: 700;
  color: #1d5962;
  background: rgba(255, 255, 255, 0.76);
}

.chat-attachment-name {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 700;
}

.chat-attachment-remove {
  font-size: 11px;
  color: #6d8a90;
}

.composer-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 160px;
  gap: 12px;
  margin: 12px 0 8px;
}

.composer-input :deep(.el-textarea__inner) {
  border-radius: 16px;
  border-color: rgba(67, 126, 133, 0.12);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 16px 32px rgba(32, 80, 88, 0.06);
  padding: 14px 16px;
  font-size: 14px;
  line-height: 1.6;
}

.composer-actions {
  flex-direction: column;
  gap: 10px;
}

@keyframes pulse {
  0%,
  100% {
    transform: scale(0.68);
    opacity: 0.45;
  }

  50% {
    transform: scale(1);
    opacity: 1;
  }
}

@media (max-width: 1080px) {
  .page-shell {
    grid-template-columns: 1fr;
    padding: 16px;
    height: auto;
    min-height: 100dvh;
    overflow: auto;
  }

  .hero-panel {
    align-items: stretch;
    flex-direction: column;
  }

  .hero-stats {
    grid-auto-flow: row;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .hero-bar {
    width: 100%;
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-identity {
    width: 100%;
    justify-content: space-between;
  }

  .hero-summary {
    white-space: normal;
  }

  .brand-panel,
  .workspace {
    overflow: visible;
  }
}

@media (max-width: 768px) {
  .page-shell {
    gap: 16px;
    padding: 12px;
  }

  .brand-card,
  .control-card,
  .suggestion-card,
  .hero-panel,
  .chat-panel {
    border-radius: 22px;
  }

  .brand-lockup {
    align-items: flex-start;
  }

  .brand-logo {
    width: 68px;
    height: 68px;
    border-radius: 20px;
  }

  .brand-title {
    font-size: 22px;
  }

  .hero-copy h2 {
    font-size: 26px;
  }

  .prompt-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chat-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .message-list {
    max-height: min(52vh, 560px);
    padding: 18px 16px;
  }

  .scroll-bottom-button {
    right: 20px;
    bottom: 104px;
  }

  .message-body {
    max-width: 100%;
  }

  .prompt-grid {
    grid-template-columns: 1fr 1fr;
  }

  .composer {
    padding: 16px;
  }

  .composer-row {
    grid-template-columns: 1fr;
  }

  .composer-actions {
    flex-direction: row;
  }

  .model-switcher {
    grid-template-columns: 1fr;
  }
}
</style>
