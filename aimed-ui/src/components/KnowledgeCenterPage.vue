<template>
  <div class="knowledge-shell">
    <aside class="knowledge-sidebar">
      <div class="sidebar-card brand-card">
        <div class="brand-lockup">
          <img :src="shulanLogo" alt="杭州树兰医院" class="brand-logo" />
          <div>
            <h1>树兰知识库管理</h1>
          </div>
        </div>
      </div>

      <div class="sidebar-card operations-card">
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
        <p class="sidebar-note sidebar-note-steps">
          支持 PDF / Office / Markdown / TXT，上传后进入待发布。
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

      <div class="sidebar-card filter-card">
        <div class="sidebar-heading">
          <span>快速筛选</span>
          <strong>{{ filteredFiles.length }}</strong>
        </div>
        <div class="stage-filter-row">
          <button
            v-for="item in stageFilterOptions"
            :key="item.value"
            :class="['stage-filter-chip', { active: stageFilter === item.value }]"
            type="button"
            @click="stageFilter = item.value"
          >
            <span>{{ item.label }}</span>
            <strong>{{ stageCounts[item.value] || 0 }}</strong>
          </button>
        </div>
        <div class="batch-toolbar">
          <button class="secondary-button batch-button" type="button" @click="toggleSelectAllVisible">
            {{ filteredFiles.length && filteredFiles.every((file) => selectedBatchHashes.includes(file.hash)) ? '取消全选' : '全选可见' }}
          </button>
          <button
            class="primary-button batch-button"
            type="button"
            :disabled="batchPublishableCount === 0 || hasBatchProcessingFiles"
            @click="runBatchAction('publish')"
          >
            批量上线 {{ batchPublishableCount || '' }}
          </button>
          <button
            class="secondary-button batch-button"
            type="button"
            :disabled="batchArchivableCount === 0 || hasBatchProcessingFiles"
            @click="runBatchAction('archive')"
          >
            批量归档 {{ batchArchivableCount || '' }}
          </button>
        </div>
        <p v-if="hasBatchProcessingFiles" class="sidebar-note sidebar-note-processing">
          当前勾选文件里有“处理中/重建中”的项，等待完成后才能执行批量发布或归档。
        </p>
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
              <label class="knowledge-select-box" @click.stop>
                <input
                  type="checkbox"
                  :checked="selectedBatchHashes.includes(file.hash)"
                  @change="toggleBatchHash(file.hash, $event.target.checked)"
                />
              </label>
              <span class="knowledge-name">{{ file.originalFilename || file.fileName }}</span>
            </div>
            <div class="knowledge-meta knowledge-meta-compact">
              <span :class="['processing-tag', `processing-tag-stage-${knowledgeStage(file.processingStatus)}`]">
                {{ stageLabel(file.processingStatus) }}
              </span>
              <span :class="['source-tag', `source-tag-${file.source}`]">
                {{ sourceLabel(file.source) }}
              </span>
              <span>{{ file.extension?.toUpperCase() || 'FILE' }}</span>
              <span>{{ formatSize(file.size) }}</span>
            </div>
            <div class="knowledge-subline">
              <span>{{ file.parser || '未知解析器' }}</span>
              <span>{{ file.editable ? '可编辑' : '只读' }}</span>
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
        <div class="summary-content">
          <div>
            <p class="summary-kicker">知识详情</p>
            <h2>{{ selectedDetail?.originalFilename || '请选择一个知识文件' }}</h2>
            <p v-if="selectedDetail" class="summary-caption">
              {{ detailStageHint || '查看原文、元数据和 RAG 切分结果。' }}
            </p>
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
        </div>
        <div class="summary-actions">
          <button class="summary-nav-button" type="button" @click="backToChat">
            智能问答
          </button>
          <AdminEntryActionButton v-if="isAdmin" @click="openAdminConsole" />
          <LogoutActionButton @click="handleLogout" />
        </div>
      </section>

      <section v-if="selectedDetail" class="detail-card">
        <div v-if="isDetailProcessing" class="detail-lock-banner">
          <strong>当前文件正在重建中</strong>
          <p>{{ selectedDetail.statusMessage || '正在解析文档并重建 RAG 索引，完成前已锁定发布、归档、删除和编辑操作。' }}</p>
        </div>
        <div class="knowledge-stage-strip">
          <div :class="['knowledge-stage-item', { active: currentKnowledgeStage === 'processing' }]">
            <span>处理中</span>
            <small>{{ currentKnowledgeStage === 'processing' ? detailStageHint : '上传、解析、切分、重建' }}</small>
          </div>
          <div :class="['knowledge-stage-item', { active: currentKnowledgeStage === 'ready' }]">
            <span>待发布</span>
            <small>{{ currentKnowledgeStage === 'ready' ? detailStageHint : '已完成索引，等待上线' }}</small>
          </div>
          <div :class="['knowledge-stage-item', { active: currentKnowledgeStage === 'online' }]">
            <span>{{ selectedDetail.processingStatus === 'ARCHIVED' ? '已归档' : '已上线' }}</span>
            <small>{{ currentKnowledgeStage === 'online' ? detailStageHint : '已上线或已下线归档' }}</small>
          </div>
        </div>
        <div class="detail-toolbar">
          <div class="detail-badges">
            <span class="detail-badge">哈希 {{ selectedDetail.hash }}</span>
            <span class="detail-badge">{{ sourceLabel(selectedDetail.source) }}</span>
            <span class="detail-badge">{{ selectedDetail.parser || '未知解析器' }}</span>
            <span :class="['detail-badge', 'detail-badge-status', `processing-tag-stage-${currentKnowledgeStage}`]">
              {{ detailStatusText }}
            </span>
          </div>
          <div class="detail-actions">
            <button
              v-if="selectedDetail.editable && selectedDetail.processingStatus === 'READY' && !isEditing"
              class="secondary-button slim-button"
              type="button"
              @click="startEdit"
              :disabled="isDetailActionLocked"
            >
              编辑内容
            </button>
            <button
              v-if="['READY', 'ARCHIVED'].includes(selectedDetail.processingStatus)"
              class="primary-button slim-button"
              type="button"
              @click="publishFile"
              :disabled="isDetailActionLocked"
            >
              {{ selectedDetail.processingStatus === 'ARCHIVED' ? '重新上线' : '发布上线' }}
            </button>
            <button
              v-if="selectedDetail.processingStatus === 'PUBLISHED'"
              class="secondary-button slim-button"
              type="button"
              @click="archiveFile"
              :disabled="isDetailActionLocked"
            >
              归档下线
            </button>
            <button
              v-if="['READY', 'FAILED', 'ARCHIVED', 'PUBLISHED'].includes(selectedDetail.processingStatus)"
              class="secondary-button slim-button"
              type="button"
              @click="reprocessFile"
              :disabled="isDetailActionLocked"
            >
              重新处理
            </button>
            <button
              class="secondary-button slim-button"
              type="button"
              @click="saveMetadata"
              :disabled="!canSaveMetadata || isDetailActionLocked"
            >
              {{ isSaving ? '保存中...' : isPollingProcessing ? '等待重建完成...' : '保存元数据' }}
            </button>
            <button
              v-if="isEditing"
              class="secondary-button slim-button"
              type="button"
              @click="cancelEdit"
              :disabled="isDetailActionLocked"
            >
              取消
            </button>
            <button
              v-if="isEditing"
              class="primary-button slim-button"
              type="button"
              @click="saveEdit"
              :disabled="isDetailActionLocked"
            >
              {{ isSaving ? '保存中...' : isPollingProcessing ? '等待重建完成...' : '保存并重建 RAG' }}
            </button>
            <button
              v-if="selectedDetail.deletable"
              class="danger-button slim-button"
              type="button"
              @click="deleteFile"
              :disabled="isDeleting || isDetailActionLocked"
            >
              {{ isDeleting ? '删除中...' : '删除文件' }}
            </button>
          </div>
        </div>

        <section class="content-card detail-section-card">
          <button class="section-heading section-heading-toggle" type="button" @click="toggleDetailSection('overview')">
            <div>
              <span>文件概览</span>
              <small>先看状态、规模和使用范围</small>
            </div>
            <strong>{{ detailSections.overview ? '收起' : '展开' }}</strong>
          </button>
          <div v-if="detailSections.overview" class="detail-section-body">
            <div class="detail-grid">
              <div class="meta-card">
                <span>文档类型</span>
                <strong>{{ docTypeLabel(selectedDetail.docType) }}</strong>
              </div>
              <div class="meta-card">
                <span>适用对象</span>
                <strong>{{ audienceLabel(selectedDetail.audience) }}</strong>
              </div>
              <div class="meta-card">
                <span>科室归属</span>
                <strong>{{ departmentLabel(selectedDetail.department) }}</strong>
              </div>
              <div class="meta-card">
                <span>版本</span>
                <strong>{{ selectedDetail.version || 'v1' }}</strong>
              </div>
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
            <div v-if="detailStageHint" class="status-note workflow-note">
              {{ detailStageHint }}
            </div>
          </div>
        </section>

        <section class="content-card detail-section-card">
          <button class="section-heading section-heading-toggle" type="button" @click="toggleDetailSection('metadata')">
            <div>
              <span>检索元数据</span>
              <small>影响关键词召回、过滤和排序</small>
            </div>
            <strong>{{ detailSections.metadata ? '收起' : '展开' }}</strong>
          </button>
          <div v-if="detailSections.metadata" class="detail-section-body">
            <div class="metadata-form-grid">
            <label class="metadata-field">
              <span>标题</span>
              <input v-model.trim="metadataForm.title" type="text" placeholder="用于展示和检索的标题" />
            </label>
            <label class="metadata-field">
              <span>文档类型</span>
              <select v-model="metadataForm.docType">
                <option v-for="option in docTypeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </label>
            <label class="metadata-field">
              <span>适用对象</span>
              <select v-model="metadataForm.audience">
                <option v-for="option in audienceOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </label>
            <label class="metadata-field">
              <span>科室</span>
              <div class="dept-tree-picker">
                <button
                  class="dept-tree-trigger"
                  type="button"
                  :disabled="isDeptTreeLoading"
                  @click="toggleDeptTreePicker"
                >
                  <span>{{ departmentLabel(metadataForm.department) }}</span>
                  <small>{{ isDeptTreeLoading ? '加载中' : '选择科室' }}</small>
                </button>
                <div v-if="isDeptTreePickerOpen" class="dept-tree-popover">
                  <input
                    v-model.trim="deptTreeKeyword"
                    class="dept-tree-search"
                    type="text"
                    placeholder="搜索科室名称或编码"
                  />
                  <div class="dept-tree-options">
                    <button
                      v-for="option in filteredDeptTreeOptions"
                      :key="option.deptCode"
                      :class="['dept-tree-option', { active: normalizeDepartmentCode(metadataForm.department) === option.deptCode }]"
                      type="button"
                      :style="{ '--dept-depth': option.depth }"
                      @click="selectDepartment(option.deptCode)"
                    >
                      <span class="dept-tree-branch" />
                      <span class="dept-tree-option-label">{{ option.name }}</span>
                      <small>{{ option.deptCode }}</small>
                    </button>
                    <div v-if="!filteredDeptTreeOptions.length" class="dept-tree-empty">
                      没有匹配的科室
                    </div>
                  </div>
                </div>
              </div>
            </label>
            <label class="metadata-field">
              <span>医生/专家</span>
              <input v-model.trim="metadataForm.doctorName" type="text" placeholder="医生或专家姓名" />
            </label>
            <label class="metadata-field">
              <span>版本</span>
              <input v-model.trim="metadataForm.version" type="text" placeholder="v1 / 2024版" />
            </label>
            <label class="metadata-field">
              <span>生效时间</span>
              <input v-model="metadataForm.effectiveAt" type="datetime-local" />
            </label>
            <label class="metadata-field">
              <span>来源优先级</span>
              <input v-model.number="metadataForm.sourcePriority" type="number" min="0" max="100" />
            </label>
            <label class="metadata-field metadata-field-wide">
              <span>关键词</span>
              <textarea v-model.trim="metadataForm.keywords" rows="3" placeholder="多个关键词用空格分隔" />
            </label>
          </div>
            <div class="metadata-disease-card">
              <div class="metadata-disease-head">
                <div>
                  <strong>关联疾病</strong>
                  <span>从疾病树中手动勾选适用疾病，可多选。左侧勾选后右侧立即同步，点击这里单独保存。</span>
                </div>
                <div class="metadata-disease-actions">
                  <span class="metadata-disease-count">{{ diseaseSelectorStatusText }}</span>
                  <button
                    class="primary-button slim-button"
                    type="button"
                    :disabled="diseaseMappingSaving || diseaseMappingLoading || !hasDiseaseMappingChanges"
                    @click="saveDiseaseMappings(selectedDetail.hash)"
                  >
                    {{ diseaseMappingSaving ? '保存中...' : '保存关联疾病' }}
                  </button>
                  <button
                    class="secondary-button slim-button"
                    type="button"
                    :disabled="!diseaseMappingSelectedCodes.length"
                    @click="clearSelectedDiseases"
                  >
                    清空已选
                  </button>
                </div>
              </div>
              <div class="metadata-disease-body">
                <div class="metadata-disease-picker">
                  <label class="metadata-field">
                    <span>检索疾病</span>
                    <input v-model.trim="diseaseSelectorKeyword" type="text" placeholder="如 感冒、肝癌、C22、gm" />
                  </label>
                  <div class="metadata-disease-hint">
                    <span>{{ isDiseaseSelectorSearchMode ? '检索树' : '分类树' }}</span>
                    <small>{{ isDiseaseSelectorSearchMode ? '输入后自动过滤并展示命中的疾病树' : '展开分类后勾选疾病，可多选' }}</small>
                  </div>
                  <div class="metadata-disease-tree-shell">
                    <el-tree
                      :key="diseaseSelectorTreeRenderKey"
                      ref="diseaseSelectorTreeRef"
                      class="metadata-disease-tree"
                      :data="diseaseSelectorTreeData"
                      node-key="nodeKey"
                      show-checkbox
                      :props="diseaseSelectorTreeProps"
                      :lazy="!isDiseaseSelectorSearchMode"
                      :load="loadKnowledgeDiseaseTreeNode"
                      :default-expand-all="isDiseaseSelectorSearchMode"
                      :expand-on-click-node="false"
                      :check-on-click-node="true"
                      :empty-text="diseaseSelectorLoading ? '疾病树加载中...' : '暂无疾病数据'"
                      @node-click="handleKnowledgeDiseaseNodeClick"
                      @check="handleKnowledgeDiseaseTreeCheck"
                    >
                      <template #default="{ data }">
                        <div :class="['knowledge-disease-tree-node', data.nodeType === 'CATEGORY' ? 'category' : 'disease']">
                          <div class="knowledge-disease-tree-main">
                            <div class="knowledge-disease-tree-title-row">
                              <strong>{{ data.label }}</strong>
                              <span :class="['knowledge-disease-tree-badge', data.nodeType === 'CATEGORY' ? 'category' : 'disease']">
                                {{ data.nodeType === 'CATEGORY' ? '分类' : '疾病' }}
                              </span>
                            </div>
                            <span v-if="data.nodeType === 'CATEGORY'">{{ data.categoryCode }} · {{ data.count || 0 }} 个疾病</span>
                            <span v-else>{{ data.standardCode || data.conceptCode }}</span>
                          </div>
                        </div>
                      </template>
                    </el-tree>
                  </div>
                </div>

                <div class="metadata-disease-selected">
                  <div class="metadata-disease-selected-head">
                    <strong>已选疾病</strong>
                    <small>{{ hasDiseaseMappingChanges ? '当前选择尚未保存。' : '当前选择已保存。' }}</small>
                  </div>
                  <div v-if="diseaseMappings.length" class="metadata-disease-list">
                    <article
                      v-for="(item, index) in diseaseMappings"
                      :key="item.conceptCode"
                      :class="['metadata-disease-item', { dragging: draggedDiseaseCode === item.conceptCode }]"
                      draggable="true"
                      @dragstart="handleDiseaseDragStart(item.conceptCode)"
                      @dragover.prevent="handleDiseaseDragOver(item.conceptCode)"
                      @drop.prevent="handleDiseaseDrop(item.conceptCode)"
                      @dragend="handleDiseaseDragEnd"
                    >
                      <div>
                        <strong>
                          <span class="metadata-disease-priority">P{{ index + 1 }}</span>
                          {{ item.diseaseTitle || item.standardCode || item.conceptCode }}
                        </strong>
                        <span>{{ item.standardCode || item.conceptCode }}</span>
                      </div>
                      <div class="metadata-disease-item-actions">
                        <span class="metadata-disease-drag-handle">拖动排序</span>
                        <button class="secondary-button slim-button metadata-disease-remove" type="button" @click="removeSelectedDisease(item.conceptCode)">
                          移除
                        </button>
                      </div>
                    </article>
                  </div>
                  <p v-else class="metadata-disease-empty">
                    暂未选择疾病。左侧勾选后，会同步显示在这里。
                  </p>
                </div>
              </div>
            </div>
          </div>
        </section>

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

        <section class="content-card detail-section-card">
          <button class="section-heading section-heading-toggle" type="button" @click="toggleDetailSection('content')">
            <div>
              <span>解析原文</span>
              <small>{{ isEditing ? '编辑模式' : '查看模式' }}</small>
            </div>
            <strong>{{ detailSections.content ? '收起' : '展开' }}</strong>
          </button>
          <div v-if="detailSections.content" class="detail-section-body">
            <p v-if="selectedDetail.statusMessage" class="status-note">{{ selectedDetail.statusMessage }}</p>
            <textarea
              v-if="isEditing"
              v-model="editorContent"
              class="content-editor"
              spellcheck="false"
            />
            <pre v-else class="content-preview">{{ selectedDetail.extractedText || selectedDetail.statusMessage || '暂无内容' }}</pre>
          </div>
        </section>

        <section class="content-card detail-section-card">
          <button class="section-heading section-heading-toggle" type="button" @click="toggleDetailSection('chunks')">
            <div>
              <span>RAG 切分结果</span>
              <small>按需展开查看 chunk 内容</small>
            </div>
            <strong>{{ detailSections.chunks ? '收起' : '展开' }}</strong>
          </button>
          <div v-if="detailSections.chunks" class="detail-section-body">
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
      </section>

      <section v-else class="empty-detail">
        <h3>知识库页已就绪</h3>
        <p>从左侧选择一个知识文件，即可查看原文、删除/编辑能力和切分后的 RAG 数据。</p>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, markRaw, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AdminEntryActionButton from '@/components/AdminEntryActionButton.vue'
import LogoutActionButton from '@/components/LogoutActionButton.vue'
import shulanLogo from '@/assets/shulan-logo.png'
import { apiClient, isAdmin, logout } from '@/lib/auth'
import { audienceLabel, audienceOptions, docTypeLabel, docTypeOptions, knowledgeStage, stageLabel } from '@/lib/knowledgeUi'
import { confirmDanger } from '@/lib/confirmDialog'

const router = useRouter()
const route = useRoute()
const uploadInputRef = ref()
const files = ref([])
const selectedBatchHashes = ref([])
const searchKeyword = ref('')
const stageFilter = ref('ALL')
const selectedHash = ref('')
const selectedDetail = ref(null)
const editorContent = ref('')
const deptTree = ref([])
const deptTreeKeyword = ref('')
const isDeptTreePickerOpen = ref(false)
const isDeptTreeLoading = ref(false)
const isLoading = ref(false)
const isUploading = ref(false)
const isSaving = ref(false)
const isDeleting = ref(false)
const isEditing = ref(false)
const isPollingProcessing = ref(false)
const diseaseMappingLoading = ref(false)
const diseaseMappingSaving = ref(false)
const diseaseMappings = ref([])
const diseaseMappingSelectedCodes = ref([])
const savedDiseaseMappingCodes = ref([])
const diseaseSelectorTreeRef = ref(null)
const diseaseSelectorKeyword = ref('')
const diseaseSelectorLoading = ref(false)
const diseaseSelectorSearchLoading = ref(false)
const diseaseSelectorSearchPerformed = ref(false)
const diseaseSelectorTreeVersion = ref(0)
const diseaseSelectorCategories = shallowRef([])
const diseaseSelectorSearchIndex = shallowRef([])
const diseaseSelectorSearchResults = shallowRef([])
const detailSections = reactive({
  overview: true,
  metadata: true,
  content: false,
  chunks: false,
})
let processingPollTimer = null
let diseaseSelectorSearchTimer = null
const diseaseSelectorCategoryChildrenCache = new Map()
const GENERAL_DEPT_CODE = 'GENERAL'
const GENERAL_DEPT_LABEL = '通用'
const diseaseSelectorTreeProps = {
  label: 'label',
  children: 'children',
  isLeaf: 'leaf',
  disabled: 'disabled',
}

const metadataForm = reactive({
  docType: '',
  department: '',
  audience: 'BOTH',
  version: 'v1',
  effectiveAt: '',
  title: '',
  doctorName: '',
  sourcePriority: 50,
  keywords: '',
})

const stageFilterOptions = [
  { value: 'ALL', label: '全部' },
  { value: 'processing', label: '处理中' },
  { value: 'ready', label: '待发布' },
  { value: 'online', label: '已上线' },
  { value: 'archived', label: '已归档' },
]

const filteredFiles = computed(() => {
  const keyword = searchKeyword.value.toLowerCase()
  return files.value.filter((file) => {
    const stageMatched =
      stageFilter.value === 'ALL' ||
      (stageFilter.value === 'archived' && file.processingStatus === 'ARCHIVED') ||
      (stageFilter.value === 'online' && file.processingStatus === 'PUBLISHED') ||
      (stageFilter.value !== 'archived' && stageFilter.value !== 'online' && knowledgeStage(file.processingStatus) === stageFilter.value)
    if (!stageMatched) {
      return false
    }
    if (!keyword) {
      return true
    }
    return [file.originalFilename, file.fileName, file.hash, file.parser]
      .filter(Boolean)
      .some((value) => value.toLowerCase().includes(keyword))
  })
})

const deptNameByCode = computed(() => {
  const mapping = new Map()
  mapping.set(GENERAL_DEPT_CODE, GENERAL_DEPT_LABEL)
  const visit = (nodes) => {
    const safeNodes = Array.isArray(nodes) ? nodes : []
    safeNodes.forEach((node) => {
      const normalizedCode = normalizeDepartmentCode(node?.deptCode || node?.deptName)
      if (normalizedCode && normalizedCode !== GENERAL_DEPT_CODE) {
        mapping.set(normalizedCode, node.deptName || normalizedCode)
      }
      visit(node?.children || [])
    })
  }
  visit(deptTree.value)
  return mapping
})

const deptTreeOptions = computed(() => {
  const options = [{ deptCode: GENERAL_DEPT_CODE, name: GENERAL_DEPT_LABEL, depth: 0 }]
  const seen = new Set([GENERAL_DEPT_CODE])
  const visit = (nodes, depth = 0) => {
    const safeNodes = Array.isArray(nodes) ? nodes : []
    safeNodes.forEach((node) => {
      const normalizedCode = normalizeDepartmentCode(node?.deptCode || node?.deptName)
      if (normalizedCode && !seen.has(normalizedCode)) {
        seen.add(normalizedCode)
        options.push({
          deptCode: normalizedCode,
          name: node.deptName || normalizedCode,
          depth,
        })
      }
      visit(node?.children || [], depth + 1)
    })
  }
  visit(deptTree.value)
  return options
})

const filteredDeptTreeOptions = computed(() => {
  const keyword = deptTreeKeyword.value.toLowerCase()
  if (!keyword) {
    return deptTreeOptions.value
  }
  return deptTreeOptions.value.filter((option) =>
    [option.name, option.deptCode]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword))
  )
})

const normalizedDiseaseSelectorKeyword = computed(() => diseaseSelectorKeyword.value.trim().toLowerCase())
const isDiseaseSelectorSearchMode = computed(() => Boolean(normalizedDiseaseSelectorKeyword.value))
const diseaseSelectorSearchTreeData = computed(() => {
  if (!isDiseaseSelectorSearchMode.value) {
    return []
  }
  const categoryOrder = new Map(diseaseSelectorCategories.value.map((item, index) => [item.categoryCode, index]))
  const groups = new Map()
  diseaseSelectorSearchResults.value.forEach((item) => {
    const categoryCode = item.categoryCode || 'UNCATEGORIZED'
    if (!groups.has(categoryCode)) {
      groups.set(categoryCode, {
        nodeType: 'CATEGORY',
        nodeKey: `SEARCH_CATEGORY:${categoryCode}`,
        label: item.categoryName || '未分类',
        categoryCode,
        count: 0,
        disabled: true,
        leaf: false,
        children: [],
      })
    }
    groups.get(categoryCode).children.push({
      ...item,
      nodeKey: item.conceptCode,
      nodeType: 'DISEASE',
      disabled: false,
      leaf: true,
    })
  })
  return Array.from(groups.values())
    .sort((left, right) => (categoryOrder.get(left.categoryCode) ?? 9999) - (categoryOrder.get(right.categoryCode) ?? 9999))
    .map((group) => ({
      ...group,
      count: group.children.length,
    }))
})
const diseaseSelectorTreeData = computed(() => (
  isDiseaseSelectorSearchMode.value ? diseaseSelectorSearchTreeData.value : diseaseSelectorCategories.value
))
const diseaseSelectorTreeRenderKey = computed(() => (
  `${isDiseaseSelectorSearchMode.value ? 'search' : 'browse'}-${diseaseSelectorTreeVersion.value}`
))
const diseaseSelectorStatusText = computed(() => {
  if (!diseaseSelectorKeyword.value.trim()) {
    return `已选 ${diseaseMappingSelectedCodes.value.length} 项`
  }
  if (diseaseSelectorSearchLoading.value) {
    return '检索中'
  }
  return `命中 ${diseaseSelectorSearchResults.value.length} 项`
})
const hasDiseaseMappingChanges = computed(() => (
  normalizeDiseaseConceptCodes(diseaseMappingSelectedCodes.value).join('|')
  !== normalizeDiseaseConceptCodes(savedDiseaseMappingCodes.value).join('|')
))

const stageCounts = computed(() => {
  const counts = {
    ALL: files.value.length,
    processing: 0,
    ready: 0,
    online: 0,
    archived: 0,
  }
  files.value.forEach((file) => {
    if (file.processingStatus === 'ARCHIVED') {
      counts.archived += 1
      return
    }
    if (file.processingStatus === 'PUBLISHED') {
      counts.online += 1
      return
    }
    const stage = knowledgeStage(file.processingStatus)
    if (stage === 'processing') {
      counts.processing += 1
    } else if (stage === 'ready') {
      counts.ready += 1
    }
  })
  return counts
})

const fileCountText = computed(() => `${files.value.length} 个文件`)
const selectedBatchFiles = computed(() => files.value.filter((file) => selectedBatchHashes.value.includes(file.hash)))
const batchPublishableCount = computed(() => selectedBatchFiles.value.filter((file) => ['READY', 'ARCHIVED'].includes(file.processingStatus)).length)
const batchArchivableCount = computed(() => selectedBatchFiles.value.filter((file) => file.processingStatus === 'PUBLISHED').length)
// 批量操作只允许作用在稳定态文件上，避免发布、归档和后台处理状态交错。
const hasBatchProcessingFiles = computed(() =>
  selectedBatchFiles.value.some((file) => ['DRAFT', 'PROCESSING'].includes(file.processingStatus))
)
const isDetailProcessing = computed(() =>
  ['DRAFT', 'PROCESSING'].includes(selectedDetail.value?.processingStatus)
)
const canSaveMetadata = computed(() =>
  Boolean(selectedDetail.value && ['READY', 'PUBLISHED'].includes(selectedDetail.value?.processingStatus))
)
const isDetailActionLocked = computed(() =>
  isSaving.value || isPollingProcessing.value || isDetailProcessing.value
)
// 页面上只展示三段式业务状态，内部英文状态继续保留给后端和接口使用。
// 这样管理员关注的是“处理中 / 待发布 / 已上线(已归档)”，而不是一堆技术态枚举值。
const currentKnowledgeStage = computed(() => knowledgeStage(selectedDetail.value?.processingStatus))
const detailStatusText = computed(() => {
  if (!selectedDetail.value) {
    return '待选择'
  }
  if (currentKnowledgeStage.value === 'processing') {
    return '处理中'
  }
  if (currentKnowledgeStage.value === 'ready') {
    return '待发布'
  }
  return selectedDetail.value.processingStatus === 'ARCHIVED' ? '已归档' : '已上线'
})
const detailStageHint = computed(() => {
  const status = selectedDetail.value?.processingStatus
  if (!selectedDetail.value) {
    return ''
  }
  if (status === 'READY') {
    return '当前文件已经完成解析和 embedding，下一步点击“发布上线”后，才会参与问答检索。'
  }
  if (status === 'PUBLISHED') {
    return '当前文件已经上线，问答检索会优先引用这份知识内容。'
  }
  if (status === 'ARCHIVED') {
    return '当前文件已归档下线。如需恢复检索，直接点击“重新上线”，不需要重新构建 embedding。'
  }
  if (status === 'PENDING_SYNC') {
    return '当前节点不构建 embedding，请在本地构建环境完成索引后再同步到线上节点。'
  }
  if (status === 'FAILED') {
    return selectedDetail.value.statusMessage || '当前文件处理失败，请修正后重新处理。'
  }
  return selectedDetail.value.statusMessage || '当前文件正在处理中，完成前不会进入问答检索。'
})

onMounted(async () => {
  await Promise.all([loadDeptTree(), loadDiseaseSelectorCategories()])
  await loadFiles(route.query.hash || '')
})

watch(
  () => route.query.hash,
  async (hash) => {
    if (hash && hash !== selectedHash.value) {
      await selectFile(hash)
    }
  }
)

onBeforeUnmount(() => {
  stopProcessingPolling()
  if (diseaseSelectorSearchTimer) {
    window.clearTimeout(diseaseSelectorSearchTimer)
    diseaseSelectorSearchTimer = null
  }
})

watch(
  () => detailSections.metadata,
  (opened) => {
    if (!opened) {
      closeDeptTreePicker()
    }
  }
)

watch(diseaseSelectorKeyword, (value) => {
  if (diseaseSelectorSearchTimer) {
    window.clearTimeout(diseaseSelectorSearchTimer)
  }
  const keyword = value.trim()
  if (!keyword) {
    diseaseSelectorSearchPerformed.value = false
    diseaseSelectorSearchResults.value = []
    diseaseSelectorTreeVersion.value += 1
    nextTick(() => {
      syncDiseaseSelectorCheckedState()
    })
    return
  }
  diseaseSelectorSearchTimer = window.setTimeout(() => {
    void searchDiseaseSelectorTree()
  }, 220)
})

const openUpload = () => {
  uploadInputRef.value?.click()
}

const backToChat = () => {
  router.push('/')
}

const openAdminConsole = () => {
  router.push('/admin')
}

const handleLogout = async () => {
  await logout()
  await router.replace('/login')
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

const normalizeDepartmentCode = (value) => {
  if (!value || value === GENERAL_DEPT_LABEL) {
    return GENERAL_DEPT_CODE
  }
  return value
}

const departmentLabel = (value) => {
  const normalized = normalizeDepartmentCode(value)
  if (normalized === GENERAL_DEPT_CODE) {
    return GENERAL_DEPT_LABEL
  }
  return deptNameByCode.value.get(normalized) || normalized
}

const toggleDeptTreePicker = () => {
  isDeptTreePickerOpen.value = !isDeptTreePickerOpen.value
  if (isDeptTreePickerOpen.value) {
    deptTreeKeyword.value = ''
  }
}

const closeDeptTreePicker = () => {
  isDeptTreePickerOpen.value = false
  deptTreeKeyword.value = ''
}

const selectDepartment = (deptCode) => {
  metadataForm.department = normalizeDepartmentCode(deptCode)
  closeDeptTreePicker()
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

const applyFileSnapshot = (detail) => {
  if (!detail?.hash) {
    return
  }
  // 操作成功后先本地回写一份快照，目的是让左侧列表和快速筛选立刻看到状态变化。
  // 否则用户会先看到“按钮提示成功”，但左侧状态还停在旧值，观感会像接口没生效。
  files.value = files.value.map((file) => {
    if (file.hash !== detail.hash) {
      return file
    }
    return {
      ...file,
      processingStatus: detail.processingStatus ?? file.processingStatus,
      statusMessage: detail.statusMessage ?? file.statusMessage,
      progressPercent: typeof detail.progressPercent === 'number' ? detail.progressPercent : file.progressPercent,
      currentBatch: typeof detail.currentBatch === 'number' ? detail.currentBatch : file.currentBatch,
      totalBatches: typeof detail.totalBatches === 'number' ? detail.totalBatches : file.totalBatches,
      docType: detail.docType ?? file.docType,
      department: detail.department ?? file.department,
      audience: detail.audience ?? file.audience,
      version: detail.version ?? file.version,
      effectiveAt: detail.effectiveAt ?? file.effectiveAt,
      title: detail.title ?? file.title,
      doctorName: detail.doctorName ?? file.doctorName,
      sourcePriority: detail.sourcePriority ?? file.sourcePriority,
      keywords: detail.keywords ?? file.keywords,
    }
  })
}

const clearDeletedFileSnapshot = (deletedHash) => {
  if (!deletedHash) {
    return ''
  }
  const nextFiles = files.value.filter((file) => file.hash !== deletedHash)
  files.value = nextFiles
  selectedBatchHashes.value = selectedBatchHashes.value.filter((hash) => hash !== deletedHash)
  if (selectedHash.value === deletedHash) {
    selectedHash.value = ''
    selectedDetail.value = null
    editorContent.value = ''
    diseaseMappings.value = []
    diseaseMappingSelectedCodes.value = []
    savedDiseaseMappingCodes.value = []
    isEditing.value = false
    closeDeptTreePicker()
    syncMetadataForm(null)
    void syncDiseaseSelectorCheckedState()
    stopProcessingPolling()
    if (route.query.hash) {
      router.replace({ path: '/knowledge', query: {} })
    }
  }
  const candidate = nextFiles.find((file) => filteredFiles.value.some((visibleFile) => visibleFile.hash === file.hash))
    || nextFiles[0]
  return candidate?.hash || ''
}

const stopProcessingPolling = () => {
  isPollingProcessing.value = false
  if (processingPollTimer) {
    window.clearTimeout(processingPollTimer)
    processingPollTimer = null
  }
}

const loadDeptTree = async () => {
  isDeptTreeLoading.value = true
  try {
    const { data } = await apiClient.get('/api/aimed/dept/tree')
    deptTree.value = Array.isArray(data) ? data : []
  } catch (error) {
    console.error('加载科室树失败:', error)
    deptTree.value = []
    ElMessage.error(resolveAxiosErrorMessage(error, '科室树加载失败，文档科室将只能选择通用。'))
  } finally {
    isDeptTreeLoading.value = false
  }
}

const startProcessingPolling = (hash) => {
  stopProcessingPolling()
  if (!hash) {
    return
  }
  // 知识库处理链是典型的后台任务，列表状态并不需要毫秒级实时推送。
  // 这里直接用低频轮询做主链，避免 ws 握手失败后反复重连刷日志，同时也更容易稳定收尾。
  isPollingProcessing.value = true
  const poll = async () => {
    try {
      const { data } = await apiClient.get(`/api/aimed/knowledge/files/${hash}`)
      selectedHash.value = data.hash
      selectedDetail.value = data
      editorContent.value = data?.extractedText || ''
      syncMetadataForm(data)
      closeDeptTreePicker()
      files.value = files.value.map((file) => (file.hash === data.hash ? { ...file, ...data } : file))
      if (!['DRAFT', 'PROCESSING'].includes(data.processingStatus)) {
        stopProcessingPolling()
        await loadFiles(data.hash)
        return
      }
    } catch (error) {
      console.error('轮询知识文件状态失败:', error)
      ElMessage.error(resolveAxiosErrorMessage(error, '知识文件状态刷新失败，请稍后手动刷新。'))
      stopProcessingPolling()
      return
    }
    processingPollTimer = window.setTimeout(poll, 2000)
  }
  processingPollTimer = window.setTimeout(poll, 1500)
}

const loadFiles = async (preferredHash = '') => {
  isLoading.value = true
  try {
    const { data } = await apiClient.get('/api/aimed/knowledge/files')
    files.value = Array.isArray(data) ? data : []

    const availableHash = preferredHash && files.value.some((file) => file.hash === preferredHash)
      ? preferredHash
      : files.value[0]?.hash

    selectedBatchHashes.value = selectedBatchHashes.value.filter((hash) => files.value.some((file) => file.hash === hash))

    if (availableHash) {
      await selectFile(availableHash)
    } else {
      selectedHash.value = ''
      selectedDetail.value = null
      diseaseMappings.value = []
      diseaseMappingSelectedCodes.value = []
      savedDiseaseMappingCodes.value = []
      isEditing.value = false
      closeDeptTreePicker()
      if (route.query.hash) {
        router.replace({ path: '/knowledge', query: {} })
      }
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
    const { data } = await apiClient.get(`/api/aimed/knowledge/files/${hash}`)
    selectedHash.value = hash
    selectedDetail.value = data
    editorContent.value = data?.extractedText || ''
    syncMetadataForm(data)
    closeDeptTreePicker()
    isEditing.value = false
    void loadDiseaseMappings(data.hash)
    detailSections.overview = true
    // 详情页一旦点到“处理中”的文件，就主动进入锁定态。
    // 这样页面不会出现“看起来还能点发布/归档，点了却报错”的反直觉体验。
    if (['DRAFT', 'PROCESSING'].includes(data.processingStatus)) {
      startProcessingPolling(data.hash)
    } else {
      stopProcessingPolling()
    }
    if (route.query.hash !== hash) {
      router.replace({ path: '/knowledge', query: { hash } })
    }
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
    const { data } = await apiClient.post('/api/aimed/knowledge/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })

    const acceptedItem = (data.items || []).find((item) => item.status === 'QUEUED')
    await loadFiles(acceptedItem?.hash)
    ElMessage.success(`文件已上传：后台处理中 ${data.accepted || 0} 个，跳过 ${data.skipped || 0} 个。处理完成后请在知识库页点击“发布上线”。`)
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
  syncMetadataForm(selectedDetail.value)
  closeDeptTreePicker()
  detailSections.content = true
  isEditing.value = true
}

const cancelEdit = () => {
  editorContent.value = selectedDetail.value?.extractedText || ''
  syncMetadataForm(selectedDetail.value)
  closeDeptTreePicker()
  isEditing.value = false
}

const buildKnowledgeUpdatePayload = (contentOverride) => ({
  content: contentOverride,
  docType: metadataForm.docType || undefined,
  department: normalizeDepartmentCode(metadataForm.department),
  audience: metadataForm.audience || undefined,
  version: metadataForm.version || undefined,
  effectiveAt: metadataForm.effectiveAt || undefined,
  title: metadataForm.title || undefined,
  doctorName: metadataForm.doctorName || undefined,
  sourcePriority: Number.isFinite(Number(metadataForm.sourcePriority)) ? Number(metadataForm.sourcePriority) : undefined,
  keywords: metadataForm.keywords || undefined,
})

const saveEdit = async () => {
  if (!selectedDetail.value) {
    return
  }

  isSaving.value = true
  try {
    const { data } = await apiClient.put(
      `/api/aimed/knowledge/files/${selectedDetail.value.hash}`,
      buildKnowledgeUpdatePayload(editorContent.value)
    )
    selectedHash.value = data.hash
    selectedDetail.value = data
    editorContent.value = data.extractedText || ''
    syncMetadataForm(data)
    isEditing.value = false
    await loadFiles(data.hash)
    if (['DRAFT', 'PROCESSING'].includes(data.processingStatus)) {
      startProcessingPolling(data.hash)
    }
    ElMessage.success('知识文件已保存，并完成向量重建。')
  } catch (error) {
    console.error('更新知识文件失败:', error)
    ElMessage.error(resolveAxiosErrorMessage(error, '知识文件保存失败。'))
  } finally {
    isSaving.value = false
  }
}

const saveMetadata = async () => {
  if (!selectedDetail.value) {
    return
  }
  isSaving.value = true
  try {
    const { data } = await apiClient.put(
      `/api/aimed/knowledge/files/${selectedDetail.value.hash}`,
      buildKnowledgeUpdatePayload(undefined)
    )
    selectedHash.value = data.hash
    selectedDetail.value = data
    editorContent.value = data.extractedText || ''
    syncMetadataForm(data)
    applyFileSnapshot(data)
    await loadFiles(data.hash)
    ElMessage.success('文档元数据已更新。')
  } catch (error) {
    console.error('更新知识元数据失败:', error)
    ElMessage.error(resolveAxiosErrorMessage(error, '知识元数据保存失败。'))
  } finally {
    isSaving.value = false
  }
}

const publishFile = async () => {
  if (!selectedDetail.value) {
    return
  }

  try {
    const { data } = await apiClient.post(`/api/aimed/knowledge/files/${selectedDetail.value.hash}/publish`)
    selectedDetail.value = data
    applyFileSnapshot(data)
    await loadFiles(data.hash)
    ElMessage.success(data.processingStatus === 'PUBLISHED' && data.statusMessage?.includes('重新上线') ? '知识文件已重新上线。' : '知识文件已发布。')
  } catch (error) {
    console.error('发布知识文件失败:', error)
    ElMessage.error(resolveAxiosErrorMessage(error, '发布知识文件失败。'))
  }
}

const archiveFile = async () => {
  if (!selectedDetail.value) {
    return
  }

  try {
    const { data } = await apiClient.post(`/api/aimed/knowledge/files/${selectedDetail.value.hash}/archive`)
    selectedDetail.value = data
    applyFileSnapshot(data)
    await loadFiles(data.hash)
    ElMessage.success('知识文件已归档。')
  } catch (error) {
    console.error('归档知识文件失败:', error)
    ElMessage.error(resolveAxiosErrorMessage(error, '归档知识文件失败。'))
  }
}

const reprocessFile = async () => {
  if (!selectedDetail.value) {
    return
  }

  try {
    const { data } = await apiClient.post(`/api/aimed/knowledge/files/${selectedDetail.value.hash}/reprocess`)
    selectedDetail.value = data
    applyFileSnapshot(data)
    await loadFiles(data.hash)
    ElMessage.success('知识文件已重新加入处理队列。')
  } catch (error) {
    console.error('重新处理知识文件失败:', error)
    ElMessage.error(resolveAxiosErrorMessage(error, '重新处理知识文件失败。'))
  }
}

const deleteFile = async () => {
  if (!selectedDetail.value) {
    return
  }

  try {
    await confirmDanger(
      `确认删除知识文件「${selectedDetail.value.originalFilename}」吗？这会同时移除它的 RAG 切分数据。`,
      '删除知识文件'
    )
  } catch {
    return
  }

  isDeleting.value = true
  try {
    const currentHash = selectedDetail.value.hash
    await apiClient.delete(`/api/aimed/knowledge/files/${currentHash}`)
    const nextHash = clearDeletedFileSnapshot(currentHash)
    await loadFiles(nextHash)
    ElMessage.success('知识文件已删除。')
  } catch (error) {
    console.error('删除知识文件失败:', error)
    ElMessage.error(resolveAxiosErrorMessage(error, '删除知识文件失败。'))
  } finally {
    isDeleting.value = false
  }
}

const toggleBatchHash = (hash, checked) => {
  const next = new Set(selectedBatchHashes.value)
  if (checked) {
    next.add(hash)
  } else {
    next.delete(hash)
  }
  selectedBatchHashes.value = Array.from(next)
}

const toggleSelectAllVisible = () => {
  const visibleHashes = filteredFiles.value.map((file) => file.hash)
  const everySelected = visibleHashes.length > 0 && visibleHashes.every((hash) => selectedBatchHashes.value.includes(hash))
  selectedBatchHashes.value = everySelected ? [] : visibleHashes
}

const runBatchAction = async (action) => {
  // 批量上线支持两类文件：
  // - READY：第一次正式上线
  // - ARCHIVED：不重建 embedding，直接重新上线
  const eligible = selectedBatchFiles.value
    .filter((file) => (action === 'publish' ? ['READY', 'ARCHIVED'].includes(file.processingStatus) : file.processingStatus === 'PUBLISHED'))
    .map((file) => file.hash)
  if (!eligible.length) {
    ElMessage.warning(action === 'publish' ? '请选择待发布或已归档的文件。' : '请选择已发布的文件。')
    return
  }
  try {
    const { data } = await apiClient.post(`/api/aimed/knowledge/files/batch/${action}`, { hashes: eligible })
    if (Array.isArray(data)) {
      data.forEach((item) => applyFileSnapshot(item))
    }
    await loadFiles(selectedHash.value)
    ElMessage.success(action === 'publish' ? `已批量上线 ${eligible.length} 个文件。` : `已批量归档 ${eligible.length} 个文件。`)
  } catch (error) {
    console.error('批量知识操作失败:', error)
    ElMessage.error(resolveAxiosErrorMessage(error, '批量知识操作失败。'))
  }
}

const syncMetadataForm = (detail) => {
  metadataForm.docType = detail?.docType || 'HOSPITAL_OVERVIEW'
  metadataForm.department = normalizeDepartmentCode(detail?.department)
  metadataForm.audience = detail?.audience || 'BOTH'
  metadataForm.version = detail?.version || 'v1'
  metadataForm.effectiveAt = toLocalDateTimeInput(detail?.effectiveAt)
  metadataForm.title = detail?.title || detail?.originalFilename || ''
  metadataForm.doctorName = detail?.doctorName || ''
  metadataForm.sourcePriority = Number.isFinite(Number(detail?.sourcePriority)) ? Number(detail.sourcePriority) : 50
  metadataForm.keywords = detail?.keywords || ''
}

const loadDiseaseSelectorCategories = async () => {
  diseaseSelectorLoading.value = true
  try {
    clearDiseaseSelectorCaches()
    const categories = await loadMedicalStandardJson('disease-categories.json')
    diseaseSelectorCategories.value = markRaw((Array.isArray(categories) ? categories : []).map((item) => ({
      ...item,
      nodeKey: `CATEGORY:${item.categoryCode}`,
      nodeType: 'CATEGORY',
      disabled: true,
      leaf: false,
    })))
    await nextTick()
    syncDiseaseSelectorCheckedState()
  } catch (error) {
    console.error('加载疾病树失败:', error)
    diseaseSelectorCategories.value = []
    ElMessage.error(resolveAxiosErrorMessage(error, '疾病树加载失败。'))
  } finally {
    diseaseSelectorLoading.value = false
  }
}

const loadMedicalStandardJson = async (path) => {
  const base = import.meta.env.BASE_URL.endsWith('/') ? import.meta.env.BASE_URL : `${import.meta.env.BASE_URL}/`
  const response = await fetch(`${base}medical-standards/${path}`, { cache: 'force-cache' })
  if (!response.ok) {
    throw new Error(`疾病静态数据加载失败: ${response.status}`)
  }
  return response.json()
}

const clearDiseaseSelectorCaches = () => {
  diseaseSelectorCategoryChildrenCache.clear()
  diseaseSelectorSearchIndex.value = []
  diseaseSelectorSearchResults.value = []
  diseaseSelectorSearchPerformed.value = false
}

const sanitizeDiseaseCategoryCode = (value) => String(value || '').replace(/[^A-Za-z0-9_-]+/g, '_')

const buildDiseaseSelectorSearchText = (item) => [
  item.label,
  item.diseaseName,
  item.englishName,
  item.standardCode,
  item.conceptCode,
  item.categoryCode,
  item.categoryName,
  item.initials,
  ...(item.aliases || []),
].filter(Boolean).join(' ').toLowerCase()

const enrichDiseaseSelectorNode = (item) => ({
  ...item,
  nodeKey: item.conceptCode,
  nodeType: 'DISEASE',
  disabled: false,
  leaf: true,
  searchText: buildDiseaseSelectorSearchText(item),
})

const loadDiseaseSelectorCategoryChildren = async (categoryCode) => {
  if (!categoryCode) {
    return []
  }
  if (diseaseSelectorCategoryChildrenCache.has(categoryCode)) {
    return diseaseSelectorCategoryChildrenCache.get(categoryCode)
  }
  const children = await loadMedicalStandardJson(`categories/${sanitizeDiseaseCategoryCode(categoryCode)}.json`)
  const safeChildren = markRaw((Array.isArray(children) ? children : []).map(enrichDiseaseSelectorNode))
  diseaseSelectorCategoryChildrenCache.set(categoryCode, safeChildren)
  return safeChildren
}

const loadDiseaseSelectorSearchIndex = async () => {
  if (diseaseSelectorSearchIndex.value.length) {
    return diseaseSelectorSearchIndex.value
  }
  const groups = await Promise.all(diseaseSelectorCategories.value.map((category) => loadDiseaseSelectorCategoryChildren(category.categoryCode)))
  diseaseSelectorSearchIndex.value = markRaw(groups.flat())
  return diseaseSelectorSearchIndex.value
}

const searchDiseaseSelectorTree = async () => {
  if (!diseaseSelectorCategories.value.length) {
    return
  }
  const tokens = normalizedDiseaseSelectorKeyword.value
    .split(/[\s,，;；、]+/)
    .map((item) => item.trim())
    .filter(Boolean)
  if (!tokens.length) {
    diseaseSelectorSearchPerformed.value = false
    diseaseSelectorSearchResults.value = []
    diseaseSelectorTreeVersion.value += 1
    await nextTick()
    syncDiseaseSelectorCheckedState()
    return
  }
  diseaseSelectorSearchLoading.value = true
  diseaseSelectorSearchPerformed.value = true
  try {
    const index = await loadDiseaseSelectorSearchIndex()
    diseaseSelectorSearchResults.value = markRaw(index
      .filter((item) => tokens.every((token) => item.searchText.includes(token)))
      .slice(0, 180))
    diseaseSelectorTreeVersion.value += 1
    await nextTick()
    syncDiseaseSelectorCheckedState()
  } catch (error) {
    console.error('检索疾病树失败:', error)
    diseaseSelectorSearchResults.value = []
    ElMessage.error(resolveAxiosErrorMessage(error, '疾病检索失败。'))
  } finally {
    diseaseSelectorSearchLoading.value = false
  }
}

const loadDiseaseMappings = async (hash) => {
  if (!hash) {
    diseaseMappings.value = []
    diseaseMappingSelectedCodes.value = []
    savedDiseaseMappingCodes.value = []
    return
  }
  const requestedHash = hash
  diseaseMappingLoading.value = true
  try {
    const { data } = await apiClient.get('/api/aimed/admin/medical-standards/knowledge/mappings', {
      params: { hash: requestedHash },
    })
    if (selectedHash.value === requestedHash) {
      diseaseMappings.value = Array.isArray(data) ? data : []
      diseaseMappingSelectedCodes.value = diseaseMappings.value.map((item) => item.conceptCode).filter(Boolean)
      savedDiseaseMappingCodes.value = [...diseaseMappingSelectedCodes.value]
      await nextTick()
      syncDiseaseSelectorCheckedState()
    }
  } catch (error) {
    console.error('加载文档关联疾病失败:', error)
    if (selectedHash.value === requestedHash) {
      ElMessage.error(resolveAxiosErrorMessage(error, '文档关联疾病加载失败。'))
    }
  } finally {
    if (selectedHash.value === requestedHash) {
      diseaseMappingLoading.value = false
    }
  }
}

const saveDiseaseMappings = async (hash) => {
  if (!hash) {
    return
  }
  diseaseMappingSaving.value = true
  try {
    const { data } = await apiClient.put('/api/aimed/admin/medical-standards/knowledge/mappings', {
      hash,
      conceptCodes: diseaseMappingSelectedCodes.value,
    })
    diseaseMappings.value = Array.isArray(data) ? data : []
    diseaseMappingSelectedCodes.value = diseaseMappings.value.map((item) => item.conceptCode).filter(Boolean)
    savedDiseaseMappingCodes.value = [...diseaseMappingSelectedCodes.value]
    await nextTick()
    syncDiseaseSelectorCheckedState()
    ElMessage.success('关联疾病已保存。')
  } catch (error) {
    console.error('保存文档关联疾病失败:', error)
    ElMessage.error(resolveAxiosErrorMessage(error, '关联疾病保存失败，请重试。'))
  } finally {
    diseaseMappingSaving.value = false
  }
}

const loadKnowledgeDiseaseTreeNode = async (node, resolve) => {
  if (node.level === 0) {
    resolve(diseaseSelectorCategories.value)
    return
  }
  const item = node.data
  const children = item?.nodeType === 'CATEGORY' ? await loadDiseaseSelectorCategoryChildren(item.categoryCode) : []
  resolve(children)
  nextTick(() => {
    syncDiseaseSelectorCheckedState()
  })
}

const handleKnowledgeDiseaseNodeClick = async (item, node) => {
  if (item?.nodeType === 'CATEGORY') {
    if (isDiseaseSelectorSearchMode.value) {
      node.expanded ? node.collapse() : node.expand()
      return
    }
    if (node.expanded) {
      node.collapse()
      return
    }
    node.expand(() => {
      nextTick(() => {
        if (!node.expanded) {
          node.expanded = true
        }
        syncDiseaseSelectorCheckedState()
      })
    }, true)
    return
  }
  if (item?.nodeType !== 'DISEASE') {
    return
  }
  const tree = diseaseSelectorTreeRef.value
  if (!tree) {
    return
  }
  const checkedKeys = new Set(tree.getCheckedKeys(false))
  const nextChecked = !checkedKeys.has(item.conceptCode)
  tree.setChecked(item, nextChecked, false)
  handleKnowledgeDiseaseTreeCheck()
}

const handleKnowledgeDiseaseTreeCheck = () => {
  const tree = diseaseSelectorTreeRef.value
  if (!tree) {
    return
  }
  const visibleDiseaseNodes = flattenDiseaseSelectorNodes(diseaseSelectorTreeData.value)
  const visibleCodes = new Set(visibleDiseaseNodes.map((item) => item.conceptCode).filter(Boolean))
  const checkedNodes = tree.getCheckedNodes(false, true).filter((item) => item?.nodeType === 'DISEASE')
  const existingByCode = new Map(diseaseMappings.value.map((item) => [item.conceptCode, item]))
  const retainedMappings = diseaseMappings.value.filter((item) => !visibleCodes.has(item.conceptCode))
  const visibleMappings = checkedNodes.map((item) => ({
    id: existingByCode.get(item.conceptCode)?.id || null,
    knowledgeHash: selectedDetail.value?.hash || '',
    conceptCode: item.conceptCode,
    standardCode: item.standardCode || existingByCode.get(item.conceptCode)?.standardCode || null,
    diseaseTitle: item.diseaseName || item.label || existingByCode.get(item.conceptCode)?.diseaseTitle || null,
    matchSource: existingByCode.get(item.conceptCode)?.matchSource || 'MANUAL',
    confidence: existingByCode.get(item.conceptCode)?.confidence ?? 1,
  }))
  diseaseMappings.value = [...retainedMappings, ...visibleMappings]
  diseaseMappingSelectedCodes.value = diseaseMappings.value.map((item) => item.conceptCode).filter(Boolean)
}

const syncDiseaseSelectorCheckedState = () => {
  const tree = diseaseSelectorTreeRef.value
  if (!tree) {
    return
  }
  tree.setCheckedKeys(diseaseMappingSelectedCodes.value, false)
}

const clearSelectedDiseases = () => {
  diseaseMappings.value = []
  diseaseMappingSelectedCodes.value = []
  syncDiseaseSelectorCheckedState()
}

const removeSelectedDisease = (conceptCode) => {
  if (!conceptCode) {
    return
  }
  diseaseMappingSelectedCodes.value = diseaseMappingSelectedCodes.value.filter((code) => code !== conceptCode)
  diseaseMappings.value = diseaseMappings.value.filter((item) => item.conceptCode !== conceptCode)
  syncDiseaseSelectorCheckedState()
}

const handleDiseaseDragStart = (conceptCode) => {
  draggedDiseaseCode.value = conceptCode || ''
}

const handleDiseaseDragOver = (conceptCode) => {
  if (!draggedDiseaseCode.value || draggedDiseaseCode.value === conceptCode) {
    return
  }
}

const handleDiseaseDrop = (targetConceptCode) => {
  const sourceConceptCode = draggedDiseaseCode.value
  if (!sourceConceptCode || !targetConceptCode || sourceConceptCode === targetConceptCode) {
    draggedDiseaseCode.value = ''
    return
  }
  const nextMappings = [...diseaseMappings.value]
  const sourceIndex = nextMappings.findIndex((item) => item.conceptCode === sourceConceptCode)
  const targetIndex = nextMappings.findIndex((item) => item.conceptCode === targetConceptCode)
  if (sourceIndex < 0 || targetIndex < 0) {
    draggedDiseaseCode.value = ''
    return
  }
  const [movedItem] = nextMappings.splice(sourceIndex, 1)
  nextMappings.splice(targetIndex, 0, movedItem)
  diseaseMappings.value = nextMappings
  diseaseMappingSelectedCodes.value = nextMappings.map((item) => item.conceptCode).filter(Boolean)
  draggedDiseaseCode.value = ''
}

const handleDiseaseDragEnd = () => {
  draggedDiseaseCode.value = ''
}

const flattenDiseaseSelectorNodes = (nodes) => {
  const result = []
  const visit = (items) => {
    const safeItems = Array.isArray(items) ? items : []
    safeItems.forEach((item) => {
      if (item?.nodeType === 'DISEASE') {
        result.push(item)
      }
      if (Array.isArray(item?.children) && item.children.length) {
        visit(item.children)
      }
    })
  }
  visit(nodes)
  return result
}

const normalizeDiseaseConceptCodes = (conceptCodes) => {
  if (!conceptCodes?.length) {
    return []
  }
  return [...new Set(conceptCodes.filter(Boolean).map((item) => String(item).trim()))].sort()
}

const toggleDetailSection = (key) => {
  detailSections[key] = !detailSections[key]
}

const toLocalDateTimeInput = (value) => {
  if (!value) {
    return ''
  }
  return String(value).slice(0, 16)
}

</script>

<style scoped>
.knowledge-shell {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
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
  gap: 12px;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

@media (min-width: 1101px) {
  .knowledge-sidebar {
    position: sticky;
    top: 0;
    max-height: calc(100dvh - 36px);
    padding-right: 4px;
  }
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
  padding: 16px;
}

.brand-lockup {
  display: flex;
  gap: 10px;
  align-items: center;
}

.brand-logo {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #eef8f7 100%);
  padding: 7px;
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

.brand-card h1 {
  font-size: 18px;
}

.brand-subtitle,
.sidebar-note,
.empty-detail p {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #55757b;
}

.sidebar-note-steps {
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(239, 248, 247, 0.92);
  color: #3b6c74;
}

.sidebar-note-processing {
  margin-top: 0;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 244, 222, 0.9);
  color: #91600d;
}

.stage-filter-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}

.stage-filter-chip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 38px;
  padding: 0 12px;
  border: none;
  border-radius: 14px;
  background: rgba(240, 247, 247, 0.9);
  box-shadow: inset 0 0 0 1px rgba(90, 152, 156, 0.12);
  color: #3f6d73;
  font: inherit;
  cursor: pointer;
}

.stage-filter-chip span {
  font-size: 12px;
  font-weight: 700;
}

.stage-filter-chip strong {
  font-size: 12px;
  color: #1c5c64;
}

.stage-filter-chip.active {
  background: linear-gradient(135deg, rgba(46, 136, 140, 0.16) 0%, rgba(87, 173, 163, 0.2) 100%);
  box-shadow: inset 0 0 0 1px rgba(46, 136, 140, 0.18);
  color: #184b53;
}

.primary-button,
.secondary-button,
.danger-button,
.summary-nav-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  border: none;
  min-height: 42px;
  max-width: 100%;
  padding: 0 14px;
  font-weight: 700;
  line-height: 1.3;
  text-align: center;
  overflow: hidden;
  vertical-align: middle;
  cursor: pointer;
}

.secondary-button {
  color: #216a72;
  background: rgba(227, 244, 242, 0.86);
  box-shadow: inset 0 0 0 1px rgba(46, 136, 140, 0.14);
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
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.operations-card .sidebar-heading {
  margin-bottom: 10px;
}

.operations-card .action-grid .primary-button,
.operations-card .action-grid .secondary-button {
  white-space: nowrap;
  text-overflow: ellipsis;
}

.batch-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.batch-button {
  min-height: 36px;
  padding-inline: 12px;
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
  gap: 8px;
  flex: 1;
  min-height: 0;
  max-height: none;
  overflow-y: auto;
}

.filter-card {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}

.knowledge-item {
  width: 100%;
  padding: 10px 11px;
  border: 1px solid rgba(79, 160, 160, 0.14);
  border-radius: 14px;
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
  gap: 10px;
}

.knowledge-select-box {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.knowledge-select-box input {
  width: 16px;
  height: 16px;
}

.knowledge-name {
  font-size: 12.5px;
  font-weight: 700;
  color: #174047;
  word-break: break-word;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.knowledge-meta {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
  font-size: 11px;
  color: #618187;
}

.knowledge-meta-compact {
  margin-top: 8px;
}

.knowledge-subline {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  font-size: 11px;
  line-height: 1.5;
  color: #5c7e84;
}

.knowledge-subline span + span::before {
  content: '·';
  margin-right: 8px;
  color: rgba(92, 126, 132, 0.56);
}

.progress-inline {
  margin-top: 8px;
}

.progress-inline-text {
  display: inline-block;
  margin-top: 4px;
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
  min-width: 54px;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.processing-tag-processing,
.processing-tag-stage-processing {
  color: #9a6400;
  background: rgba(255, 190, 61, 0.18);
}

.processing-tag-ready,
.processing-tag-stage-ready {
  color: #0f766e;
  background: rgba(15, 118, 110, 0.14);
}

.processing-tag-stage-online {
  color: #1a5c66;
  background: rgba(46, 136, 140, 0.14);
}

.source-tag,
.detail-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 3px 8px;
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
  gap: 12px;
  min-height: 0;
  overflow: hidden;
}

.summary-card {
  justify-content: space-between;
  gap: 14px;
  flex-shrink: 0;
}

.summary-content {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.summary-actions {
  display: flex;
  flex-shrink: 0;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 10px;
}

.summary-nav-button {
  min-height: 40px;
  border: none;
  color: #216a72;
  background: rgba(227, 244, 242, 0.86);
  box-shadow: inset 0 0 0 1px rgba(46, 136, 140, 0.14);
}

.summary-nav-button:hover {
  background: rgba(216, 238, 236, 0.96);
}

.summary-stats {
  gap: 10px;
  flex-wrap: wrap;
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

.summary-caption {
  margin: 10px 0 0;
  max-width: 560px;
  font-size: 13px;
  line-height: 1.65;
  color: #5a7b80;
}

.detail-card {
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.knowledge-stage-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.knowledge-stage-item {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(244, 250, 249, 0.82);
  box-shadow: inset 0 0 0 1px rgba(90, 152, 156, 0.12);
}

.knowledge-stage-item span {
  display: block;
  margin-bottom: 4px;
  font-size: 13px;
  font-weight: 700;
  color: #40666d;
}

.knowledge-stage-item small {
  display: block;
  line-height: 1.5;
  color: #68878c;
}

.knowledge-stage-item.active {
  background: linear-gradient(180deg, rgba(230, 245, 243, 0.96) 0%, rgba(251, 255, 255, 0.98) 100%);
  box-shadow: inset 0 0 0 1px rgba(46, 136, 140, 0.18), 0 12px 24px rgba(29, 96, 102, 0.08);
}

.knowledge-stage-item.active span {
  color: #184952;
}

.detail-lock-banner {
  margin-bottom: 14px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(255, 190, 61, 0.28);
  background: linear-gradient(180deg, rgba(255, 247, 229, 0.96) 0%, rgba(255, 252, 244, 0.98) 100%);
  color: #7a580e;
}

.detail-lock-banner strong {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
}

.detail-lock-banner p {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
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
}

.detail-actions {
  align-items: flex-start;
}

.detail-actions > button,
.summary-actions > button {
  flex: 0 0 auto;
}

.summary-actions :deep(.admin-entry-button),
.summary-actions :deep(.logout-action-button) {
  max-width: 100%;
  overflow: hidden;
}

.summary-actions :deep(.admin-entry-icon-svg),
.summary-actions :deep(.logout-action-icon-svg) {
  display: block;
  width: 14px;
  height: 14px;
  max-width: 14px;
  max-height: 14px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.metadata-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.metadata-field {
  display: grid;
  gap: 8px;
}

.metadata-field span {
  font-size: 12px;
  color: #5a7c81;
}

.metadata-field input,
.metadata-field select,
.metadata-field textarea {
  width: 100%;
  padding: 11px 13px;
  border: 1px solid rgba(91, 145, 149, 0.18);
  border-radius: 14px;
  background: rgba(244, 250, 249, 0.88);
  color: #173b41;
  font: inherit;
}

.dept-tree-picker {
  position: relative;
}

.dept-tree-trigger {
  display: flex;
  width: 100%;
  min-height: 46px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 13px;
  border: 1px solid rgba(91, 145, 149, 0.18);
  border-radius: 14px;
  background: rgba(244, 250, 249, 0.88);
  color: #173b41;
  font: inherit;
  cursor: pointer;
}

.dept-tree-trigger:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.dept-tree-trigger span {
  margin: 0;
  color: #173b41;
  font-size: 14px;
  font-weight: 700;
}

.dept-tree-trigger small {
  color: #6a8a8f;
  font-size: 12px;
  white-space: nowrap;
}

.dept-tree-popover {
  position: absolute;
  z-index: 20;
  top: calc(100% + 8px);
  left: 0;
  right: 0;
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid rgba(46, 136, 140, 0.18);
  border-radius: 18px;
  background: rgba(250, 254, 253, 0.98);
  box-shadow: 0 22px 48px rgba(23, 65, 72, 0.18);
}

.dept-tree-search {
  width: 100%;
  min-height: 38px;
  padding: 0 12px;
  border: 1px solid rgba(91, 145, 149, 0.18);
  border-radius: 12px;
  background: #f4faf9;
  color: #173b41;
  font: inherit;
}

.dept-tree-options {
  display: grid;
  gap: 4px;
  max-height: 300px;
  overflow-y: auto;
  padding-right: 4px;
}

.dept-tree-option {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 36px;
  padding: 7px 10px 7px calc(10px + var(--dept-depth, 0) * 18px);
  border: none;
  border-radius: 12px;
  background: transparent;
  color: #1d464d;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.dept-tree-option:hover,
.dept-tree-option.active {
  background: rgba(46, 136, 140, 0.12);
}

.dept-tree-branch {
  width: 12px;
  height: 16px;
  border-left: 1px solid rgba(46, 136, 140, 0.28);
  border-bottom: 1px solid rgba(46, 136, 140, 0.28);
  border-radius: 0 0 0 6px;
}

.dept-tree-option-label {
  overflow: hidden;
  color: #1b454c;
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dept-tree-option small {
  color: #789399;
  font-size: 11px;
}

.dept-tree-empty {
  padding: 12px;
  border-radius: 12px;
  background: rgba(240, 247, 247, 0.88);
  color: #6a858b;
  text-align: center;
}

.metadata-field textarea {
  min-height: 88px;
  resize: vertical;
}

.metadata-field-wide {
  grid-column: 1 / -1;
}

.metadata-disease-card {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgba(91, 145, 149, 0.14);
  border-radius: 18px;
  background: rgba(242, 249, 249, 0.82);
}

.metadata-disease-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.metadata-disease-head div:first-child {
  display: grid;
  gap: 5px;
}

.metadata-disease-head strong {
  color: #173b41;
  font-size: 15px;
}

.metadata-disease-head span,
.metadata-disease-empty {
  color: #6b8b91;
  font-size: 12px;
  line-height: 1.55;
}

.metadata-disease-actions,
.metadata-disease-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.metadata-disease-count {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(228, 244, 243, 0.96);
  color: #2f7075;
  font-size: 12px;
  font-weight: 700;
}

.metadata-disease-body {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: 12px;
}

.metadata-disease-picker,
.metadata-disease-selected {
  display: grid;
  gap: 10px;
  min-width: 0;
  padding: 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: inset 0 0 0 1px rgba(91, 145, 149, 0.1);
}

.metadata-disease-hint,
.metadata-disease-selected-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.metadata-disease-hint span {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: rgba(228, 244, 243, 0.96);
  color: #2f7075;
  font-size: 11px;
  font-weight: 700;
}

.metadata-disease-hint small,
.metadata-disease-selected-head small {
  color: #6b8b91;
  font-size: 12px;
  line-height: 1.5;
}

.metadata-disease-selected-head strong {
  color: #173b41;
  font-size: 14px;
}

.metadata-disease-tree-shell {
  max-height: 360px;
  overflow-y: auto;
  padding: 8px;
  border: 1px solid rgba(91, 145, 149, 0.12);
  border-radius: 16px;
  background: rgba(245, 250, 250, 0.82);
}

.metadata-disease-tree {
  background: transparent;
  color: #204d55;
}

.metadata-disease-tree :deep(.el-tree-node__content) {
  position: relative;
  min-height: 42px;
  height: auto;
  margin: 2px 0;
  padding: 4px 6px;
  border-radius: 12px;
  transition: background-color 160ms ease, box-shadow 160ms ease, transform 160ms ease;
}

.metadata-disease-tree :deep(.el-tree-node__content:hover),
.metadata-disease-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: rgba(232, 247, 246, 0.95);
  box-shadow: inset 0 0 0 1px rgba(88, 170, 168, 0.16);
}

.metadata-disease-tree :deep(.el-tree-node.is-current > .el-tree-node__content::before) {
  content: '';
  position: absolute;
  left: -8px;
  top: 7px;
  bottom: 7px;
  width: 4px;
  border-radius: 999px;
  background: linear-gradient(180deg, #4aa7a5, #66c0a8);
}

.metadata-disease-tree :deep(.el-tree-node__content:hover) {
  transform: translateX(2px);
}

.metadata-disease-tree :deep(.el-tree-node__expand-icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-right: 5px;
  border-radius: 999px;
  background: rgba(232, 246, 246, 0.92);
  color: #5d8f95;
  transition: transform 180ms ease, background-color 180ms ease, color 180ms ease;
}

.metadata-disease-tree :deep(.el-tree-node__expand-icon.expanded) {
  background: rgba(76, 166, 164, 0.14);
  color: #2d6f76;
  transform: rotate(90deg);
}

.metadata-disease-tree :deep(.el-tree-node__expand-icon.is-leaf) {
  background: transparent;
  color: transparent;
}

.metadata-disease-tree :deep(.el-tree-node__children) {
  margin-left: 10px;
  padding-left: 10px;
  border-left: 1px solid rgba(106, 178, 179, 0.16);
}

.knowledge-disease-tree-node {
  display: flex;
  width: 100%;
  min-width: 0;
}

.knowledge-disease-tree-main {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.knowledge-disease-tree-title-row {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.knowledge-disease-tree-title-row strong {
  overflow: hidden;
  color: #1d4d54;
  font-size: 13px;
  font-weight: 800;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-disease-tree-main > span {
  color: #6f9197;
  font-size: 11px;
  line-height: 1.45;
}

.knowledge-disease-tree-badge {
  display: inline-flex;
  align-items: center;
  min-height: 19px;
  padding: 0 7px;
  flex: 0 0 auto;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 800;
}

.knowledge-disease-tree-badge.category {
  background: rgba(76, 166, 164, 0.14);
  color: #2d6f76;
}

.knowledge-disease-tree-badge.disease {
  background: rgba(98, 134, 201, 0.12);
  color: #446a9c;
}

.metadata-disease-list {
  display: grid;
  gap: 8px;
  max-height: 360px;
  overflow-y: auto;
  padding-right: 4px;
}

.metadata-disease-item {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px;
  border: 1px solid rgba(91, 145, 149, 0.12);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.78);
  transition: transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease;
}

.metadata-disease-item.dragging {
  opacity: 0.72;
  transform: scale(0.99);
}

.metadata-disease-item:hover {
  border-color: rgba(76, 166, 164, 0.22);
  box-shadow: 0 12px 24px rgba(39, 105, 111, 0.06);
}

.metadata-disease-item div {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.metadata-disease-item strong,
.metadata-disease-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metadata-disease-item strong {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #173b41;
  font-size: 14px;
}

.metadata-disease-item span,
.metadata-disease-item small {
  color: #6b8b91;
  font-size: 12px;
}

.metadata-disease-item small {
  flex: 0 0 auto;
  white-space: nowrap;
}

.metadata-disease-remove {
  min-height: 34px;
  padding-inline: 10px;
}

.metadata-disease-item-actions {
  display: grid;
  justify-items: end;
  gap: 6px;
  flex: 0 0 auto;
}

.metadata-disease-drag-handle,
.metadata-disease-priority {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.metadata-disease-drag-handle {
  background: rgba(238, 247, 246, 0.92);
  color: #5d858b;
  cursor: grab;
}

.metadata-disease-priority {
  background: rgba(76, 166, 164, 0.14);
  color: #2d6f76;
  flex: 0 0 auto;
}

.content-card {
  padding: 14px 16px;
  border-radius: 20px;
  background: rgba(247, 251, 251, 0.9);
  box-shadow: inset 0 0 0 1px rgba(90, 152, 156, 0.1);
}

.detail-section-card {
  display: grid;
  gap: 12px;
}

.detail-section-body {
  display: grid;
  gap: 12px;
}

.section-heading-toggle {
  width: 100%;
  border: none;
  padding: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.section-heading-toggle div {
  min-width: 0;
}

.section-heading-toggle strong {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(235, 246, 245, 0.92);
  color: #2b6670;
  font-size: 12px;
  font-weight: 700;
  box-shadow: inset 0 0 0 1px rgba(90, 152, 156, 0.12);
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

.workflow-note {
  margin-top: 0;
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
    overflow-x: hidden;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
  }

  .action-grid {
    grid-template-columns: 1fr;
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

  .summary-content {
    width: 100%;
    flex-direction: column;
    align-items: stretch;
  }

  .summary-actions {
    justify-content: flex-start;
  }

  .detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metadata-form-grid {
    grid-template-columns: 1fr;
  }

  .metadata-disease-body {
    grid-template-columns: 1fr;
  }

  .metadata-disease-list {
    max-height: none;
  }

  .dept-tree-popover {
    position: static;
  }

  .knowledge-stage-strip {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .knowledge-shell {
    padding: 10px;
    gap: 10px;
  }

  .sidebar-card,
  .summary-card,
  .detail-card,
  .empty-detail {
    padding: 14px;
    border-radius: 18px;
  }

  .brand-lockup {
    align-items: flex-start;
  }

  .brand-logo {
    width: 54px;
    height: 54px;
    border-radius: 16px;
  }

  .brand-card h1,
  .summary-card h2,
  .empty-detail h3 {
    font-size: 22px;
  }

  .brand-subtitle,
  .sidebar-note,
  .empty-detail p,
  .status-note,
  .progress-note {
    font-size: 12px;
  }

  .action-grid {
    grid-template-columns: 1fr;
  }

  .batch-toolbar {
    flex-direction: column;
  }

  .stage-filter-row {
    grid-template-columns: 1fr;
  }

  .knowledge-list {
    max-height: none;
  }

  .summary-stats,
  .detail-grid {
    width: 100%;
  }

  .summary-stats {
    flex-direction: column;
    align-items: stretch;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .summary-stat,
  .meta-card {
    padding: 9px 10px;
  }

  .detail-toolbar,
  .detail-badges,
  .detail-actions,
  .metadata-disease-head,
  .progress-card-head,
  .chunk-item summary {
    flex-direction: column;
    align-items: flex-start;
  }

  .detail-actions,
  .metadata-disease-actions {
    width: 100%;
  }

  .metadata-disease-count {
    width: 100%;
    justify-content: center;
  }

  .detail-actions .slim-button,
  .detail-actions .danger-button,
  .metadata-disease-actions .slim-button {
    width: 100%;
  }

  .knowledge-disease-tree-title-row {
    flex-wrap: wrap;
    gap: 6px;
  }

  .knowledge-disease-tree-title-row strong {
    font-size: 12px;
    white-space: normal;
  }

  .knowledge-disease-tree-main > span {
    font-size: 10.5px;
  }

  .content-card {
    margin-top: 14px;
    padding: 14px;
    border-radius: 16px;
  }

  .content-editor,
  .content-preview,
  .chunk-content {
    min-height: 180px;
    padding: 12px;
  }

  .chunk-item summary {
    padding: 12px 14px;
  }

  .chunk-preview {
    padding: 0 14px 10px;
    font-size: 12px;
  }

  .chunk-content {
    margin: 0 14px 14px;
  }
}

@media (max-width: 520px) {
  .knowledge-shell {
    padding: 8px;
    gap: 8px;
  }

  .sidebar-card,
  .summary-card,
  .detail-card,
  .empty-detail {
    padding: 12px;
    border-radius: 16px;
  }

  .search-input {
    min-height: 40px;
    padding: 0 12px;
  }

  .knowledge-item {
    padding: 10px;
    border-radius: 14px;
  }

  .knowledge-item-top,
  .knowledge-meta {
    flex-direction: column;
    gap: 4px;
  }

  .processing-tag,
  .source-tag,
  .detail-badge {
    min-width: 0;
  }

  .progress-card {
    padding: 12px;
    border-radius: 16px;
  }

  .progress-value {
    font-size: 20px;
  }

  .primary-button,
  .secondary-button,
  .danger-button,
  .back-button {
    min-height: 38px;
  }
}
</style>
