<template>
  <section class="metadata-ops-card">
    <div class="metadata-ops-header">
      <div>
        <span class="metadata-ops-kicker">检索字段补齐</span>
        <h4>批量补齐检索字段</h4>
        <p>用于把历史文档缺失的标题、医生、科室、关键词补齐，提升检索命中；不会覆盖管理员已维护内容。</p>
      </div>
      <strong>{{ scopeLabel }}</strong>
    </div>

    <label v-if="allowHashInput" class="metadata-hash-field">
      <span>指定文件 hash（可选）</span>
      <textarea
        :value="hashesText"
        rows="4"
        placeholder="每行一个 hash；留空则处理全部知识文件"
        @input="$emit('update:hashesText', $event.target.value)"
      />
      <small>支持换行、逗号或空格分隔。建议先预览，再执行回填。</small>
    </label>
    <p v-else class="metadata-ops-note">
      复用当前筛选结果；如果已经勾选文件，则只对勾选项做预览和执行。
    </p>

    <div class="metadata-action-row">
      <button
        class="metadata-button secondary"
        type="button"
        :disabled="!canRun || loading"
        @click="$emit('preview')"
      >
        <span>{{ loading ? '处理中...' : '预览回填' }}</span>
        <small>查看将补哪些字段</small>
      </button>
      <button
        class="metadata-button primary"
        type="button"
        :disabled="!canRun || loading"
        @click="$emit('apply')"
      >
        <span>{{ loading ? '处理中...' : '执行回填' }}</span>
        <small>同步文件与切片检索字段</small>
      </button>
    </div>

    <div v-if="result" class="metadata-result-block">
      <div class="metadata-result-summary">
        <span>{{ result.preview ? '预览结果' : '执行结果' }}</span>
        <span>目标 {{ result.targetCount || 0 }}</span>
        <span>命中 {{ result.matchedCount || 0 }}</span>
        <span>更新 {{ result.updatedCount || 0 }}</span>
        <span>跳过 {{ result.skippedCount || 0 }}</span>
      </div>
      <div v-if="previewItems.length" class="metadata-preview-list">
        <article
          v-for="item in previewItems"
          :key="item.hash"
          class="metadata-preview-item"
        >
          <div class="metadata-preview-head">
            <strong>{{ item.originalFilename }}</strong>
            <span>{{ item.updated ? '已更新' : '待确认' }}</span>
          </div>
          <p class="metadata-preview-hash">{{ item.hash }}</p>
          <div class="metadata-preview-tags">
            <span
              v-for="change in item.changes"
              :key="`${item.hash}-${change.field}`"
              class="metadata-preview-tag"
            >
              {{ fieldLabel(change.field) }}：{{ change.before || '空' }} → {{ change.after || '空' }}
            </span>
          </div>
        </article>
      </div>
      <div v-if="skippedItems.length" class="metadata-skipped-list">
        <span>跳过原因</span>
        <p v-for="item in skippedItems" :key="`skip-${item.hash}`">
          {{ item.originalFilename }}：{{ item.skippedReasons?.join('；') || '无可回填字段' }}
        </p>
      </div>
      <p v-if="!previewItems.length && !skippedItems.length" class="metadata-ops-note metadata-empty-note">
        当前范围内没有需要补齐的检索字段。
      </p>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  loading: {
    type: Boolean,
    default: false,
  },
  result: {
    type: Object,
    default: null,
  },
  scopeCount: {
    type: Number,
    default: 0,
  },
  selectedCount: {
    type: Number,
    default: 0,
  },
  scopeMode: {
    type: String,
    default: 'visible',
  },
  allowHashInput: {
    type: Boolean,
    default: false,
  },
  hashesText: {
    type: String,
    default: '',
  },
})

defineEmits(['preview', 'apply', 'update:hashesText'])

const hashCount = computed(() => props.hashesText
  .split(/[\s,，]+/)
  .map((item) => item.trim())
  .filter(Boolean)
  .length)

const canRun = computed(() => props.scopeMode === 'global' || props.selectedCount > 0 || props.scopeCount > 0)
const scopeLabel = computed(() => {
  if (props.scopeMode === 'global') {
    return hashCount.value > 0 ? `指定 ${hashCount.value}` : '全部文件'
  }
  return props.selectedCount > 0 ? `已选 ${props.selectedCount}` : `可见 ${props.scopeCount}`
})
const previewItems = computed(() => Array.isArray(props.result?.items)
  ? props.result.items.filter((item) => Array.isArray(item.changes) && item.changes.length > 0).slice(0, 8)
  : [])
const skippedItems = computed(() => Array.isArray(props.result?.items)
  ? props.result.items
    .filter((item) => (!Array.isArray(item.changes) || item.changes.length === 0) && Array.isArray(item.skippedReasons) && item.skippedReasons.length > 0)
    .slice(0, 4)
  : [])

const fieldLabel = (field) => {
  const labels = {
    title: '标题',
    department: '科室',
    doctorName: '医生',
    keywords: '关键词',
  }
  return labels[field] || field
}
</script>

<style scoped>
.metadata-ops-card {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid rgba(42, 112, 114, 0.14);
  border-radius: 22px;
  background: rgba(250, 253, 253, 0.96);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.metadata-ops-header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
}

.metadata-ops-kicker,
.metadata-hash-field span {
  color: #6e9298;
  font-size: 12px;
  font-weight: 800;
}

.metadata-ops-header h4 {
  margin: 6px 0 0;
  color: #1d4c54;
  font-size: 19px;
  line-height: 1.25;
}

.metadata-ops-header p,
.metadata-ops-note,
.metadata-hash-field small {
  margin: 8px 0 0;
  color: #6b8d93;
  font-size: 12px;
  line-height: 1.6;
}

.metadata-ops-header strong {
  flex: 0 0 auto;
  min-height: 30px;
  padding: 0 11px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  background: rgba(34, 114, 124, 0.09);
  color: #205d66;
  font-size: 12px;
}

.metadata-hash-field {
  display: grid;
  gap: 7px;
}

.metadata-hash-field textarea {
  width: 100%;
  min-height: 96px;
  padding: 12px 13px;
  border: 1px solid rgba(58, 122, 126, 0.18);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.94);
  color: #183f46;
  font: inherit;
  resize: vertical;
  outline: none;
}

.metadata-action-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.metadata-button {
  min-height: 58px;
  padding: 10px 13px;
  border: 1px solid rgba(96, 170, 171, 0.24);
  border-radius: 17px;
  cursor: pointer;
  font: inherit;
  text-align: left;
  display: grid;
  gap: 4px;
  transition: transform 0.18s ease, box-shadow 0.18s ease, opacity 0.18s ease;
}

.metadata-button span {
  font-weight: 800;
}

.metadata-button small {
  color: inherit;
  opacity: 0.72;
  font-size: 11px;
  line-height: 1.35;
}

.metadata-button.secondary {
  background: rgba(243, 250, 250, 0.94);
  color: #265e67;
}

.metadata-button.primary {
  background: linear-gradient(135deg, #4aa7a5, #66c0a8);
  color: #fff;
  box-shadow: 0 14px 24px rgba(76, 160, 157, 0.2);
}

.metadata-button:hover:not(:disabled) {
  transform: translateY(-1px);
}

.metadata-button:disabled {
  cursor: not-allowed;
  opacity: 0.54;
}

.metadata-result-block {
  display: grid;
  gap: 10px;
}

.metadata-result-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: #527278;
}

.metadata-result-summary span {
  min-height: 28px;
  display: inline-flex;
  align-items: center;
  padding: 0 9px;
  border-radius: 999px;
  background: rgba(232, 244, 244, 0.86);
}

.metadata-preview-list {
  display: grid;
  gap: 10px;
  max-height: 420px;
  overflow: auto;
  padding-right: 2px;
}

.metadata-preview-item {
  padding: 12px;
  border: 1px solid rgba(52, 117, 118, 0.12);
  border-radius: 16px;
  background: rgba(247, 251, 251, 0.84);
}

.metadata-preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #476970;
}

.metadata-preview-head strong {
  color: #1b4e56;
  font-size: 13px;
  line-height: 1.35;
}

.metadata-preview-head span {
  flex: 0 0 auto;
  color: #2a7a75;
  font-size: 11px;
  font-weight: 800;
}

.metadata-preview-hash {
  margin: -2px 0 8px;
  color: #7f9da3;
  font-size: 11px;
  word-break: break-all;
}

.metadata-preview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.metadata-preview-tag {
  padding: 5px 9px;
  border-radius: 999px;
  background: rgba(34, 114, 124, 0.08);
  color: #205d66;
  font-size: 11px;
  line-height: 1.3;
}

.metadata-empty-note {
  margin: 0;
}

.metadata-skipped-list {
  display: grid;
  gap: 6px;
  padding: 12px;
  border-radius: 16px;
  background: rgba(247, 250, 250, 0.86);
  color: #69878d;
  font-size: 12px;
}

.metadata-skipped-list span {
  color: #315f67;
  font-weight: 800;
}

.metadata-skipped-list p {
  margin: 0;
  line-height: 1.5;
}

@media (max-width: 720px) {
  .metadata-ops-header,
  .metadata-action-row {
    grid-template-columns: 1fr;
  }

  .metadata-ops-header {
    display: grid;
  }
}
</style>
