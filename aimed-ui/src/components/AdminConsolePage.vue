<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <section class="admin-card admin-brand-card">
        <p class="admin-eyebrow">管理台</p>
        <h1>树兰管理后台</h1>
        <p class="admin-subtitle">统一管理用户、角色与审计日志。</p>
        <div class="admin-nav-actions">
          <button class="admin-link-button" type="button" @click="goToChat">返回智能问答</button>
          <button class="admin-link-button secondary" type="button" @click="goToKnowledge">知识库页</button>
        </div>
      </section>

      <section class="admin-card">
        <div class="admin-side-heading">
          <span>导航</span>
          <small>高频入口</small>
        </div>
        <div class="admin-tab-list">
          <button
            v-for="tab in tabs"
            :key="tab.value"
            :class="['admin-tab-button', { active: activeTab === tab.value }]"
            type="button"
            @click="activeTab = tab.value"
          >
            <strong>{{ tab.label }}</strong>
            <span>{{ tab.description }}</span>
          </button>
        </div>
      </section>

      <section class="admin-card">
        <div class="admin-side-heading">
          <span>概览</span>
          <small>当前工作面</small>
        </div>
        <div class="admin-summary-grid">
          <div class="admin-summary-item">
            <span>用户总数</span>
            <strong>{{ userPagination.total }}</strong>
          </div>
          <div class="admin-summary-item">
            <span>医生</span>
            <strong>{{ doctorCount }}</strong>
          </div>
          <div class="admin-summary-item">
            <span>管理员</span>
            <strong>{{ adminCount }}</strong>
          </div>
          <div class="admin-summary-item">
            <span>日志总量</span>
            <strong>{{ auditPagination.total }}</strong>
          </div>
          <div class="admin-summary-item">
            <span>启用中的 MCP</span>
            <strong>{{ mcpEnabledCount }}</strong>
          </div>
          <div class="admin-summary-item admin-summary-item-soft">
            <span>当前管理员</span>
            <strong>{{ adminDisplayName }}</strong>
          </div>
        </div>
      </section>
    </aside>

    <main class="admin-main">
      <section class="admin-hero">
        <div>
          <p class="admin-eyebrow">工作面板</p>
          <h2>{{ activeTabTitle }}</h2>
          <p class="admin-hero-copy">{{ activeTabDescription }}</p>
        </div>
        <div class="admin-hero-actions">
          <AdminEntryActionButton label="智能问答" @click="goToChat" />
          <LogoutActionButton @click="handleLogout" />
        </div>
      </section>

      <section v-if="activeTab === 'users'" class="admin-card admin-panel">
        <div class="admin-toolbar">
          <div class="admin-toolbar-copy">
            <h3>用户管理</h3>
            <p>统一处理账号搜索、角色调整、启用禁用与医生账号创建。</p>
          </div>
          <div class="admin-toolbar-actions">
            <button class="admin-primary-button" type="button" @click="doctorDialogVisible = true">创建医生账号</button>
            <button class="admin-secondary-button" type="button" @click="loadUsers()">刷新列表</button>
          </div>
        </div>

        <div class="admin-filter-grid">
          <label class="admin-field">
            <span>搜索账号</span>
            <input v-model.trim="userFilters.keyword" type="text" placeholder="邮箱、用户名、昵称" />
          </label>
          <label class="admin-field">
            <span>角色</span>
            <select v-model="userFilters.role">
              <option value="">全部</option>
              <option value="PATIENT">患者</option>
              <option value="DOCTOR">医生</option>
              <option value="ADMIN">管理员</option>
            </select>
          </label>
          <label class="admin-field">
            <span>状态</span>
            <select v-model="userFilters.status">
              <option value="">全部</option>
              <option value="ACTIVE">启用</option>
              <option value="DISABLED">禁用</option>
            </select>
          </label>
          <div class="admin-filter-actions">
            <button class="admin-primary-button" type="button" @click="applyUserFilters">筛选</button>
            <button class="admin-secondary-button" type="button" @click="resetUserFilters">重置</button>
          </div>
        </div>

        <el-table :data="users" v-loading="usersLoading" class="admin-table" empty-text="暂无用户数据">
          <el-table-column prop="email" label="账号邮箱" min-width="220" />
          <el-table-column prop="username" label="用户名" min-width="140" />
          <el-table-column prop="nickname" label="昵称" min-width="140" />
          <el-table-column label="角色" width="120">
            <template #default="{ row }">
              <span :class="['inline-tag', roleTagClass(row.role)]">{{ roleLabel(row.role) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <span :class="['inline-tag', statusTagClass(row.status)]">{{ statusLabel(row.status) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="角色调整" min-width="240">
            <template #default="{ row }">
              <div class="role-editor">
                <select v-model="roleEdits[row.id]" class="role-select">
                  <option value="PATIENT">患者</option>
                  <option value="DOCTOR">医生</option>
                  <option value="ADMIN">管理员</option>
                </select>
                <button
                  class="admin-primary-button compact"
                  type="button"
                  :disabled="roleSavingId === row.id || roleEdits[row.id] === row.role"
                  @click="saveRole(row)"
                >
                  {{ roleSavingId === row.id ? '保存中...' : '保存角色' }}
                </button>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="180">
            <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="最近登录" min-width="180">
            <template #default="{ row }">{{ formatDate(row.lastLoginAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" min-width="180" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <button
                  :class="['admin-secondary-button', 'compact', row.status === 'ACTIVE' ? 'danger' : 'success']"
                  type="button"
                  :disabled="statusSavingId === row.id"
                  @click="toggleUserStatus(row)"
                >
                  {{
                    statusSavingId === row.id
                      ? '处理中...'
                      : row.status === 'ACTIVE'
                        ? '禁用账号'
                        : '启用账号'
                  }}
                </button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="admin-pagination">
          <span>共 {{ userPagination.total }} 条</span>
          <el-pagination
            background
            layout="prev, pager, next"
            :current-page="userPagination.page"
            :page-size="userPagination.size"
            :total="userPagination.total"
            @current-change="handleUserPageChange"
          />
        </div>
      </section>

      <section v-else-if="activeTab === 'mcp'" class="admin-card admin-panel">
        <div class="admin-toolbar">
          <div class="admin-toolbar-copy">
            <h3>MCP 接入</h3>
            <p>支持 Streamable HTTP 远端服务，管理员可以保存配置、验证连通性并查看工具清单。</p>
          </div>
          <div class="admin-toolbar-actions">
            <button class="admin-primary-button" type="button" @click="openCreateMcpDialog">新增 MCP 服务</button>
            <button class="admin-secondary-button" type="button" @click="loadMcpServers">刷新列表</button>
          </div>
        </div>

        <el-table :data="mcpServers" v-loading="mcpLoading" class="admin-table" empty-text="暂无 MCP 服务">
          <el-table-column prop="name" label="服务名称" min-width="180" />
          <el-table-column label="传输类型" width="150">
            <template #default="{ row }">
              <span :class="['inline-tag', row.transportType === 'STREAMABLE_HTTP' ? 'inline-tag-active' : 'inline-tag-disabled']">
                {{ row.transportType === 'STREAMABLE_HTTP' ? 'Streamable HTTP' : row.transportType }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="baseUrl" label="服务地址" min-width="240" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <span :class="['inline-tag', row.lastStatus === 'OK' ? 'inline-tag-active' : row.lastStatus === 'FAILED' ? 'inline-tag-disabled' : 'inline-tag-patient']">
                {{ row.lastStatus === 'OK' ? '可用' : row.lastStatus === 'FAILED' ? '异常' : '未验证' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="服务信息" min-width="180">
            <template #default="{ row }">
              <div class="audit-meta">
                <span v-if="row.serverName">{{ row.serverName }}</span>
                <span v-if="row.serverVersion">{{ row.serverVersion }}</span>
                <span v-if="row.toolsCount !== null && row.toolsCount !== undefined">工具 {{ row.toolsCount }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="最近验证" min-width="180">
            <template #default="{ row }">{{ formatDate(row.lastCheckedAt) }}</template>
          </el-table-column>
          <el-table-column label="错误摘要" min-width="220">
            <template #default="{ row }">{{ row.lastError || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" min-width="240" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <button class="admin-secondary-button compact" type="button" @click="openEditMcpDialog(row)">编辑</button>
                <button
                  class="admin-primary-button compact"
                  type="button"
                  :disabled="mcpTestingId === row.id"
                  @click="testMcpServer(row)"
                >
                  {{ mcpTestingId === row.id ? '验证中...' : '验证连接' }}
                </button>
                <button class="admin-secondary-button compact danger" type="button" @click="deleteMcpServer(row)">删除</button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-else-if="activeTab === 'knowledge-ops'" class="admin-card admin-panel">
        <div class="admin-toolbar">
          <div class="admin-toolbar-copy">
            <h3>知识运营</h3>
            <p>集中处理“批量补齐检索字段”和“检索诊断”，不占用知识库文件管理主流程。</p>
          </div>
          <div class="admin-toolbar-actions">
            <button class="admin-secondary-button" type="button" @click="clearKnowledgeOps">清空结果</button>
          </div>
        </div>

        <div class="knowledge-ops-grid">
          <KnowledgeMetadataBackfillPanel
            v-model:hashes-text="metadataBackfillHashesText"
            :loading="knowledgeOpsLoading"
            :result="knowledgeOpsResult"
            scope-mode="global"
            allow-hash-input
            :scope-count="metadataBackfillScopeCount"
            :selected-count="0"
            @preview="previewMetadataBackfill"
            @apply="applyMetadataBackfill"
          />

          <KnowledgeRetrievalDiagnosticsPanel
            v-model:query="retrievalDiagnosticQuery"
            v-model:profile="retrievalDiagnosticProfile"
            :loading="retrievalDiagnosticLoading"
            :result="retrievalDiagnosticResult"
            @run="runRetrievalDiagnostic"
          />
        </div>
      </section>

      <section v-else class="admin-card admin-panel">
        <div class="admin-toolbar">
          <div class="admin-toolbar-copy">
            <h3>审计日志</h3>
            <p>展示认证日志、管理操作日志与聊天审计摘要，不返回完整聊天正文。</p>
          </div>
          <div class="admin-toolbar-actions">
            <button class="admin-secondary-button" type="button" @click="loadAuditLogs()">刷新日志</button>
          </div>
        </div>

        <div class="admin-filter-grid audit-filter-grid">
          <label class="admin-field admin-field-wide">
            <span>关键词</span>
            <input v-model.trim="auditFilters.keyword" type="text" placeholder="摘要、TraceId、目标 ID、Provider" />
          </label>
          <label class="admin-field">
            <span>动作类型</span>
            <select v-model="auditFilters.actionType">
              <option value="">全部</option>
              <option v-for="option in auditActionOptions" :key="option" :value="option">{{ option }}</option>
            </select>
          </label>
          <label class="admin-field">
            <span>日志范围</span>
            <select v-model="auditFilters.targetType">
              <option value="">全部</option>
              <option v-for="option in auditTargetOptions" :key="option" :value="option">{{ auditTargetLabel(option) }}</option>
            </select>
          </label>
          <label class="admin-field">
            <span>操作人 ID</span>
            <input v-model.trim="auditFilters.actorUserId" type="text" inputmode="numeric" placeholder="输入用户 ID" />
          </label>
          <label class="admin-field">
            <span>开始时间</span>
            <input v-model="auditFilters.createdFrom" type="datetime-local" />
          </label>
          <label class="admin-field">
            <span>结束时间</span>
            <input v-model="auditFilters.createdTo" type="datetime-local" />
          </label>
          <div class="admin-filter-actions">
            <button class="admin-primary-button" type="button" @click="applyAuditFilters">筛选</button>
            <button class="admin-secondary-button" type="button" @click="resetAuditFilters">重置</button>
          </div>
        </div>

        <div class="audit-quick-filters">
          <button
            v-for="option in auditTargetChipOptions"
            :key="option.value"
            :class="['audit-chip', { active: auditFilters.targetType === option.value }]"
            type="button"
            @click="applyAuditTargetType(option.value)"
          >
            {{ option.label }}
          </button>
        </div>

        <div class="audit-filter-summary">
          <span>当前结果 {{ auditPagination.total }} 条</span>
          <span v-if="auditFilters.targetType">范围：{{ auditTargetLabel(auditFilters.targetType) }}</span>
          <span v-if="auditFilters.actionType">动作：{{ auditActionLabel(auditFilters.actionType) }}</span>
          <span v-if="auditFilters.keyword">关键词：{{ auditFilters.keyword }}</span>
        </div>

        <el-table :data="auditLogs" v-loading="auditLoading" class="admin-table" empty-text="暂无审计日志">
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="actorLabel" label="操作人" min-width="170" show-overflow-tooltip />
          <el-table-column label="角色" width="92">
            <template #default="{ row }">{{ roleLabel(row.actorRole) }}</template>
          </el-table-column>
          <el-table-column label="范围/类型" min-width="170">
            <template #default="{ row }">
              <div class="audit-type-meta">
                <span :class="['inline-tag', auditTargetTagClass(row.targetType)]">{{ auditTargetLabel(row.targetType) }}</span>
                <span :class="['inline-tag', 'audit-action-tag']">{{ auditActionLabel(row.actionType) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip />
          <el-table-column label="会话/提供方" min-width="150">
            <template #default="{ row }">
              <div class="audit-meta">
                <span v-if="row.memoryId">#{{ row.memoryId }}</span>
                <span v-if="row.provider">{{ row.provider }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="检索摘要" min-width="220">
            <template #default="{ row }">
              <div v-if="row.actionType === 'CHAT_SUMMARY'" class="audit-retrieval-meta">
                <span v-if="row.queryType">类型 {{ row.queryType }}</span>
                <span>关键词 {{ row.retrievedCountKeyword ?? 0 }}</span>
                <span>向量 {{ row.retrievedCountVector ?? 0 }}</span>
                <span>合并 {{ row.mergedCount ?? 0 }}</span>
                <span>引用 {{ row.finalCitationCount ?? 0 }}</span>
                <span v-if="row.durationMs">总耗时 {{ formatDuration(row.durationMs) }}</span>
                <span v-if="row.firstTokenLatencyMs">首字 {{ formatDuration(row.firstTokenLatencyMs) }}</span>
                <span v-if="row.toolMode">{{ auditToolModeLabel(row.toolMode) }}</span>
                <span :class="{ danger: row.emptyRecall }">{{ row.emptyRecall ? '空召回' : '已命中' }}</span>
              </div>
              <span v-else class="trace-empty">—</span>
            </template>
          </el-table-column>
          <el-table-column label="链路/Trace" min-width="190">
            <template #default="{ row }">
              <div class="audit-trace-meta">
                <button
                  v-if="row.traceTimelineJson"
                  class="trace-link-button"
                  type="button"
                  @click="openAuditTraceTimeline(row)"
                >
                  查看链路
                </button>
                <button
                  v-if="row.traceId"
                  class="trace-link-button trace-id-button"
                  type="button"
                  @click="openTraceDetail(row.traceId)"
                >
                  {{ shortenTraceId(row.traceId) }}
                </button>
                <span v-if="!row.traceTimelineJson && !row.traceId" class="trace-empty">—</span>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="admin-pagination">
          <span>共 {{ auditPagination.total }} 条</span>
          <el-pagination
            background
            layout="prev, pager, next"
            :current-page="auditPagination.page"
            :page-size="auditPagination.size"
            :total="auditPagination.total"
            @current-change="handleAuditPageChange"
          />
        </div>
      </section>
    </main>

    <el-dialog
      v-model="doctorDialogVisible"
      width="min(92vw, 460px)"
      :close-on-click-modal="!doctorCreating"
      title="创建医生账号"
    >
      <div class="doctor-form">
        <label class="admin-field">
          <span>医生邮箱</span>
          <input v-model.trim="doctorForm.email" type="email" placeholder="请输入医生邮箱" />
        </label>
        <label class="admin-field">
          <span>医生昵称</span>
          <input v-model.trim="doctorForm.nickname" type="text" placeholder="医生姓名或科室昵称" />
        </label>
        <label class="admin-field">
          <span>初始密码</span>
          <input v-model="doctorForm.password" type="password" placeholder="请输入初始密码" />
        </label>
      </div>
      <template #footer>
        <div class="doctor-form-footer">
          <button class="admin-secondary-button" type="button" @click="doctorDialogVisible = false" :disabled="doctorCreating">取消</button>
          <button class="admin-primary-button" type="button" @click="createDoctor" :disabled="doctorCreating">
            {{ doctorCreating ? '创建中...' : '创建账号' }}
          </button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="mcpDialogVisible"
      width="min(92vw, 560px)"
      :close-on-click-modal="!mcpSaving"
      :title="mcpDialogTitle"
    >
      <div class="doctor-form">
        <label class="admin-field">
          <span>服务名称</span>
          <input v-model.trim="mcpForm.name" type="text" placeholder="服务名称" />
        </label>
        <label class="admin-field">
          <span>传输类型</span>
          <select v-model="mcpForm.transportType">
            <option value="STREAMABLE_HTTP">Streamable HTTP</option>
          </select>
        </label>
        <label class="admin-field">
          <span>服务地址</span>
          <input v-model.trim="mcpForm.baseUrl" type="url" placeholder="https://your-service-domain/mcp" />
        </label>
        <label class="admin-field">
          <span>用途说明</span>
          <input v-model.trim="mcpForm.description" type="text" placeholder="医院文档检索、工单系统等" />
        </label>
        <label class="admin-field">
          <span>连接超时（毫秒）</span>
          <input v-model.number="mcpForm.connectTimeoutMs" type="number" min="1000" max="60000" />
        </label>
        <label class="admin-field">
          <span>自定义请求头</span>
          <textarea
            v-model="mcpForm.headersText"
            rows="5"
            placeholder="Authorization: Bearer xxx&#10;X-Client-Id: aihos-admin"
          />
        </label>
        <label class="admin-switch-field">
          <input v-model="mcpForm.enabled" type="checkbox" />
          <span>启用该 MCP 服务</span>
        </label>
      </div>
      <template #footer>
        <div class="doctor-form-footer">
          <button class="admin-secondary-button" type="button" @click="mcpDialogVisible = false" :disabled="mcpSaving">取消</button>
          <button class="admin-primary-button" type="button" @click="saveMcpServer" :disabled="mcpSaving">
            {{ mcpSaving ? '保存中...' : '保存配置' }}
          </button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="auditTraceDialogVisible"
      width="min(92vw, 640px)"
      title="聊天链路耗时"
    >
      <div v-if="auditTraceDetail" class="audit-trace-dialog">
        <div class="audit-trace-summary">
          <span v-if="auditTraceDetail.provider">模型 {{ auditTraceDetail.provider }}</span>
          <span v-if="auditTraceDetail.toolMode">{{ auditToolModeLabel(auditTraceDetail.toolMode) }}</span>
          <span v-if="auditTraceDetail.durationMs">总耗时 {{ formatDuration(auditTraceDetail.durationMs) }}</span>
          <span v-if="auditTraceDetail.firstTokenLatencyMs">首字 {{ formatDuration(auditTraceDetail.firstTokenLatencyMs) }}</span>
          <span v-if="auditTraceDetail.traceId">traceId {{ shortenTraceId(auditTraceDetail.traceId) }}</span>
        </div>
        <div v-if="auditTraceDetail.stages.length" class="audit-trace-stage-list">
          <article
            v-for="stage in auditTraceDetail.stages"
            :key="stage.key"
            :class="['audit-trace-stage', { skipped: stage.status === 'SKIPPED' }]"
          >
            <div class="audit-trace-stage-head">
              <strong>{{ stage.label }}</strong>
              <span>{{ formatDuration(stage.durationMs) }}</span>
            </div>
            <p v-if="stage.detail">{{ stage.detail }}</p>
          </article>
        </div>
        <div v-else class="trace-empty">当前记录没有阶段耗时明细</div>
      </div>
    </el-dialog>

  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AdminEntryActionButton from '@/components/AdminEntryActionButton.vue'
import KnowledgeMetadataBackfillPanel from '@/components/KnowledgeMetadataBackfillPanel.vue'
import KnowledgeRetrievalDiagnosticsPanel from '@/components/KnowledgeRetrievalDiagnosticsPanel.vue'
import LogoutActionButton from '@/components/LogoutActionButton.vue'
import { apiClient, authState, isAdmin, logout } from '@/lib/auth'

const router = useRouter()
const SKYWALKING_TRACE_BASE = 'http://shenchaoqi.x3322.net:19200/General-Service/Services'

const tabs = [
  { value: 'users', label: '用户管理', description: '账号、角色、状态与医生账号统一处理' },
  { value: 'mcp', label: 'MCP 接入', description: '配置、验证并查看远端 MCP 服务能力' },
  { value: 'knowledge-ops', label: '知识运营', description: '检索字段补齐与召回诊断' },
  { value: 'logs', label: '审计日志', description: '认证、管理与聊天摘要审计' },
]

const auditActionOptions = [
  'AUTH_REGISTER',
  'AUTH_LOGIN',
  'AUTH_LOGOUT',
  'AUTH_REFRESH',
  'AUTH_PASSWORD_RESET',
  'ADMIN_CREATE_DOCTOR',
  'ADMIN_UPDATE_ROLE',
  'ADMIN_UPDATE_STATUS',
  'KNOWLEDGE_UPLOAD',
  'KNOWLEDGE_UPDATE',
  'KNOWLEDGE_DELETE',
  'KNOWLEDGE_PUBLISH',
  'KNOWLEDGE_ARCHIVE',
  'KNOWLEDGE_REPROCESS',
  'KNOWLEDGE_METADATA_BACKFILL_PREVIEW',
  'KNOWLEDGE_METADATA_BACKFILL_APPLY',
  'KNOWLEDGE_RETRIEVAL_DIAGNOSE',
  'MCP_SERVER_CREATE',
  'MCP_SERVER_UPDATE',
  'MCP_SERVER_DELETE',
  'MCP_SERVER_TEST',
  'MCP_TOOL_CALL',
  'CHAT_SUMMARY',
]

const auditTargetOptions = ['AUTH', 'USER', 'KNOWLEDGE', 'MCP', 'CHAT']
const auditTargetChipOptions = [
  { value: '', label: '全部范围' },
  { value: 'AUTH', label: '认证' },
  { value: 'USER', label: '用户/权限' },
  { value: 'KNOWLEDGE', label: '知识库' },
  { value: 'MCP', label: 'MCP' },
  { value: 'CHAT', label: '聊天' },
]

const activeTab = ref('users')
const users = ref([])
const usersLoading = ref(false)
const roleSavingId = ref(null)
const statusSavingId = ref(null)
const auditLogs = ref([])
const auditLoading = ref(false)
const mcpServers = ref([])
const mcpLoading = ref(false)
const mcpSaving = ref(false)
const mcpTestingId = ref(null)
const mcpDialogVisible = ref(false)
const editingMcpId = ref(null)
const doctorDialogVisible = ref(false)
const doctorCreating = ref(false)
const knowledgeOpsLoading = ref(false)
const knowledgeOpsResult = ref(null)
const metadataBackfillHashesText = ref('')
const retrievalDiagnosticQuery = ref('')
const retrievalDiagnosticProfile = ref('ONLINE')
const retrievalDiagnosticLoading = ref(false)
const retrievalDiagnosticResult = ref(null)
const auditTraceDialogVisible = ref(false)
const auditTraceDetail = ref(null)
const roleEdits = reactive({})
const userPagination = reactive({ total: 0, page: 1, size: 10 })
const auditPagination = reactive({ total: 0, page: 1, size: 20 })
const userFilters = reactive({ keyword: '', role: '', status: '' })
const auditFilters = reactive({ actionType: '', targetType: '', keyword: '', actorUserId: '', createdFrom: '', createdTo: '' })
const doctorForm = reactive({ email: '', nickname: '', password: '' })

const activeTabTitle = computed(() => tabs.find((tab) => tab.value === activeTab.value)?.label || '管理后台')
const activeTabDescription = computed(() => tabs.find((tab) => tab.value === activeTab.value)?.description || '')
const doctorCount = computed(() => users.value.filter((item) => item.role === 'DOCTOR').length)
const adminCount = computed(() => users.value.filter((item) => item.role === 'ADMIN').length)
const mcpEnabledCount = computed(() => mcpServers.value.filter((item) => item.enabled).length)
const adminDisplayName = computed(() => authState.user?.nickname || authState.user?.email || '管理员')
const mcpDialogTitle = computed(() => editingMcpId.value ? '编辑 MCP 服务' : '新增 MCP 服务')
const metadataBackfillHashes = computed(() => metadataBackfillHashesText.value
  .split(/[\s,，]+/)
  .map((item) => item.trim())
  .filter(Boolean))
const metadataBackfillScopeCount = computed(() => metadataBackfillHashes.value.length || 1)

const mcpForm = reactive({
  name: '',
  transportType: 'STREAMABLE_HTTP',
  baseUrl: '',
  description: '',
  enabled: true,
  connectTimeoutMs: 5000,
  headersText: '',
})

onMounted(async () => {
  if (!isAdmin.value) {
    await router.replace('/')
    return
  }
  await Promise.all([loadUsers(), loadAuditLogs(), loadMcpServers()])
})

const goToChat = () => {
  router.push('/')
}

const goToKnowledge = () => {
  router.push('/knowledge')
}

const handleLogout = async () => {
  await logout()
  await router.replace('/login')
}

const loadUsers = async (page = userPagination.page) => {
  usersLoading.value = true
  try {
    const { data } = await apiClient.get('/api/aimed/admin/users', {
      params: {
        page,
        size: userPagination.size,
        keyword: userFilters.keyword || undefined,
        role: userFilters.role || undefined,
        status: userFilters.status || undefined,
      },
    })
    users.value = Array.isArray(data?.items) ? data.items : []
    userPagination.total = Number(data?.total || 0)
    userPagination.page = Number(data?.page || page)
    userPagination.size = Number(data?.size || userPagination.size)
    syncRoleEdits()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '用户列表加载失败'))
  } finally {
    usersLoading.value = false
  }
}

const loadAuditLogs = async (page = auditPagination.page) => {
  auditLoading.value = true
  try {
    const { data } = await apiClient.get('/api/aimed/admin/audit-logs', {
      params: {
        page,
        size: auditPagination.size,
        actionType: auditFilters.actionType || undefined,
        targetType: auditFilters.targetType || undefined,
        keyword: auditFilters.keyword || undefined,
        actorUserId: normalizeActorUserId(auditFilters.actorUserId),
        createdFrom: auditFilters.createdFrom ? normalizeLocalDateTime(auditFilters.createdFrom) : undefined,
        createdTo: auditFilters.createdTo ? normalizeLocalDateTime(auditFilters.createdTo) : undefined,
      },
    })
    auditLogs.value = Array.isArray(data?.items) ? data.items : []
    auditPagination.total = Number(data?.total || 0)
    auditPagination.page = Number(data?.page || page)
    auditPagination.size = Number(data?.size || auditPagination.size)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '审计日志加载失败'))
  } finally {
    auditLoading.value = false
  }
}

const loadMcpServers = async () => {
  mcpLoading.value = true
  try {
    const { data } = await apiClient.get('/api/aimed/admin/mcp/servers')
    mcpServers.value = Array.isArray(data) ? data : []
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, 'MCP 服务列表加载失败'))
  } finally {
    mcpLoading.value = false
  }
}

const applyUserFilters = () => {
  userPagination.page = 1
  loadUsers(1)
}

const resetUserFilters = () => {
  userFilters.keyword = ''
  userFilters.role = ''
  userFilters.status = ''
  applyUserFilters()
}

const applyAuditFilters = () => {
  auditPagination.page = 1
  loadAuditLogs(1)
}

const resetAuditFilters = () => {
  auditFilters.actionType = ''
  auditFilters.targetType = ''
  auditFilters.keyword = ''
  auditFilters.actorUserId = ''
  auditFilters.createdFrom = ''
  auditFilters.createdTo = ''
  applyAuditFilters()
}

const applyAuditTargetType = (value) => {
  auditFilters.targetType = value
  applyAuditFilters()
}

const handleUserPageChange = (page) => {
  userPagination.page = page
  loadUsers(page)
}

const handleAuditPageChange = (page) => {
  auditPagination.page = page
  loadAuditLogs(page)
}

const createDoctor = async () => {
  doctorCreating.value = true
  try {
    await apiClient.post('/api/aimed/admin/doctors', {
      email: doctorForm.email,
      nickname: doctorForm.nickname,
      password: doctorForm.password,
    })
    ElMessage.success('医生账号已创建')
    doctorDialogVisible.value = false
    resetDoctorForm()
    await loadUsers(1)
    await loadAuditLogs(1)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '医生账号创建失败'))
  } finally {
    doctorCreating.value = false
  }
}

const openCreateMcpDialog = () => {
  editingMcpId.value = null
  resetMcpForm()
  mcpDialogVisible.value = true
}

const openEditMcpDialog = (row) => {
  editingMcpId.value = row.id
  mcpForm.name = row.name || ''
  mcpForm.transportType = row.transportType || 'STREAMABLE_HTTP'
  mcpForm.baseUrl = row.baseUrl || ''
  mcpForm.description = row.description || ''
  mcpForm.enabled = Boolean(row.enabled)
  mcpForm.connectTimeoutMs = Number(row.connectTimeoutMs || 5000)
  mcpForm.headersText = formatHeadersText(row.headers)
  mcpDialogVisible.value = true
}

const saveMcpServer = async () => {
  mcpSaving.value = true
  try {
    const payload = {
      name: mcpForm.name,
      transportType: mcpForm.transportType,
      baseUrl: mcpForm.baseUrl,
      description: mcpForm.description,
      enabled: mcpForm.enabled,
      connectTimeoutMs: Number(mcpForm.connectTimeoutMs || 5000),
      headers: parseHeadersText(mcpForm.headersText),
    }
    if (editingMcpId.value) {
      await apiClient.put(`/api/aimed/admin/mcp/servers/${editingMcpId.value}`, payload)
      ElMessage.success('MCP 服务已更新')
    } else {
      await apiClient.post('/api/aimed/admin/mcp/servers', payload)
      ElMessage.success('MCP 服务已创建')
    }
    mcpDialogVisible.value = false
    resetMcpForm()
    await Promise.all([loadMcpServers(), loadAuditLogs(1)])
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, 'MCP 服务保存失败'))
  } finally {
    mcpSaving.value = false
  }
}

const testMcpServer = async (row) => {
  mcpTestingId.value = row.id
  try {
    const { data } = await apiClient.post(`/api/aimed/admin/mcp/servers/${row.id}/test`)
    if (data?.success) {
      ElMessage.success(data.message || 'MCP 服务连接成功')
    } else {
      ElMessage.warning(data?.message || 'MCP 服务验证失败')
    }
    await Promise.all([loadMcpServers(), loadAuditLogs(1)])
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, 'MCP 服务验证失败'))
  } finally {
    mcpTestingId.value = null
  }
}

const deleteMcpServer = async (row) => {
  try {
    await apiClient.delete(`/api/aimed/admin/mcp/servers/${row.id}`)
    ElMessage.success('MCP 服务已删除')
    await Promise.all([loadMcpServers(), loadAuditLogs(1)])
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, 'MCP 服务删除失败'))
  }
}

const clearKnowledgeOps = () => {
  knowledgeOpsResult.value = null
  retrievalDiagnosticResult.value = null
}

const previewMetadataBackfill = async () => {
  knowledgeOpsLoading.value = true
  try {
    const { data } = await apiClient.post('/api/aimed/admin/knowledge/metadata/backfill/preview', {
      hashes: metadataBackfillHashes.value,
    })
    knowledgeOpsResult.value = data
    ElMessage.success(`回填预览完成，命中 ${data?.matchedCount || 0} 个文件。`)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '检索字段补齐预览失败'))
  } finally {
    knowledgeOpsLoading.value = false
  }
}

const applyMetadataBackfill = async () => {
  knowledgeOpsLoading.value = true
  try {
    const { data } = await apiClient.post('/api/aimed/admin/knowledge/metadata/backfill', {
      hashes: metadataBackfillHashes.value,
    })
    knowledgeOpsResult.value = data
    ElMessage.success(`检索字段补齐已执行，更新 ${data?.updatedCount || 0} 个文件。`)
    await loadAuditLogs(1)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '检索字段补齐执行失败'))
  } finally {
    knowledgeOpsLoading.value = false
  }
}

const runRetrievalDiagnostic = async () => {
  if (!retrievalDiagnosticQuery.value.trim()) {
    ElMessage.warning('请输入需要诊断的检索内容')
    return
  }
  retrievalDiagnosticLoading.value = true
  try {
    const { data } = await apiClient.post('/api/aimed/admin/knowledge/retrieval/diagnose', {
      query: retrievalDiagnosticQuery.value,
      profile: retrievalDiagnosticProfile.value,
    })
    retrievalDiagnosticResult.value = data
    if (data?.emptyRecall) {
      ElMessage.warning('当前内容没有召回结果')
    }
    await loadAuditLogs(1)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '知识检索诊断失败'))
  } finally {
    retrievalDiagnosticLoading.value = false
  }
}

const saveRole = async (row) => {
  roleSavingId.value = row.id
  try {
    const { data } = await apiClient.put(`/api/aimed/admin/users/${row.id}/role`, {
      role: roleEdits[row.id],
    })
    roleEdits[row.id] = data?.role || roleEdits[row.id]
    ElMessage.success('角色已更新')
    await loadUsers(userPagination.page)
    await loadAuditLogs(1)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '角色更新失败'))
    roleEdits[row.id] = row.role
  } finally {
    roleSavingId.value = null
  }
}

const toggleUserStatus = async (row) => {
  statusSavingId.value = row.id
  const nextStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    const { data } = await apiClient.put(`/api/aimed/admin/users/${row.id}/status`, {
      status: nextStatus,
    })
    ElMessage.success(nextStatus === 'ACTIVE' ? '账号已启用' : '账号已禁用')
    if (row.id === authState.user?.id && data?.status === 'DISABLED') {
      await logout()
      await router.replace('/login')
      return
    }
    await loadUsers(userPagination.page)
    await loadAuditLogs(1)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, nextStatus === 'ACTIVE' ? '账号启用失败' : '账号禁用失败'))
  } finally {
    statusSavingId.value = null
  }
}

const openTraceDetail = (traceId) => {
  // 管理后台不再维护一套自定义 trace 视图，直接跳原生 SkyWalking。
  const targetUrl = `${SKYWALKING_TRACE_BASE}?swService=aihos&swTraceId=${encodeURIComponent(traceId)}`
  const opened = window.open(targetUrl, '_blank', 'noopener,noreferrer')
  if (!opened) {
    ElMessage.warning('浏览器拦截了新窗口，请允许当前站点打开 SkyWalking 页面')
  }
}

const parseAuditTraceTimeline = (value) => {
  if (!value) {
    return []
  }
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

const openAuditTraceTimeline = (row) => {
  auditTraceDetail.value = {
    traceId: row.traceId || '',
    provider: row.provider || '',
    toolMode: row.toolMode || '',
    durationMs: Number(row.durationMs || 0),
    firstTokenLatencyMs: Number(row.firstTokenLatencyMs || 0),
    stages: parseAuditTraceTimeline(row.traceTimelineJson),
  }
  auditTraceDialogVisible.value = true
}

const syncRoleEdits = () => {
  users.value.forEach((user) => {
    roleEdits[user.id] = user.role
  })
}

const resetDoctorForm = () => {
  doctorForm.email = ''
  doctorForm.nickname = ''
  doctorForm.password = ''
}

const resetMcpForm = () => {
  mcpForm.name = ''
  mcpForm.transportType = 'STREAMABLE_HTTP'
  mcpForm.baseUrl = ''
  mcpForm.description = ''
  mcpForm.enabled = true
  mcpForm.connectTimeoutMs = 5000
  mcpForm.headersText = ''
}

const formatDate = (value) => {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(date)
}

const formatDuration = (value) => `${Number(value || 0)} ms`

const auditToolModeLabel = (value) => {
  const labels = {
    APPOINTMENT: '挂号工具链',
    FAST: '快速问答链',
    STANDARD: '标准链路',
  }
  return labels[value] || value || '标准链路'
}

const shortenTraceId = (traceId) => {
  if (!traceId || traceId.length <= 24) {
    return traceId
  }
  return `${traceId.slice(0, 18)}...${traceId.slice(-6)}`
}

const normalizeLocalDateTime = (value) => {
  if (!value) {
    return undefined
  }
  return value.length === 16 ? `${value}:00` : value
}

const normalizeActorUserId = (value) => {
  if (!value) {
    return undefined
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

const resolveErrorMessage = (error, fallback) => {
  const responseMessage = error?.response?.data?.message
  const traceId = error?.response?.data?.traceId || error?.response?.headers?.['x-trace-id']
  if (responseMessage && traceId) {
    return `${responseMessage}（traceId: ${traceId}）`
  }
  return responseMessage || fallback
}

const roleTagClass = (role) => {
  if (role === 'ADMIN') {
    return 'inline-tag-admin'
  }
  if (role === 'DOCTOR') {
    return 'inline-tag-doctor'
  }
  return 'inline-tag-patient'
}

const statusTagClass = (status) => (status === 'ACTIVE' ? 'inline-tag-active' : 'inline-tag-disabled')

const roleLabel = (role) => {
  const labels = {
    ADMIN: '管理员',
    DOCTOR: '医生',
    PATIENT: '患者',
  }
  return labels[role] || role || '—'
}

const statusLabel = (status) => {
  const labels = {
    ACTIVE: '启用',
    DISABLED: '禁用',
  }
  return labels[status] || status || '—'
}

const auditActionLabel = (actionType) => {
  const labels = {
    AUTH_REGISTER: '注册',
    AUTH_LOGIN: '登录',
    AUTH_LOGOUT: '退出',
    AUTH_REFRESH: '刷新登录态',
    AUTH_PASSWORD_RESET: '重置密码',
    ADMIN_CREATE_DOCTOR: '创建医生',
    ADMIN_UPDATE_ROLE: '调整角色',
    ADMIN_UPDATE_STATUS: '调整状态',
    KNOWLEDGE_UPLOAD: '上传知识',
    KNOWLEDGE_UPDATE: '更新知识',
    KNOWLEDGE_DELETE: '删除知识',
    KNOWLEDGE_PUBLISH: '发布知识',
    KNOWLEDGE_ARCHIVE: '归档知识',
    KNOWLEDGE_REPROCESS: '重新处理',
    KNOWLEDGE_METADATA_BACKFILL_PREVIEW: '预览检索字段补齐',
    KNOWLEDGE_METADATA_BACKFILL_APPLY: '执行检索字段补齐',
    KNOWLEDGE_RETRIEVAL_DIAGNOSE: '检索诊断',
    MCP_SERVER_CREATE: '创建 MCP 服务',
    MCP_SERVER_UPDATE: '更新 MCP 服务',
    MCP_SERVER_DELETE: '删除 MCP 服务',
    MCP_SERVER_TEST: '验证 MCP 服务',
    MCP_TOOL_CALL: '调用 MCP 工具',
    CHAT_SUMMARY: '聊天摘要',
  }
  return labels[actionType] || actionType
}

const auditTargetLabel = (targetType) => {
  const labels = {
    AUTH: '认证',
    USER: '用户',
    KNOWLEDGE: '知识库',
    MCP: 'MCP',
    CHAT: '聊天',
  }
  return labels[targetType] || targetType || '全部'
}

const parseHeadersText = (value) => {
  if (!value || !value.trim()) {
    return []
  }
  return value
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const separator = line.indexOf(':')
      if (separator <= 0) {
        throw new Error('请求头格式应为 Key: Value')
      }
      return {
        key: line.slice(0, separator).trim(),
        value: line.slice(separator + 1).trim(),
      }
    })
    .filter((item) => item.key && item.value !== undefined)
}

const formatHeadersText = (headers) => {
  if (!Array.isArray(headers) || !headers.length) {
    return ''
  }
  return headers.map((item) => `${item.key}: ${item.value}`).join('\n')
}

const auditTargetTagClass = (targetType) => {
  const classes = {
    AUTH: 'inline-tag-auth',
    USER: 'inline-tag-user',
    KNOWLEDGE: 'inline-tag-knowledge',
    MCP: 'inline-tag-mcp',
    CHAT: 'inline-tag-chat',
  }
  return classes[targetType] || 'audit-action-tag'
}
</script>

<style scoped>
.admin-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(272px, 304px) minmax(0, 1fr);
  gap: 18px;
  padding: 18px;
  background:
    radial-gradient(circle at top left, rgba(155, 214, 217, 0.42), transparent 32%),
    linear-gradient(180deg, #eff8f8 0%, #f8fcfc 44%, #eef7f7 100%);
}

.admin-sidebar,
.admin-main {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

@media (min-width: 1101px) {
  .admin-sidebar {
    position: sticky;
    top: 18px;
    max-height: calc(100dvh - 36px);
    overflow-y: auto;
    padding-right: 4px;
  }
}

.admin-card,
.admin-hero {
  border: 1px solid rgba(145, 197, 198, 0.3);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 24px 44px rgba(74, 123, 128, 0.08);
  backdrop-filter: blur(18px);
}

.admin-card {
  padding: 18px;
}

.admin-brand-card h1,
.admin-hero h2 {
  margin: 8px 0 0;
  font-size: 28px;
  line-height: 1.12;
  color: #214f56;
}

.admin-eyebrow {
  margin: 0;
  color: #5c9198;
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  font-weight: 700;
}

.admin-subtitle,
.admin-hero-copy {
  margin: 10px 0 0;
  color: #6c8d93;
  line-height: 1.65;
  font-size: 13px;
}

.admin-nav-actions,
.admin-hero-actions,
.admin-toolbar-actions,
.admin-filter-actions,
.doctor-form-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.admin-nav-actions {
  margin-top: 14px;
}

.admin-link-button,
.admin-secondary-button,
.admin-primary-button {
  border: 1px solid rgba(96, 170, 171, 0.24);
  border-radius: 14px;
  min-height: 40px;
  padding: 0 14px;
  cursor: pointer;
  font: inherit;
  font-weight: 700;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease, opacity 0.18s ease;
}

.admin-link-button,
.admin-secondary-button {
  background: rgba(243, 250, 250, 0.94);
  color: #265e67;
}

.admin-primary-button {
  background: linear-gradient(135deg, #4aa7a5, #66c0a8);
  color: #fff;
  box-shadow: 0 18px 28px rgba(76, 160, 157, 0.22);
}

.admin-link-button.secondary {
  background: rgba(232, 244, 244, 0.92);
}

.admin-link-button:hover,
.admin-secondary-button:hover,
.admin-primary-button:hover {
  transform: translateY(-1px);
}

.admin-side-heading,
.admin-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.admin-side-heading span,
.admin-toolbar-copy h3 {
  color: #214f56;
  font-weight: 800;
}

.admin-side-heading small,
.admin-toolbar-copy p {
  color: #7b9aa0;
}

.admin-toolbar-copy h3 {
  margin: 0;
  font-size: 22px;
}

.admin-toolbar-copy p {
  margin: 8px 0 0;
  font-size: 14px;
}

.admin-tab-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.admin-tab-button {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid rgba(96, 170, 171, 0.18);
  background: rgba(239, 248, 248, 0.88);
  cursor: pointer;
  text-align: left;
  color: #265e67;
}

.admin-tab-button.active {
  border-color: rgba(76, 160, 157, 0.34);
  background: linear-gradient(135deg, rgba(74, 167, 165, 0.12), rgba(102, 192, 168, 0.14));
  box-shadow: inset 0 0 0 1px rgba(74, 167, 165, 0.12);
}

.admin-tab-button strong {
  font-size: 15px;
}

.admin-tab-button span {
  font-size: 12px;
  color: #6f9197;
  line-height: 1.55;
}

.admin-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.admin-summary-item {
  padding: 12px;
  border-radius: 16px;
  background: rgba(239, 248, 248, 0.9);
  border: 1px solid rgba(128, 187, 189, 0.18);
}

.admin-summary-item span {
  display: block;
  color: #7d9ca2;
  font-size: 12px;
}

.admin-summary-item strong {
  display: block;
  margin-top: 6px;
  color: #225760;
  font-size: 22px;
}

.admin-summary-item-soft strong {
  font-size: 18px;
  line-height: 1.4;
}

.admin-hero {
  padding: 20px 22px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.admin-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.admin-filter-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) auto;
  gap: 12px;
}

.audit-filter-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr)) auto;
}

.admin-field-wide {
  grid-column: span 2;
}

.admin-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #517a81;
  font-size: 12px;
  font-weight: 700;
}

.admin-field input,
.admin-field select,
.admin-field textarea {
  min-height: 40px;
  border-radius: 12px;
  border: 1px solid rgba(136, 191, 193, 0.28);
  background: rgba(247, 252, 252, 0.95);
  padding: 0 12px;
  color: #1f555d;
  font: inherit;
  outline: none;
}

.admin-field textarea {
  min-height: 112px;
  padding: 10px 12px;
  resize: vertical;
}

.admin-switch-field {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #517a81;
  font-size: 13px;
  font-weight: 700;
}

.admin-switch-field input {
  width: 16px;
  height: 16px;
}

.knowledge-ops-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.82fr) minmax(0, 1.38fr);
  gap: 16px;
  align-items: start;
}

.admin-table {
  width: 100%;
}

.admin-table :deep(.el-table__header-wrapper th) {
  background: rgba(239, 248, 248, 0.9);
  color: #517a81;
}

.admin-table :deep(.el-table__row td) {
  background: rgba(255, 255, 255, 0.74);
}

.inline-tag {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.inline-tag-admin {
  background: rgba(41, 111, 122, 0.14);
  color: #225d67;
}

.inline-tag-doctor {
  background: rgba(64, 170, 146, 0.14);
  color: #23725e;
}

.inline-tag-patient {
  background: rgba(87, 173, 163, 0.12);
  color: #30787a;
}

.inline-tag-active {
  background: rgba(96, 186, 130, 0.12);
  color: #2f7a49;
}

.inline-tag-disabled {
  background: rgba(205, 108, 108, 0.12);
  color: #9d4f4f;
}

.audit-action-tag {
  background: rgba(81, 122, 129, 0.12);
  color: #3f6870;
}

.inline-tag-auth {
  background: rgba(94, 129, 172, 0.12);
  color: #3e648e;
}

.inline-tag-user {
  background: rgba(74, 167, 165, 0.12);
  color: #2d6f72;
}

.inline-tag-knowledge {
  background: rgba(95, 174, 130, 0.12);
  color: #2f7550;
}

.inline-tag-chat {
  background: rgba(196, 154, 81, 0.14);
  color: #8a6330;
}

.inline-tag-mcp {
  background: rgba(89, 121, 196, 0.14);
  color: #3f5ca4;
}

.admin-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: #6f9197;
}

.role-editor {
  display: flex;
  align-items: center;
  gap: 10px;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.role-select {
  min-height: 38px;
  border-radius: 12px;
  border: 1px solid rgba(136, 191, 193, 0.28);
  background: rgba(247, 252, 252, 0.95);
  padding: 0 12px;
  color: #1f555d;
}

.admin-primary-button.compact {
  min-height: 38px;
  padding: 0 12px;
}

.admin-secondary-button.compact {
  min-height: 36px;
  padding: 0 12px;
}

.admin-secondary-button.danger {
  border-color: rgba(205, 108, 108, 0.24);
  color: #9d4f4f;
  background: rgba(255, 243, 243, 0.94);
}

.admin-secondary-button.success {
  border-color: rgba(96, 186, 130, 0.24);
  color: #2f7a49;
  background: rgba(242, 251, 245, 0.94);
}

.audit-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #6f9197;
  font-size: 12px;
}

.audit-type-meta,
.audit-trace-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.audit-retrieval-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.audit-retrieval-meta span {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 9px;
  border-radius: 999px;
  background: rgba(239, 248, 248, 0.9);
  color: #52757c;
  font-size: 11px;
}

.audit-retrieval-meta span.danger {
  background: rgba(255, 238, 238, 0.92);
  color: #af5252;
}

.audit-quick-filters,
.audit-filter-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.audit-chip {
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid rgba(136, 191, 193, 0.24);
  border-radius: 999px;
  background: rgba(244, 250, 250, 0.96);
  color: #3f6870;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}

.audit-chip.active {
  border-color: rgba(74, 167, 165, 0.34);
  background: linear-gradient(135deg, rgba(74, 167, 165, 0.14), rgba(102, 192, 168, 0.16));
  color: #225d67;
}

.audit-filter-summary {
  color: #6f9197;
  font-size: 13px;
}

.audit-filter-summary span {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(239, 248, 248, 0.9);
}

.trace-link-button {
  max-width: 100%;
  padding: 7px 11px;
  border: 1px solid rgba(82, 160, 164, 0.22);
  border-radius: 999px;
  background: rgba(239, 249, 249, 0.88);
  color: #1e5f69;
  font: inherit;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.35;
  cursor: pointer;
  text-align: left;
  transition: transform 0.16s ease, box-shadow 0.16s ease, border-color 0.16s ease;
}

.trace-id-button {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.trace-link-button:hover {
  transform: translateY(-1px);
  border-color: rgba(82, 160, 164, 0.38);
  box-shadow: 0 10px 22px rgba(69, 136, 141, 0.14);
}

.trace-empty {
  color: rgba(54, 87, 94, 0.52);
}

.audit-trace-dialog {
  display: grid;
  gap: 12px;
}

.audit-trace-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.audit-trace-summary span {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(239, 248, 248, 0.9);
  color: #52757c;
  font-size: 12px;
}

.audit-trace-stage-list {
  display: grid;
  gap: 10px;
}

.audit-trace-stage {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(246, 250, 250, 0.96);
}

.audit-trace-stage.skipped {
  background: rgba(245, 246, 247, 0.96);
}

.audit-trace-stage-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #24545d;
}

.audit-trace-stage p {
  margin: 8px 0 0;
  color: #67828a;
  font-size: 12px;
  line-height: 1.5;
}

.doctor-form {
  display: grid;
  gap: 14px;
}

@media (max-width: 1080px) {
  .admin-shell {
    grid-template-columns: 1fr;
  }

  .audit-filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .knowledge-ops-grid {
    grid-template-columns: 1fr;
  }

  .admin-field-wide {
    grid-column: span 2;
  }

}

@media (max-width: 720px) {
  .admin-shell {
    padding: 14px;
    gap: 14px;
  }

  .admin-card,
  .admin-hero {
    padding: 18px;
    border-radius: 24px;
    backdrop-filter: none;
  }

  .admin-brand-card h1,
  .admin-hero h2 {
    font-size: 26px;
  }

  .admin-hero,
  .admin-toolbar,
  .admin-pagination {
    flex-direction: column;
    align-items: stretch;
  }

  .admin-filter-grid,
  .audit-filter-grid,
  .admin-summary-grid,
  .knowledge-ops-grid {
    grid-template-columns: 1fr;
  }

  .admin-field-wide {
    grid-column: auto;
  }

  .role-editor {
    flex-direction: column;
    align-items: stretch;
  }

  .row-actions {
    justify-content: flex-start;
  }

}
</style>
