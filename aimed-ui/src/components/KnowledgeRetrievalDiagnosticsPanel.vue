<template>
  <section class="diagnostic-card">
    <div class="diagnostic-header">
      <div>
        <span class="diagnostic-kicker">检索诊断</span>
        <h4>拆解关键词、向量与最终排序</h4>
        <p>用于管理员排查知识召回，不影响用户问答主链路。</p>
      </div>
      <span class="diagnostic-badge">管理员可见</span>
    </div>

    <div class="diagnostic-toolbar">
      <label class="diagnostic-field diagnostic-field-query">
        <span>诊断内容</span>
        <input
          :value="query"
          type="text"
          autocomplete="off"
          spellcheck="false"
          placeholder="输入医生、科室、指南或院内服务问题"
          @input="$emit('update:query', $event.target.value)"
        />
      </label>
      <label class="diagnostic-field diagnostic-field-profile">
        <span>检索档位</span>
        <select :value="profile" @change="$emit('update:profile', $event.target.value)">
          <option value="ONLINE">在线：8+8 取 6</option>
          <option value="LOCAL">本地：8+8 取 4</option>
        </select>
      </label>
      <button class="primary-button diagnostic-run-button" type="button" :disabled="loading" @click="$emit('run')">
        {{ loading ? '诊断中...' : '开始诊断' }}
      </button>
    </div>

    <div v-if="result" class="diagnostic-body">
      <div class="diagnostic-summary-grid">
        <div class="summary-stat">
          <span>查询类型</span>
          <strong>{{ result.queryType || 'GENERAL' }}</strong>
        </div>
        <div class="summary-stat">
          <span>关键词命中</span>
          <strong>{{ result.keywordHits?.length || 0 }}</strong>
        </div>
        <div class="summary-stat">
          <span>向量命中</span>
          <strong>{{ result.vectorHits?.length || 0 }}</strong>
        </div>
        <div class="summary-stat">
          <span>最终结果</span>
          <strong>{{ result.finalHits?.length || 0 }}</strong>
        </div>
      </div>

      <div class="diagnostic-meta-grid">
        <div class="diagnostic-meta-item">
          <span>当前档位</span>
          <strong>{{ profileLabel(result.profile || profile) }}</strong>
        </div>
        <div class="diagnostic-meta-item">
          <span>规范化内容</span>
          <strong>{{ result.normalizedQuery || '—' }}</strong>
        </div>
        <div class="diagnostic-meta-item">
          <span>布尔查询</span>
          <strong>{{ result.booleanQuery || '—' }}</strong>
        </div>
        <div class="diagnostic-meta-item">
          <span>耗时</span>
          <strong>{{ result.durationMs || 0 }} ms</strong>
        </div>
      </div>

      <div v-if="result.keywordTokens?.length" class="diagnostic-token-row">
        <span
          v-for="token in result.keywordTokens"
          :key="token"
          class="diagnostic-token"
        >
          {{ token }}
        </span>
      </div>

      <div v-if="result.scoringRules?.length" class="diagnostic-rules-card">
        <div class="diagnostic-rules-head">
          <h3>排序计分规则</h3>
          <span>后续可配置化的权重基础</span>
        </div>
        <div class="diagnostic-rule-list">
          <article v-for="rule in result.scoringRules" :key="rule.key" class="diagnostic-rule-item">
            <strong>{{ rule.label }}</strong>
            <span>权重 {{ formatScore(rule.weight) }}</span>
            <p>{{ rule.description }}</p>
          </article>
        </div>
      </div>

      <div class="diagnostic-columns">
        <article class="diagnostic-list-card">
          <h3>关键词命中</h3>
          <div v-if="result.keywordHits?.length" class="diagnostic-hit-list">
            <section v-for="item in result.keywordHits" :key="`keyword-${item.segmentId}`" class="diagnostic-hit-item">
              <header>
                <strong>{{ item.title || item.documentName || item.fileHash }}</strong>
                <span>{{ formatScore(item.keywordScore) }}</span>
              </header>
              <p>{{ hitMeta(item) }}</p>
            </section>
          </div>
          <p v-else class="diagnostic-empty">无命中</p>
        </article>

        <article class="diagnostic-list-card">
          <h3>向量命中</h3>
          <div v-if="result.vectorHits?.length" class="diagnostic-hit-list">
            <section v-for="item in result.vectorHits" :key="`vector-${item.segmentId}`" class="diagnostic-hit-item">
              <header>
                <strong>{{ item.title || item.documentName || item.fileHash }}</strong>
                <span>{{ formatScore(item.vectorScore) }}</span>
              </header>
              <p>{{ hitMeta(item) }}</p>
            </section>
          </div>
          <p v-else class="diagnostic-empty">无命中</p>
        </article>

        <article class="diagnostic-list-card diagnostic-list-card-final">
          <h3>最终排序</h3>
          <div v-if="result.finalHits?.length" class="diagnostic-hit-list">
            <section v-for="item in result.finalHits" :key="`final-${item.segmentId}`" class="diagnostic-hit-item">
              <header>
                <strong>{{ item.title || item.documentName || item.fileHash }}</strong>
                <span>{{ formatScore(item.combinedScore) }}</span>
              </header>
              <p>{{ hitMeta(item) }}</p>
              <small>{{ item.preview || '—' }}</small>
              <div v-if="item.scoreBreakdown?.length" class="diagnostic-score-breakdown">
                <span
                  v-for="part in item.scoreBreakdown"
                  :key="`${item.segmentId}-${part.key}`"
                >
                  {{ part.label }} {{ formatScore(part.contribution) }}
                </span>
              </div>
            </section>
          </div>
          <p v-else class="diagnostic-empty">当前内容没有召回结果</p>
        </article>
      </div>
    </div>
  </section>
</template>

<script setup>
defineProps({
  query: {
    type: String,
    default: '',
  },
  profile: {
    type: String,
    default: 'ONLINE',
  },
  loading: {
    type: Boolean,
    default: false,
  },
  result: {
    type: Object,
    default: null,
  },
})

defineEmits(['update:query', 'update:profile', 'run'])

const formatScore = (value) => Number(value || 0).toFixed(2)

const profileLabel = (value) => String(value).toUpperCase() === 'LOCAL'
  ? '本地：关键词 8 + 向量 8，最终取 4'
  : '在线：关键词 8 + 向量 8，最终取 6'

const hitMeta = (item) => [item.retrievalType, item.doctorName, item.department, item.version]
  .filter(Boolean)
  .join(' · ')
</script>

<style scoped>
.diagnostic-card {
  display: grid;
  gap: 18px;
  padding: 18px;
  min-width: 0;
  overflow: hidden;
  border: 1px solid rgba(42, 112, 114, 0.14);
  border-radius: 22px;
  background: rgba(250, 253, 253, 0.96);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.diagnostic-header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
}

.diagnostic-kicker,
.diagnostic-field span {
  color: #6e9298;
  font-size: 12px;
  font-weight: 800;
}

.diagnostic-header h4 {
  margin: 6px 0 0;
  color: #1d4c54;
  font-size: 19px;
  line-height: 1.25;
}

.diagnostic-header p {
  margin: 8px 0 0;
  color: #6b8d93;
  font-size: 12px;
  line-height: 1.6;
}

.diagnostic-badge {
  flex: 0 0 auto;
  min-height: 30px;
  padding: 0 11px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  background: rgba(34, 114, 124, 0.09);
  color: #205d66;
  font-size: 12px;
  font-weight: 800;
}

.diagnostic-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1.8fr) 180px 140px;
  gap: 12px;
  align-items: end;
}

.diagnostic-field {
  display: grid;
  gap: 6px;
}

.diagnostic-field input,
.diagnostic-field select {
  min-width: 0;
  min-height: 42px;
  padding: 0 14px;
  border: 1px solid rgba(58, 122, 126, 0.18);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.94);
  color: #183f46;
  font: inherit;
}

.diagnostic-run-button {
  min-height: 42px;
  border: 1px solid rgba(96, 170, 171, 0.24);
  border-radius: 14px;
  background: linear-gradient(135deg, #4aa7a5, #66c0a8);
  color: #fff;
  box-shadow: 0 14px 24px rgba(76, 160, 157, 0.2);
  cursor: pointer;
  font: inherit;
  font-weight: 800;
}

.diagnostic-run-button:disabled {
  cursor: not-allowed;
  opacity: 0.54;
}

.diagnostic-body {
  display: grid;
  gap: 16px;
}

.diagnostic-summary-grid,
.diagnostic-meta-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.diagnostic-meta-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.diagnostic-meta-item {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid rgba(40, 109, 110, 0.12);
  border-radius: 18px;
  background: rgba(246, 250, 250, 0.92);
}

.summary-stat {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid rgba(40, 109, 110, 0.12);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(239, 249, 249, 0.96), rgba(249, 252, 252, 0.94));
}

.summary-stat span {
  color: #65868b;
  font-size: 11px;
}

.summary-stat strong {
  color: #1c4f57;
  font-size: 20px;
  line-height: 1.2;
}

.diagnostic-meta-item span {
  font-size: 11px;
  color: #65868b;
}

.diagnostic-meta-item strong {
  color: #1c4f57;
  font-size: 13px;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.diagnostic-token-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.diagnostic-token {
  max-width: 100%;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(31, 104, 111, 0.08);
  color: #1f5f68;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.diagnostic-rules-card {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgba(42, 112, 114, 0.12);
  border-radius: 20px;
  background: rgba(248, 252, 252, 0.96);
}

.diagnostic-rules-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.diagnostic-rules-head h3 {
  margin: 0;
  color: #18444b;
  font-size: 15px;
}

.diagnostic-rules-head span {
  color: #6e9298;
  font-size: 12px;
}

.diagnostic-rule-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.diagnostic-rule-item {
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 12px;
  border-radius: 16px;
  background: rgba(239, 248, 248, 0.9);
}

.diagnostic-rule-item strong {
  color: #1d4c54;
  font-size: 13px;
}

.diagnostic-rule-item span,
.diagnostic-rule-item p {
  margin: 0;
  color: #67878d;
  font-size: 12px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.diagnostic-columns {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.diagnostic-list-card {
  display: grid;
  gap: 12px;
  min-width: 0;
  padding: 16px;
  border: 1px solid rgba(42, 112, 114, 0.12);
  border-radius: 20px;
  background: rgba(250, 252, 252, 0.96);
}

.diagnostic-list-card h3 {
  margin: 0;
  font-size: 15px;
  color: #18444b;
}

.diagnostic-hit-list {
  display: grid;
  gap: 10px;
  max-height: 420px;
  overflow: auto;
  padding-right: 2px;
}

.diagnostic-hit-item {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 12px;
  border-radius: 16px;
  background: rgba(240, 247, 247, 0.9);
}

.diagnostic-hit-item header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: start;
  min-width: 0;
}

.diagnostic-hit-item strong {
  color: #143d44;
  font-size: 13px;
  line-height: 1.4;
  min-width: 0;
  overflow-wrap: anywhere;
}

.diagnostic-hit-item p,
.diagnostic-hit-item small,
.diagnostic-empty {
  margin: 0;
  color: #638186;
  font-size: 12px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.diagnostic-score-breakdown {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.diagnostic-score-breakdown span {
  max-width: 100%;
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: rgba(31, 104, 111, 0.08);
  color: #1f5f68;
  font-size: 11px;
  font-weight: 700;
  overflow-wrap: anywhere;
}

@media (max-width: 1080px) {
  .diagnostic-header {
    display: grid;
  }

  .diagnostic-toolbar,
  .diagnostic-summary-grid,
  .diagnostic-meta-grid,
  .diagnostic-columns,
  .diagnostic-rule-list {
    grid-template-columns: 1fr;
  }
}
</style>
