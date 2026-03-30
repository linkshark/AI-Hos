<template>
  <div class="knowledge-shell">
    <aside class="knowledge-sidebar">
      <div class="sidebar-card brand-card">
        <div class="brand-lockup">
          <img :src="shulanLogo" alt="杭州树兰医院" class="brand-logo" />
          <div>
            <p class="brand-eyebrow">Knowledge Center</p>
            <h1>树兰知识库工作台</h1>
            <p class="brand-subtitle">管理知识文件、查看原文与 RAG 切分结果。</p>
          </div>
        </div>
        <button class="back-button" type="button" @click="backToChat">
          返回智能问答
        </button>
      </div>

      <div class="sidebar-card">
        <div class="sidebar-heading">
          <span>知识操作</span>
          <strong>{{ fileCountText }}</strong>
        </div>
        <div class="action-grid">
          <button
            class="primary-button"
            type="button"
            @click="openUpload"
            :disabled="isUploading"
          >
            {{ isUploading ? '上传中...' : '上传知识文件' }}
          </button>
          <button
            class="secondary-button"
            type="button"
            @click="loadFiles(selectedHash)"
            :disabled="isLoading"
          >
            刷新列表
          </button>
        </div>
        <p class="sidebar-note">
          支持 PDF、Word、Excel、PPT、Markdown、TXT、HTML、XML 等格式。文本类文件支持在线编辑。
        </p>
        <input
          ref="uploadInputRef"
          class="hidden-input"
          type="file"
          multiple
          accept=".pdf,.doc,.docx,.md,.markdown,.txt,.csv,.rtf,.html,.htm,.xml,.odt,.ods,.odp,.xls,.xlsx,.ppt,.pptx"
          @change="uploadKnowledge"
        />
      </div>

      <div class="sidebar-card">
        <div class="sidebar-heading">
          <span>快速筛选</span>
          <strong>{{ filteredFiles.length }}</strong>
        </div>
        <input
          v-model.trim="searchKeyword"
          class="search-input"
          type="text"
          placeholder="搜索文件名、哈希、解析器"
        />
        <div class="knowledge-list">
          <button
            v-for="file in filteredFiles"
            :key="file.hash"
            :class="['knowledge-item', { active: file.hash === selectedHash }]"
            type="button"
            @click="selectFile(file.hash)"
          >
            <div class="knowledge-item-top">
              <span class="knowledge-name">{{ file.originalFilename || file.fileName }}</span>
              <span :class="['source-tag', `source-tag-${file.source}`]">
                {{ sourceLabel(file.source) }}
              </span>
            </div>
            <div class="knowledge-meta">
              <span>{{ file.extension?.toUpperCase() || 'FILE' }}</span>
              <span>{{ formatSize(file.size) }}</span>
            </div>
            <div class="knowledge-meta">
              <span>{{ file.parser || '未知解析器' }}</span>
              <span>{{ file.editable ? '可编辑' : '只读' }}</span>
            </div>
            <div class="knowledge-meta">
              <span :class="['processing-tag', `processing-tag-${(file.processingStatus || 'UNKNOWN').toLowerCase()}`]">
                {{ statusLabel(file.processingStatus) }}
              </span>
              <span>{{ file.statusMessage || '等待处理' }}</span>
            </div>
            <div v-if="file.processingStatus === 'PROCESSING'" class="progress-inline">
              <div class="progress-track progress-track-compact">
                <span class="progress-fill" :style="{ width: `${safeProgress(file.progressPercent)}%` }" />
              </div>
              <span class="progress-inline-text">
                {{ safeProgress(file.progressPercent) }}%
                <template v-if="file.totalBatches">
                  · {{ file.currentBatch || 0 }}/{{ file.totalBatches }}
                </template>
              </span>
            </div>
          </button>
          <div v-if="!filteredFiles.length" class="empty-list">
            当前没有匹配的知识文件。
          </div>
        </div>
      </div>
    </aside>

    <main class="knowledge-main">
      <section class="summary-card">
        <div>
          <p class="summary-kicker">Knowledge Detail</p>
          <h2>{{ selectedDetail?.originalFilename || '请选择一个知识文件' }}</h2>
        </div>
        <div class="summary-stats">
          <div class="summary-stat">
            <span>切分块数</span>
            <strong>{{ selectedDetail?.chunks?.length || 0 }}</strong>
          </div>
          <div class="summary-stat">
            <span>解析字符</span>
            <strong>{{ selectedDetail?.extractedText?.length || 0 }}</strong>
          </div>
          <div class="summary-stat">
            <span>状态</span>
            <strong>{{ detailStatusText }}</strong>
          </div>
          <div v-if="selectedDetail?.processingStatus === 'PROCESSING'" class="summary-stat summary-stat-progress">
            <span>处理进度</span>
            <strong>{{ safeProgress(selectedDetail?.progressPercent) }}%</strong>
          </div>
        </div>
      </section>

      <section v-if="selectedDetail" class="detail-card">
        <div class="detail-toolbar">
          <div class="detail-badges">
            <span class="detail-badge">哈希 {{ selectedDetail.hash }}</span>
            <span class="detail-badge">{{ sourceLabel(selectedDetail.source) }}</span>
            <span class="detail-badge">{{ selectedDetail.parser || '未知解析器' }}</span>
            <span :class="['detail-badge', 'detail-badge-status', `processing-tag-${(selectedDetail.processingStatus || 'UNKNOWN').toLowerCase()}`]">
              {{ statusLabel(selectedDetail.processingStatus) }}
            </span>
          </div>
          <div class="detail-actions">
            <button
              v-if="selectedDetail.editable && selectedDetail.processingStatus === 'READY' && !isEditing"
              class="secondary-button slim-button"
              type="button"
              @click="startEdit"
            >
              编辑内容
            </button>
            <button
              v-if="isEditing"
              class="secondary-button slim-button"
              type="button"
              @click="cancelEdit"
            >
              取消
            </button>
            <button
              v-if="isEditing"
              class="primary-button slim-button"
              type="button"
              @click="saveEdit"
              :disabled="isSaving"
            >
              {{ isSaving ? '保存中...' : '保存并重建 RAG' }}
            </button>
            <button
              v-if="selectedDetail.deletable"
              class="danger-button slim-button"
              type="button"
              @click="deleteFile"
              :disabled="isDeleting"
            >
              {{ isDeleting ? '删除中...' : '删除文件' }}
            </button>
          </div>
        </div>

        <div class="detail-grid">
          <div class="meta-card">
            <span>文件名</span>
            <strong>{{ selectedDetail.originalFilename }}</strong>
          </div>
          <div class="meta-card">
            <span>格式</span>
            <strong>{{ selectedDetail.extension?.toUpperCase() || 'FILE' }}</strong>
          </div>
          <div class="meta-card">
            <span>大小</span>
            <strong>{{ formatSize(selectedDetail.size) }}</strong>
          </div>
          <div class="meta-card">
            <span>管理能力</span>
            <strong>{{ selectedDetail.editable ? '可编辑' : '只读查看' }}</strong>
          </div>
        </div>

        <div v-if="selectedDetail.processingStatus === 'PROCESSING'" class="progress-card">
          <div class="progress-card-head">
            <div>
              <span class="progress-label">后台处理进度</span>
              <strong class="progress-value">{{ safeProgress(selectedDetail.progressPercent) }}%</strong>
            </div>
            <small v-if="selectedDetail.totalBatches">
              第 {{ selectedDetail.currentBatch || 0 }} / {{ selectedDetail.totalBatches }} 批
            </small>
          </div>
          <div class="progress-track">
            <span class="progress-fill" :style="{ width: `${safeProgress(selectedDetail.progressPercent)}%` }" />
          </div>
          <p class="progress-note">
            {{ selectedDetail.statusMessage || '正在处理中，请稍候。' }}
          </p>
        </div>

        <div class="content-card">
          <div class="section-heading">
            <span>解析原文</span>
            <small>{{ isEditing ? '编辑模式' : '查看模式' }}</small>
          </div>
          <p v-if="selectedDetail.statusMessage" class="status-note">{{ selectedDetail.statusMessage }}</p>
          <textarea
            v-if="isEditing"
            v-model="editorContent"
            class="content-editor"
            spellcheck="false"
          />
          <pre v-else class="content-preview">{{ selectedDetail.extractedText || selectedDetail.statusMessage || '暂无内容' }}</pre>
        </div>

        <div class="content-card">
          <div class="section-heading">
            <span>RAG 切分结果</span>
            <small>展示入向量库前的 chunk 内容</small>
          </div>
          <div class="chunk-list">
            <details
              v-for="chunk in selectedDetail.chunks"
              :key="chunk.index"
              class="chunk-item"
            >
              <summary>
                <span>Chunk {{ chunk.index }}</span>
                <span>{{ chunk.characterCount }} 字</span>
              </summary>
              <p class="chunk-preview">{{ chunk.preview }}</p>
              <pre class="chunk-content">{{ chunk.content }}</pre>
            </details>
            <div v-if="!selectedDetail.chunks?.length" class="empty-chunks">
              {{ selectedDetail.processingStatus === 'PROCESSING' ? '当前文件正在处理中，RAG 切分完成后会自动刷新。' : '当前文件还没有可展示的切分结果。' }}
            </div>
          </div>
        </div>
      </section>

      <section v-else class="empty-detail">
        <h3>知识库页已就绪</h3>
        <p>从左侧选择一个知识文件，即可查看原文、删除/编辑能力和切分后的 RAG 数据。</p>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import shulanLogo from '@/assets/shulan-logo.png'

const uploadInputRef = ref()
const files = ref([])
const searchKeyword = ref('')
const selectedHash = ref('')
const selectedDetail = ref(null)
const editorContent = ref('')
const isLoading = ref(false)
const isUploading = ref(false)
const isSaving = ref(false)
const isDeleting = ref(false)
const isEditing = ref(false)
const wsConnected = ref(false)
let knowledgeSocket = null
let reconnectTimer = null

const filteredFiles = computed(() => {
  const keyword = searchKeyword.value.toLowerCase()
  if (!keyword) {
    return files.value
  }

  return files.value.filter((file) =>
    [file.originalFilename, file.fileName, file.hash, file.parser]
      .filter(Boolean)
      .some((value) => value.toLowerCase().includes(keyword))
  )
})

const fileCountText = computed(() => `${files.value.length} 个文件`)
const detailStatusText = computed(() => {
  if (isSaving.value) {
    return '重建中'
  }
  if (selectedDetail.value?.processingStatus === 'PROCESSING') {
    return '处理中'
  }
  if (selectedDetail.value?.processingStatus === 'FAILED') {
    return '失败'
  }
  if (selectedDetail.value?.editable) {
    return '可编辑'
  }
  return selectedDetail.value ? '只读' : '待选择'
})

onMounted(async () => {
  connectKnowledgeSocket()
  await loadFiles()
})

onBeforeUnmount(() => {
  wsConnected.value = false
  if (reconnectTimer) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (knowledgeSocket) {
    knowledgeSocket.close()
    knowledgeSocket = null
  }
})

const openUpload = () => {
  uploadInputRef.value?.click()
}

const backToChat = () => {
  window.location.href = '/'
}

const sourceLabel = (source) => {
  if (source === 'bundled') {
    return '内置'
  }
  if (source === 'uploaded') {
    return '本地'
  }
  return source || '未知'
}

const statusLabel = (status) => {
  if (status === 'PROCESSING') {
    return '处理中'
  }
  if (status === 'READY') {
    return '已完成'
  }
  if (status === 'FAILED') {
    return '失败'
  }
  return '未知'
}

const formatSize = (size) => {
  if (!size) {
    return '0 B'
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

const safeProgress = (value) => {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return 0
  }
  return Math.max(0, Math.min(100, Math.round(value)))
}

const resolveAxiosErrorMessage = (error, fallback) => {
  const responseMessage = error?.response?.data?.message
  const traceId = error?.response?.data?.traceId || error?.response?.headers?.['x-trace-id']
  if (responseMessage && traceId) {
    return `${responseMessage}（traceId: ${traceId}）`
  }
  if (responseMessage) {
    return responseMessage
  }
  return fallback
}

const loadFiles = async (preferredHash = '') => {
  isLoading.value = true
  try {
    const { data } = await axios.get('/api/aimed/knowledge/files')
    files.value = Array.isArray(data) ? data : []

    const availableHash = preferredHash && files.value.some((file) => file.hash === preferredHash)
      ? preferredHash
      : files.value[0]?.hash

    if (availableHash) {
      await selectFile(availableHash)
    } else {
      selectedHash.value = ''
      selectedDetail.value = null
      isEditing.value = false
    }
  } catch (error) {
    console.error('加载知识库列表失败:', error)
    ElMessage.error(resolveAxiosErrorMessage(error, '知识库列表加载失败，请稍后重试。'))
  } finally {
    isLoading.value = false
  }
}

const selectFile = async (hash) => {
  if (!hash) {
    return
  }

  try {
    const { data } = await axios.get(`/api/aimed/knowledge/files/${hash}`)
    selectedHash.value = hash
    selectedDetail.value = data
    editorContent.value = data?.extractedText || ''
    isEditing.value = false
  } catch (error) {
    console.error('加载知识文件详情失败:', error)
    ElMessage.error(resolveAxiosErrorMessage(error, '知识文件详情加载失败。'))
  }
}

const uploadKnowledge = async (event) => {
  const pickedFiles = Array.from(event.target.files || [])
  if (!pickedFiles.length) {
    return
  }

  const formData = new FormData()
  pickedFiles.forEach((file) => formData.append('files', file))
  isUploading.value = true

  try {
    const { data } = await axios.post('/api/aimed/knowledge/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })

    const acceptedItem = (data.items || []).find((item) => item.status === 'QUEUED')
    await loadFiles(acceptedItem?.hash)
    ElMessage.success(`文件已上传：后台处理中 ${data.accepted || 0} 个，跳过 ${data.skipped || 0} 个。`)
  } catch (error) {
    console.error('上传知识失败:', error)
    ElMessage.error(resolveAxiosErrorMessage(error, '知识文件上传失败，请检查格式或稍后重试。'))
  } finally {
    isUploading.value = false
    event.target.value = ''
  }
}

const startEdit = () => {
  editorContent.value = selectedDetail.value?.extractedText || ''
  isEditing.value = true
}

const cancelEdit = () => {
  editorContent.value = selectedDetail.value?.extractedText || ''
  isEditing.value = false
}

const saveEdit = async () => {
  if (!selectedDetail.value) {
    return
  }

  isSaving.value = true
  try {
    const { data } = await axios.put(`/api/aimed/knowledge/files/${selectedDetail.value.hash}`, {
      content: editorContent.value,
    })
    selectedHash.value = data.hash
    selectedDetail.value = data
    editorContent.value = data.extractedText || ''
    isEditing.value = false
    await loadFiles(data.hash)
    ElMessage.success('知识文件已保存，并完成向量重建。')
  } catch (error) {
    console.error('更新知识文件失败:', error)
    ElMessage.error(resolveAxiosErrorMessage(error, '知识文件保存失败。'))
  } finally {
    isSaving.value = false
  }
}

const deleteFile = async () => {
  if (!selectedDetail.value) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认删除知识文件「${selectedDetail.value.originalFilename}」吗？这会同时移除它的 RAG 切分数据。`,
      '删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
  } catch {
    return
  }

  isDeleting.value = true
  try {
    await axios.delete(`/api/aimed/knowledge/files/${selectedDetail.value.hash}`)
    const currentHash = selectedDetail.value.hash
    selectedHash.value = ''
    selectedDetail.value = null
    isEditing.value = false
    await loadFiles(files.value.find((file) => file.hash !== currentHash)?.hash)
    ElMessage.success('知识文件已删除。')
  } catch (error) {
    console.error('删除知识文件失败:', error)
    ElMessage.error(resolveAxiosErrorMessage(error, '删除知识文件失败。'))
  } finally {
    isDeleting.value = false
  }
}

const connectKnowledgeSocket = () => {
  if (knowledgeSocket && (knowledgeSocket.readyState === WebSocket.OPEN || knowledgeSocket.readyState === WebSocket.CONNECTING)) {
    return
  }

  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const host = window.location.hostname || 'localhost'
  const port = import.meta.env.VITE_KNOWLEDGE_WS_PORT || '8080'
  knowledgeSocket = new WebSocket(`${protocol}://${host}:${port}/ws/knowledge`)

  knowledgeSocket.onopen = () => {
    wsConnected.value = true
  }

  knowledgeSocket.onmessage = async (event) => {
    try {
      const payload = JSON.parse(event.data)
      applyProgressPayload(payload)

      if (payload.processingStatus === 'READY' || payload.processingStatus === 'FAILED') {
        const preferredHash = selectedHash.value || payload.hash
        await loadFiles(preferredHash)
      }

      if (payload.processingStatus === 'READY') {
        ElMessage.success(`${payload.originalFilename || '知识文件'} 已完成 RAG 切分。`)
      } else if (payload.processingStatus === 'FAILED') {
        ElMessage.error(payload.statusMessage || '知识文件处理失败。')
      }
    } catch (error) {
      console.error('处理知识库通知失败:', error)
    }
  }

  knowledgeSocket.onerror = () => {
    wsConnected.value = false
  }

  knowledgeSocket.onclose = () => {
    wsConnected.value = false
    if (reconnectTimer) {
      window.clearTimeout(reconnectTimer)
    }
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null
      connectKnowledgeSocket()
    }, 3000)
  }
}

const applyProgressPayload = (payload) => {
  if (!payload?.hash) {
    return
  }

  files.value = files.value.map((file) => {
    if (file.hash !== payload.hash) {
      return file
    }
    return {
      ...file,
      processingStatus: payload.processingStatus || file.processingStatus,
      statusMessage: payload.statusMessage || file.statusMessage,
      progressPercent: typeof payload.progressPercent === 'number' ? payload.progressPercent : file.progressPercent,
      currentBatch: typeof payload.currentBatch === 'number' ? payload.currentBatch : file.currentBatch,
      totalBatches: typeof payload.totalBatches === 'number' ? payload.totalBatches : file.totalBatches,
    }
  })

  if (selectedDetail.value?.hash === payload.hash) {
    selectedDetail.value = {
      ...selectedDetail.value,
      processingStatus: payload.processingStatus || selectedDetail.value.processingStatus,
      statusMessage: payload.statusMessage || selectedDetail.value.statusMessage,
      progressPercent: typeof payload.progressPercent === 'number' ? payload.progressPercent : selectedDetail.value.progressPercent,
      currentBatch: typeof payload.currentBatch === 'number' ? payload.currentBatch : selectedDetail.value.currentBatch,
      totalBatches: typeof payload.totalBatches === 'number' ? payload.totalBatches : selectedDetail.value.totalBatches,
    }
  }
}
</script>

<style scoped>
.knowledge-shell {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
  height: 100dvh;
  padding: 18px;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(111, 194, 190, 0.2), transparent 24%),
    radial-gradient(circle at bottom right, rgba(25, 83, 92, 0.16), transparent 28%),
    linear-gradient(135deg, #eef7f6 0%, #f7fbfb 58%, #edf2f7 100%);
}

.knowledge-sidebar,
.knowledge-main {
  min-width: 0;
  min-height: 0;
}

.knowledge-sidebar {
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
}

.sidebar-card,
.summary-card,
.detail-card,
.empty-detail {
  border: 1px solid rgba(60, 115, 122, 0.12);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 20px 48px rgba(22, 70, 79, 0.08);
  backdrop-filter: blur(14px);
}

.sidebar-card,
.summary-card,
.detail-card,
.empty-detail {
  padding: 18px;
}

.brand-lockup {
  display: flex;
  gap: 12px;
  align-items: center;
}

.brand-logo {
  width: 60px;
  height: 60px;
  border-radius: 18px;
  background: linear-gradient(180deg, #ffffff 0%, #eef8f7 100%);
  padding: 8px;
  box-shadow: inset 0 0 0 1px rgba(79, 160, 160, 0.12);
}

.brand-eyebrow,
.summary-kicker {
  margin: 0 0 6px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #5c8d8f;
}

.brand-card h1,
.summary-card h2,
.empty-detail h3 {
  margin: 0;
  color: #14373e;
}

.brand-subtitle,
.sidebar-note,
.empty-detail p {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #55757b;
}

.back-button,
.primary-button,
.secondary-button,
.danger-button {
  border-radius: 14px;
  border: none;
  min-height: 42px;
  font-weight: 700;
  cursor: pointer;
}

.back-button,
.secondary-button {
  color: #216a72;
  background: rgba(227, 244, 242, 0.86);
  box-shadow: inset 0 0 0 1px rgba(46, 136, 140, 0.14);
}

.back-button {
  width: 100%;
  margin-top: 16px;
}

.primary-button {
  color: #fff;
  background: linear-gradient(135deg, #2e888c 0%, #57ada3 100%);
  box-shadow: 0 16px 28px rgba(56, 139, 140, 0.22);
}

.danger-button {
  color: #b23a48;
  background: rgba(255, 236, 239, 0.92);
  box-shadow: inset 0 0 0 1px rgba(178, 58, 72, 0.16);
}

.primary-button:disabled,
.secondary-button:disabled,
.danger-button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.action-grid {
  display: grid;
  gap: 10px;
}

.sidebar-heading,
.section-heading,
.detail-toolbar,
.detail-badges,
.detail-actions,
.summary-card,
.summary-stats {
  display: flex;
  align-items: center;
}

.sidebar-heading,
.section-heading {
  justify-content: space-between;
  margin-bottom: 12px;
  color: #183f45;
}

.sidebar-heading strong,
.section-heading small {
  font-size: 12px;
  color: #5a7c81;
}

.search-input,
.content-editor {
  width: 100%;
  border: 1px solid rgba(91, 145, 149, 0.18);
  border-radius: 14px;
  background: rgba(244, 250, 249, 0.88);
  color: #173b41;
}

.search-input {
  min-height: 44px;
  padding: 0 14px;
  margin-bottom: 12px;
}

.hidden-input {
  display: none;
}

.knowledge-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 44dvh;
  overflow-y: auto;
}

.knowledge-item {
  width: 100%;
  padding: 12px;
  border: 1px solid rgba(79, 160, 160, 0.14);
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(249, 252, 252, 0.96) 0%, rgba(238, 247, 246, 0.88) 100%);
  text-align: left;
  color: #17393f;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.knowledge-item:hover,
.knowledge-item.active {
  border-color: rgba(46, 136, 140, 0.32);
  transform: translateY(-1px);
  box-shadow: 0 18px 28px rgba(39, 105, 111, 0.08);
}

.knowledge-item-top,
.knowledge-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.knowledge-name {
  font-size: 13px;
  font-weight: 700;
  color: #174047;
  word-break: break-word;
}

.knowledge-meta {
  margin-top: 8px;
  font-size: 11px;
  color: #618187;
}

.progress-inline {
  margin-top: 10px;
}

.progress-inline-text {
  display: inline-block;
  margin-top: 6px;
  font-size: 11px;
  color: #4f767d;
}

.progress-card {
  margin-bottom: 16px;
  padding: 14px 16px;
  border: 1px solid rgba(46, 136, 140, 0.16);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(239, 248, 247, 0.92) 0%, rgba(252, 255, 255, 0.98) 100%);
}

.progress-card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.progress-label {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  color: #5a7c81;
}

.progress-value {
  font-size: 22px;
  color: #1c5b63;
}

.progress-note {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #56767c;
}

.progress-track {
  position: relative;
  width: 100%;
  height: 10px;
  margin-top: 12px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(90, 140, 146, 0.14);
}

.progress-track-compact {
  height: 6px;
  margin-top: 0;
}

.progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2e888c 0%, #57ada3 100%);
  box-shadow: 0 8px 16px rgba(46, 136, 140, 0.22);
  transition: width 0.3s ease;
}

.processing-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 58px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.processing-tag-processing {
  color: #9a6400;
  background: rgba(255, 190, 61, 0.18);
}

.processing-tag-ready {
  color: #0f766e;
  background: rgba(15, 118, 110, 0.14);
}

.processing-tag-failed {
  color: #b42318;
  background: rgba(180, 35, 24, 0.12);
}

.processing-tag-unknown {
  color: #51646b;
  background: rgba(81, 100, 107, 0.12);
}

.source-tag,
.detail-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 700;
}

.source-tag-uploaded,
.detail-badge {
  color: #1a5c66;
  background: rgba(108, 189, 180, 0.14);
}

.source-tag-bundled {
  color: #7a6132;
  background: rgba(255, 233, 188, 0.46);
}

.detail-badge-status {
  border: none;
}

.empty-list,
.empty-chunks {
  padding: 18px 14px;
  border-radius: 16px;
  background: rgba(244, 250, 249, 0.82);
  color: #628188;
  text-align: center;
}

.knowledge-main {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
  overflow: hidden;
}

.summary-card {
  justify-content: space-between;
  gap: 16px;
  flex-shrink: 0;
}

.summary-stats {
  gap: 10px;
}

.summary-stat,
.meta-card {
  padding: 10px 12px;
  border-radius: 16px;
  background: rgba(238, 247, 246, 0.92);
  box-shadow: inset 0 0 0 1px rgba(90, 152, 156, 0.12);
}

.summary-stat span,
.meta-card span {
  display: block;
  margin-bottom: 4px;
  font-size: 11px;
  color: #64868b;
}

.summary-stat strong,
.meta-card strong {
  font-size: 15px;
  color: #17424a;
}

.detail-card {
  min-height: 0;
  overflow-y: auto;
}

.detail-toolbar {
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.detail-badges,
.detail-actions {
  gap: 8px;
  flex-wrap: wrap;
}

.slim-button {
  min-height: 38px;
  padding: 0 14px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.content-card {
  margin-top: 16px;
  padding: 16px;
  border-radius: 20px;
  background: rgba(247, 251, 251, 0.9);
  box-shadow: inset 0 0 0 1px rgba(90, 152, 156, 0.1);
}

.status-note {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(240, 247, 247, 0.92);
  color: #43666b;
  font-size: 13px;
  line-height: 1.6;
}

.content-editor,
.content-preview,
.chunk-content {
  min-height: 220px;
  margin: 0;
  padding: 14px;
  overflow: auto;
  font-family: "SFMono-Regular", "JetBrains Mono", "Fira Code", monospace;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.content-editor {
  resize: vertical;
}

.content-preview,
.chunk-content {
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.88);
  color: #173b41;
}

.chunk-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.chunk-item {
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.86);
  box-shadow: inset 0 0 0 1px rgba(79, 160, 160, 0.12);
}

.chunk-item summary {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  cursor: pointer;
  color: #184047;
  font-weight: 700;
}

.chunk-preview {
  margin: 0;
  padding: 0 16px 12px;
  color: #5c7c81;
  font-size: 13px;
  line-height: 1.6;
}

.chunk-content {
  margin: 0 16px 16px;
  min-height: 0;
}

.empty-detail {
  display: grid;
  place-items: center;
  min-height: 100%;
  text-align: center;
}

@media (max-width: 1180px) {
  .knowledge-shell {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 100dvh;
  }

  .knowledge-sidebar,
  .knowledge-main,
  .detail-card {
    overflow: visible;
  }

  .summary-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .knowledge-shell {
    padding: 12px;
    gap: 12px;
  }

  .summary-stats,
  .detail-grid {
    width: 100%;
    grid-template-columns: 1fr;
  }
}
</style>
