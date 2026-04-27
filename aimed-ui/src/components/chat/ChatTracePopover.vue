<template>
  <div class="message-trace-inline">
    <el-popover
      trigger="click"
      placement="top"
      :width="360"
    >
      <template #reference>
        <button class="message-trace-chip" type="button">
          链路 {{ formatTraceDuration(message.serverDurationMs) }}
        </button>
      </template>
      <div class="message-trace-popover">
        <div class="message-trace-head">
          <strong>回答链路</strong>
          <span>{{ traceModeLabel(message.toolMode) }}</span>
        </div>
        <small class="message-trace-meta">
          <span v-if="message.provider">{{ message.provider }}</span>
          <span v-if="message.intentType">意图 {{ message.intentType }}</span>
          <span v-if="message.routeTarget">路由 {{ message.routeTarget }}</span>
          <span>{{ message.ragApplied ? 'RAG 已执行' : 'RAG 已跳过' }}</span>
          <span v-if="message.firstTokenLatencyMs > 0">首字 {{ formatTraceDuration(message.firstTokenLatencyMs) }}</span>
          <span v-if="message.traceId">traceId {{ shortenInlineTraceId(message.traceId) }}</span>
        </small>
        <ul class="message-trace-stage-list">
          <li
            v-for="stage in message.traceStages"
            :key="stage.key"
            :class="stageClass(stage.status)"
          >
            <div class="message-trace-stage-head">
              <strong>{{ stage.label }}</strong>
              <div class="message-trace-stage-side">
                <span class="message-trace-stage-duration">{{ formatTraceDuration(stage.durationMs) }}</span>
                <span v-if="showStatus(stage.status)" class="message-trace-stage-status">
                  {{ stage.status }}
                </span>
              </div>
            </div>
            <p v-if="stage.detail">{{ stage.detail }}</p>
          </li>
        </ul>
      </div>
    </el-popover>
  </div>
</template>

<script setup>
const props = defineProps({
  message: {
    type: Object,
    required: true,
  },
})

const formatTraceDuration = (value) => {
  const duration = Number(value || 0)
  if (!Number.isFinite(duration) || duration <= 0) {
    return '0 ms'
  }
  if (duration < 1000) {
    return `${Math.round(duration)} ms`
  }
  return `${(duration / 1000).toFixed(duration >= 10_000 ? 0 : 1)} s`
}

const traceModeLabel = (toolMode) => {
  if (toolMode === 'MCP') {
    return 'MCP 工具链'
  }
  if (toolMode === 'APPOINTMENT') {
    return '挂号工具链'
  }
  if (toolMode === 'FAST') {
    return '在线快答链'
  }
  if (toolMode === 'DEEP') {
    return '在线深答链'
  }
  return '标准链路'
}

const shortenInlineTraceId = (traceId) => {
  if (!traceId || traceId.length <= 18) {
    return traceId
  }
  return `${traceId.slice(0, 10)}...${traceId.slice(-6)}`
}

const showStatus = (status) => ['SKIPPED', 'DEGRADED', 'ERROR'].includes(String(status || '').toUpperCase())

const stageClass = (status) => {
  const normalized = String(status || 'DONE').toUpperCase()
  return {
    skipped: normalized === 'SKIPPED',
    degraded: normalized === 'DEGRADED',
    error: normalized === 'ERROR',
  }
}
</script>

<style scoped>
.message-trace-inline {
  margin-top: 10px;
}

.message-trace-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(124, 158, 201, 0.22);
  background: rgba(241, 246, 255, 0.94);
  color: #466985;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.message-trace-chip:hover {
  transform: translateY(-1px);
  border-color: rgba(124, 158, 201, 0.34);
  background: rgba(232, 241, 255, 0.98);
}

.message-trace-popover {
  display: grid;
  gap: 10px;
}

.message-trace-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.message-trace-head strong {
  color: #1d444b;
}

.message-trace-head span {
  color: #58727e;
  font-size: 12px;
}

.message-trace-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: #678189;
  line-height: 1.5;
}

.message-trace-meta span {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(233, 245, 246, 0.86);
  color: #4c7179;
  white-space: nowrap;
}

.message-trace-stage-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.message-trace-stage-list li {
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(246, 250, 253, 0.98);
}

.message-trace-stage-list li.skipped {
  background: rgba(245, 246, 247, 0.98);
}

.message-trace-stage-list li.degraded {
  background: rgba(255, 247, 237, 0.98);
}

.message-trace-stage-list li.error {
  background: rgba(254, 242, 242, 0.98);
}

.message-trace-stage-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #275762;
}

.message-trace-stage-side {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.message-trace-stage-duration {
  color: #58727e;
  font-size: 12px;
  white-space: nowrap;
}

.message-trace-stage-status {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(233, 245, 246, 0.86);
  color: #4c7179;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.degraded .message-trace-stage-status {
  background: rgba(255, 237, 213, 0.96);
  color: #9a3412;
}

.error .message-trace-stage-status {
  background: rgba(254, 226, 226, 0.98);
  color: #b91c1c;
}

.skipped .message-trace-stage-status {
  background: rgba(229, 231, 235, 0.96);
  color: #4b5563;
}

.message-trace-stage-list p {
  margin: 6px 0 0;
  color: #678089;
  font-size: 12px;
  line-height: 1.5;
}
</style>
