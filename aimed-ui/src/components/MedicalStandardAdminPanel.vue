<template>
  <div class="medicalStandard-panel">
    <div class="admin-toolbar">
      <div class="admin-toolbar-copy">
        <h3>疾病标准</h3>
        <p>检索疾病标准数据，并维护疾病对应的症状与主诉映射。</p>
      </div>
      <div class="admin-toolbar-actions">
        <button class="admin-secondary-button" type="button" :disabled="summaryLoading || categoryLoading" @click="refreshAll">
          {{ summaryLoading || categoryLoading ? '刷新中...' : '刷新数据' }}
        </button>
        <button class="admin-primary-button" type="button" @click="openAllMappingsDialog">
          查看全部映射
        </button>
      </div>
    </div>

    <div class="medicalStandard-grid">
      <section class="medicalStandard-card disease-browser-card">
        <header class="medicalStandard-card-head">
          <div>
            <h4>检索疾病</h4>
            <p>疾病数据为前端静态标准库，支持按疾病名、编码、分类和别名检索。</p>
          </div>
        </header>

        <div class="medicalStandard-metrics">
          <span>疾病 {{ formatCount(summary?.entityCount) }}</span>
          <span>症状主诉 {{ formatCount(summary?.symptomMappingCount) }}</span>
          <span>{{ entityResultSummary }}</span>
        </div>

        <div class="disease-search-row">
          <label class="admin-field">
            <span>搜索关键词</span>
            <input v-model.trim="entityKeyword" type="text" placeholder="如 感冒、肝癌、上呼吸道感染、C22、gm" />
          </label>
          <div class="search-actions">
            <span class="search-status-pill">{{ searchStatusText }}</span>
            <button class="admin-secondary-button compact" type="button" :disabled="!entityKeyword.trim()" @click="clearEntitySearch">清空</button>
          </div>
        </div>

        <div class="tree-mode-strip">
          <span class="mode-pill">{{ isSearchMode ? '检索模式' : '浏览模式' }}</span>
          <span class="tree-mode-copy">
            {{ isSearchMode ? `当前按“${entityKeyword.trim()}”展示命中的疾病树` : '按疾病分类展开浏览，点击疾病后在右侧维护映射。' }}
          </span>
        </div>

        <div class="disease-tree-shell">
          <el-tree
            :key="treeRenderKey"
            ref="diseaseTreeRef"
            class="disease-tree-widget"
            :data="diseaseTreeData"
            node-key="nodeKey"
            :props="diseaseTreeProps"
            :lazy="!isSearchMode"
            :load="loadDiseaseTreeNode"
            :default-expand-all="isSearchMode"
            :expand-on-click-node="false"
            :highlight-current="true"
            :empty-text="categoryLoading ? '疾病树加载中...' : '暂无疾病数据'"
            @node-click="handleDiseaseTreeNodeClick"
          >
            <template #default="{ data }">
              <div :class="['disease-tree-node', data.nodeType === 'CATEGORY' ? 'category' : 'disease']">
                <div class="disease-tree-main">
                  <div class="tree-title-row">
                    <strong>{{ data.label }}</strong>
                    <span :class="['tree-node-badge', data.nodeType === 'CATEGORY' ? 'category' : 'disease']">
                      {{ data.nodeType === 'CATEGORY' ? '分类' : '疾病' }}
                    </span>
                  </div>
                  <span v-if="data.nodeType === 'CATEGORY'">{{ data.categoryCode }} · {{ formatCount(data.count) }} 个疾病</span>
                  <span v-else>{{ data.standardCode || data.conceptCode }}</span>
                </div>
                <small v-if="data.nodeType === 'DISEASE' && previewAliases(data).length">
                  {{ previewAliases(data).join(' / ') }}
                </small>
              </div>
            </template>
          </el-tree>
        </div>
        <p v-if="searchPerformed && !searchLoading && !entityResults.length" class="medicalStandard-empty">没有匹配的疾病。</p>

        <div v-if="selectedConcept" class="selected-disease-card">
          <div class="selected-disease-title">
            <div>
              <span>当前疾病</span>
              <strong>{{ selectedConcept.diseaseName || selectedConcept.standardCode }}</strong>
            </div>
            <small>{{ selectedConcept.standardCode || selectedConcept.conceptCode }}</small>
          </div>
          <div class="selected-disease-meta">
            <span>ICD-10：{{ selectedConcept.icd10Code || '未标注' }}</span>
            <span>医保编码：{{ selectedConcept.nhsaCode || '未标注' }}</span>
            <span>分类：{{ selectedConcept.categoryName || '未分类' }}</span>
          </div>
          <div class="alias-chip-row">
            <span v-for="alias in displayAliases(selectedConcept)" :key="alias" class="alias-chip">{{ alias }}</span>
            <span v-if="!displayAliases(selectedConcept).length" class="alias-chip muted">暂无别名</span>
          </div>
        </div>
      </section>

      <section class="medicalStandard-card mapping-panel-card">
        <header class="medicalStandard-card-head mapping-head">
          <div>
            <h4>症状 / 主诉映射</h4>
            <p>选择左侧疾病后，在这里维护该疾病的症状词和主诉词。</p>
          </div>
          <div class="mapping-count-pill">{{ currentMappingTitle }}</div>
        </header>

        <template v-if="selectedConcept">
          <div class="mapping-summary-strip">
            <span>总映射 {{ symptomMappings.length }}</span>
            <span>启用 {{ enabledMappingCount }}</span>
            <span>{{ symptomForm.id ? '正在编辑现有映射' : '当前为新增映射' }}</span>
          </div>

          <div class="mapping-target-card">
            <span>当前维护疾病</span>
            <strong>{{ mappingTargetTitle }}</strong>
            <small>{{ symptomForm.conceptCode }}</small>
          </div>

          <div class="mapping-search-row compact-search">
            <label class="admin-field">
              <span>检索当前映射</span>
              <input v-model.trim="mappingKeyword" type="text" placeholder="输入症状词或主诉词" />
            </label>
            <button class="admin-secondary-button compact" type="button" @click="mappingKeyword = ''">清空</button>
          </div>

          <div class="mapping-editor-card">
            <div class="mapping-editor-head">
              <div>
                <strong>{{ symptomForm.id ? '编辑映射' : '新增映射' }}</strong>
                <span>{{ symptomForm.id ? '当前表单已带入所选映射，修改后可直接保存或删除。' : '输入症状词或主诉词后可直接保存到当前疾病。' }}</span>
              </div>
              <span :class="['inline-tag', symptomForm.id ? 'inline-tag-active' : 'inline-tag-neutral']">
                {{ symptomForm.id ? '编辑中' : '新增' }}
              </span>
            </div>
            <div class="mapping-form-grid">
              <label class="admin-field">
                <span>症状词</span>
                <input v-model.trim="symptomForm.symptomTerm" type="text" placeholder="如 发热、鼻塞、咳嗽" />
              </label>
              <label class="admin-field">
                <span>主诉词</span>
                <input v-model.trim="symptomForm.chiefComplaintTerm" type="text" placeholder="如 感冒了、拉肚子" />
              </label>
              <label class="admin-field">
                <span>映射类型</span>
                <select v-model="symptomForm.mappingType">
                  <option v-for="option in mappingTypeOptions" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </label>
              <label class="admin-field">
                <span>权重</span>
                <input v-model.number="symptomForm.weight" type="number" min="0" max="10" step="0.1" />
              </label>
            </div>
            <label class="admin-switch-field">
              <input v-model="symptomForm.enabled" type="checkbox" />
              <span>启用该映射</span>
            </label>
            <div class="mapping-actions">
              <button class="admin-primary-button compact" type="button" :disabled="symptomSaving" @click="saveSymptomMapping">
                {{ symptomSaving ? '保存中...' : symptomForm.id ? '保存修改' : '新增映射' }}
              </button>
              <button
                v-if="symptomForm.id"
                class="admin-secondary-button compact danger"
                type="button"
                :disabled="symptomSaving"
                @click="removeSymptomMapping(symptomForm)"
              >
                删除当前
              </button>
              <button class="admin-secondary-button compact" type="button" @click="resetSymptomForm">
                清空表单
              </button>
            </div>
          </div>

          <div class="mapping-list-head">
            <div>
              <strong>当前疾病映射</strong>
              <span>点击下方任一映射，会直接带入上方表单编辑。</span>
            </div>
            <span class="mapping-list-count">{{ filteredSymptomMappings.length }} 条</span>
          </div>

          <div class="mapping-inline-shell">
            <div class="mapping-inline-header">
              <span>症状 / 主诉</span>
              <span>类型</span>
              <span>状态</span>
              <span>权重</span>
            </div>
            <div class="mapping-inline-list">
              <button
                v-for="row in filteredSymptomMappings"
                :key="row.id"
                :class="['mapping-inline-row', { active: symptomForm.id === row.id }]"
                type="button"
                @click="editSymptomMapping(row)"
              >
                <div class="mapping-row-main">
                  <div class="mapping-row-lines">
                    <span class="mapping-row-line"><strong>症状</strong>{{ row.symptomTerm || '未填写' }}</span>
                    <span class="mapping-row-line"><strong>主诉</strong>{{ row.chiefComplaintTerm || '未填写' }}</span>
                  </div>
                  <span class="inline-tag inline-tag-strong">{{ mappingTypeLabel(row.mappingType) }}</span>
                  <span :class="['inline-tag', row.enabled ? 'inline-tag-active' : 'inline-tag-disabled']">
                    {{ row.enabled ? '启用' : '停用' }}
                  </span>
                  <span class="inline-tag">权重 {{ row.weight }}</span>
                </div>
              </button>
              <p v-if="!filteredSymptomMappings.length" class="medicalStandard-empty">
                {{ symptomLoading ? '映射加载中...' : '当前疾病暂无症状主诉映射。' }}
              </p>
            </div>
          </div>
        </template>

        <div v-else class="mapping-empty-state">
          <strong>请先选择疾病</strong>
          <span>左侧点击疾病后，可在这里新增、编辑和删除该疾病的症状主诉映射。</span>
        </div>
      </section>
    </div>

    <div v-if="showAllMappingsDialog" class="mapping-dialog-mask" @click.self="closeAllMappingsDialog">
      <section class="mapping-dialog">
        <header class="mapping-dialog-head">
          <div>
            <h4>全部症状 / 主诉映射</h4>
            <p>用于快速查看和定位所有已维护映射。</p>
          </div>
          <button class="admin-secondary-button compact" type="button" @click="closeAllMappingsDialog">关闭</button>
        </header>
        <div class="mapping-search-row">
          <label class="admin-field">
            <span>搜索映射</span>
            <input v-model.trim="allMappingKeyword" type="text" placeholder="疾病、编码、症状词、主诉词" />
          </label>
          <span class="search-status-pill">{{ allMappingStatusText }}</span>
          <button class="admin-secondary-button compact" type="button" :disabled="allMappingsLoading" @click="clearAllMappingSearch">清空</button>
        </div>
        <div class="mapping-dialog-list">
          <article v-for="row in allMappings" :key="row.id" class="mapping-item-card">
            <div class="mapping-item-main">
              <strong>{{ row.diseaseTitle || row.standardCode || row.conceptCode }}</strong>
              <span>{{ row.standardCode || row.conceptCode }}</span>
            </div>
            <div class="mapping-term-row">
              <span v-if="row.symptomTerm">症状：{{ row.symptomTerm }}</span>
              <span v-if="row.chiefComplaintTerm">主诉：{{ row.chiefComplaintTerm }}</span>
            </div>
            <div class="mapping-item-meta">
              <span class="inline-tag inline-tag-active">{{ mappingTypeLabel(row.mappingType) }}</span>
              <span :class="['inline-tag', row.enabled ? 'inline-tag-active' : 'inline-tag-disabled']">
                {{ row.enabled ? '启用' : '停用' }}
              </span>
              <span class="inline-tag">权重 {{ row.weight }}</span>
            </div>
            <div class="row-actions">
              <button class="admin-secondary-button compact" type="button" @click="editMappingFromDialog(row)">编辑</button>
              <button class="admin-secondary-button compact danger" type="button" @click="removeSymptomMapping(row)">删除</button>
            </div>
          </article>
          <p v-if="!allMappings.length" class="medicalStandard-empty">
            {{ allMappingsLoading ? '映射加载中...' : '暂无症状主诉映射。' }}
          </p>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, markRaw, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { apiClient } from '@/lib/auth'

const emit = defineEmits(['audit-refresh'])

const summary = ref(null)
const categories = shallowRef([])
const diseaseTreeRef = ref(null)
const selectedConcept = ref(null)
const mappingTarget = ref(null)
const summaryLoading = ref(false)
const categoryLoading = ref(false)
const symptomLoading = ref(false)
const symptomSaving = ref(false)
const showAllMappingsDialog = ref(false)
const allMappingsLoading = ref(false)
const searchLoading = ref(false)
const searchPerformed = ref(false)
const treeVersion = ref(0)
let searchDebounceTimer = null
let allMappingDebounceTimer = null

const entityKeyword = ref('')
const mappingKeyword = ref('')
const symptomMappings = ref([])
const allMappings = ref([])
const allMappingKeyword = ref('')
const diseaseSearchIndex = shallowRef([])
const diseaseSearchResults = shallowRef([])
const categoryChildrenCache = new Map()
const diseaseTreeProps = {
  label: 'label',
  children: 'children',
  isLeaf: 'leaf',
}

const symptomForm = reactive({
  id: null,
  conceptCode: '',
  symptomTerm: '',
  chiefComplaintTerm: '',
  mappingType: 'BOTH',
  weight: 1,
  enabled: true,
})

const mappingTypeOptions = [
  { value: 'SYMPTOM', label: '症状' },
  { value: 'CHIEF_COMPLAINT', label: '主诉' },
  { value: 'BOTH', label: '症状 + 主诉' },
]

const normalizedEntityKeyword = computed(() => entityKeyword.value.trim().toLowerCase())
const isSearchMode = computed(() => searchPerformed.value && Boolean(normalizedEntityKeyword.value))
const entityResults = computed(() => diseaseSearchResults.value)
const searchTreeData = computed(() => {
  if (!isSearchMode.value) {
    return []
  }
  const categoryOrder = new Map(categories.value.map((item, index) => [item.categoryCode, index]))
  const groups = new Map()
  entityResults.value.forEach((item) => {
    const key = item.categoryCode || 'UNCATEGORIZED'
    if (!groups.has(key)) {
      groups.set(key, {
        nodeType: 'CATEGORY',
        nodeKey: `SEARCH_CATEGORY:${key}`,
        label: item.categoryName || '未分类',
        categoryCode: key,
        categoryName: item.categoryName || '未分类',
        count: 0,
        leaf: false,
        children: [],
      })
    }
    groups.get(key).children.push({
      ...item,
      nodeKey: `SEARCH_DISEASE:${item.conceptCode}`,
      nodeType: 'DISEASE',
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
const diseaseTreeData = computed(() => (isSearchMode.value ? searchTreeData.value : categories.value))
const treeRenderKey = computed(() => `${isSearchMode.value ? 'search' : 'browse'}-${treeVersion.value}-${entityResults.value.length}`)
const filteredSymptomMappings = computed(() => {
  const tokens = splitSearchTokens(mappingKeyword.value)
  if (!tokens.length) {
    return symptomMappings.value
  }
  return symptomMappings.value.filter((item) => {
    const text = [
      item.symptomTerm,
      item.chiefComplaintTerm,
      item.mappingType,
      item.enabled ? '启用' : '停用',
    ].filter(Boolean).join(' ').toLowerCase()
    return tokens.every((token) => text.includes(token))
  })
})

const mappingTargetTitle = computed(() => mappingTarget.value?.diseaseName || selectedConcept.value?.diseaseName || '未选择疾病')
const currentMappingTitle = computed(() => selectedConcept.value ? `当前疾病 ${symptomMappings.value.length} 条` : '未选择疾病')
const enabledMappingCount = computed(() => symptomMappings.value.filter((item) => item.enabled).length)
const entityResultSummary = computed(() => {
  if (!entityKeyword.value) {
    return `${categories.value.length} 类`
  }
  if (searchLoading.value) {
    return '检索中...'
  }
  return searchPerformed.value ? `${entityResults.value.length} 条` : '自动检索'
})
const searchStatusText = computed(() => {
  if (!entityKeyword.value.trim()) {
    return '输入后自动检索'
  }
  if (searchLoading.value) {
    return '正在检索'
  }
  if (!searchPerformed.value) {
    return '等待检索'
  }
  return `命中 ${entityResults.value.length} 条`
})
const allMappingStatusText = computed(() => {
  if (allMappingsLoading.value) {
    return '检索中'
  }
  if (!showAllMappingsDialog.value) {
    return '全部映射'
  }
  if (!allMappingKeyword.value.trim()) {
    return `共 ${allMappings.value.length} 条`
  }
  return `命中 ${allMappings.value.length} 条`
})

onMounted(async () => {
  await Promise.all([loadSummary(), loadCategories()])
})

onBeforeUnmount(() => {
  if (searchDebounceTimer) {
    clearTimeout(searchDebounceTimer)
    searchDebounceTimer = null
  }
  if (allMappingDebounceTimer) {
    clearTimeout(allMappingDebounceTimer)
    allMappingDebounceTimer = null
  }
})

watch(entityKeyword, (value) => {
  if (searchDebounceTimer) {
    clearTimeout(searchDebounceTimer)
  }
  const keyword = value.trim()
  if (!keyword) {
    clearEntitySearch()
    return
  }
  searchDebounceTimer = setTimeout(() => {
    searchEntities()
  }, 220)
})

watch(allMappingKeyword, (value) => {
  if (!showAllMappingsDialog.value) {
    return
  }
  if (allMappingDebounceTimer) {
    clearTimeout(allMappingDebounceTimer)
  }
  allMappingDebounceTimer = setTimeout(() => {
    loadAllMappings()
  }, value.trim() ? 220 : 120)
})

const refreshAll = async () => {
  clearDiseaseCaches()
  await Promise.all([loadSummary(), loadCategories()])
  if (selectedConcept.value) {
    await loadSymptomMappings()
  }
}

const loadSummary = async () => {
  summaryLoading.value = true
  try {
    const { data } = await apiClient.get('/api/aimed/admin/medical-standards/summary')
    summary.value = data
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '疾病标准摘要加载失败'))
  } finally {
    summaryLoading.value = false
  }
}

const loadCategories = async () => {
  categoryLoading.value = true
  try {
    clearDiseaseCaches()
    const diseaseCategories = await loadStaticDiseaseJson('disease-categories.json')
    categories.value = markRaw(Array.isArray(diseaseCategories) ? diseaseCategories : [])
    if (entityKeyword.value.trim()) {
      await searchEntities()
    }
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '疾病树加载失败'))
  } finally {
    categoryLoading.value = false
  }
}

const searchEntities = async () => {
  if (!categories.value.length) {
    if (categoryLoading.value) {
      return
    }
    ElMessage.warning('疾病树还在加载，请稍后再试')
    return
  }
  const tokens = splitSearchTokens(normalizedEntityKeyword.value)
  if (!tokens.length) {
    clearEntitySearch()
    return
  }
  searchLoading.value = true
  searchPerformed.value = true
  try {
    const index = await loadDiseaseSearchIndex()
    diseaseSearchResults.value = markRaw(index
      .filter((item) => tokens.every((token) => item.searchText.includes(token)))
      .slice(0, 160))
    treeVersion.value += 1
    await nextTick()
  } catch (error) {
    diseaseSearchResults.value = []
    ElMessage.error(resolveErrorMessage(error, '疾病检索失败'))
  } finally {
    searchLoading.value = false
  }
}

const clearEntitySearch = () => {
  entityKeyword.value = ''
  searchPerformed.value = false
  searchLoading.value = false
  diseaseSearchResults.value = []
  treeVersion.value += 1
}

const loadDiseaseTreeNode = async (node, resolve) => {
  if (node.level === 0) {
    resolve(categories.value)
    return
  }
  const item = node.data
  resolve(item?.nodeType === 'CATEGORY' ? await loadCategoryChildren(item.categoryCode) : [])
}

const handleDiseaseTreeNodeClick = async (item, node) => {
  if (item?.nodeType === 'CATEGORY') {
    if (isSearchMode.value) {
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
      })
    }, true)
    return
  }
  if (item?.nodeType === 'DISEASE') {
    await selectEntity(item)
  }
}

const loadCategoryChildren = async (categoryCode) => {
  if (!categoryCode) {
    return []
  }
  if (categoryChildrenCache.has(categoryCode)) {
    return categoryChildrenCache.get(categoryCode)
  }
  try {
    const children = await loadStaticDiseaseJson(`categories/${sanitizeCategoryCode(categoryCode)}.json`)
    const safeChildren = Array.isArray(children) ? markRaw(children.map(enrichDiseaseNode)) : []
    categoryChildrenCache.set(categoryCode, safeChildren)
    return safeChildren
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '疾病分类数据加载失败'))
    return []
  }
}

const loadDiseaseSearchIndex = async () => {
  if (diseaseSearchIndex.value.length) {
    return diseaseSearchIndex.value
  }
  const groups = await Promise.all(categories.value.map(async (category) => {
    if (categoryChildrenCache.has(category.categoryCode)) {
      return categoryChildrenCache.get(category.categoryCode)
    }
    const children = await loadStaticDiseaseJson(`categories/${sanitizeCategoryCode(category.categoryCode)}.json`)
    const safeChildren = Array.isArray(children) ? markRaw(children.map(enrichDiseaseNode)) : []
    categoryChildrenCache.set(category.categoryCode, safeChildren)
    return safeChildren
  }))
  diseaseSearchIndex.value = markRaw(groups.flat())
  return diseaseSearchIndex.value
}

const loadStaticDiseaseJson = async (path) => {
  const response = await fetch(staticDiseaseAssetUrl(path), { cache: 'force-cache' })
  if (!response.ok) {
    throw new Error(`疾病静态数据加载失败: ${response.status}`)
  }
  return response.json()
}

const staticDiseaseAssetUrl = (path) => {
  const base = import.meta.env.BASE_URL.endsWith('/') ? import.meta.env.BASE_URL : `${import.meta.env.BASE_URL}/`
  return `${base}medical-standards/${path}`
}

const selectEntity = async (item) => {
  selectedConcept.value = item
  mappingTarget.value = {
    conceptCode: item?.conceptCode || '',
    diseaseName: item?.diseaseName || item?.standardCode || '',
    standardCode: item?.standardCode || '',
  }
  resetSymptomForm({ keepDisease: false })
  await loadSymptomMappings()
}

const loadSymptomMappings = async () => {
  if (!symptomForm.conceptCode) {
    symptomMappings.value = []
    return
  }
  symptomLoading.value = true
  try {
    const { data } = await apiClient.get('/api/aimed/admin/medical-standards/symptoms', {
      params: { conceptCode: symptomForm.conceptCode },
    })
    symptomMappings.value = Array.isArray(data) ? data : []
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '症状主诉映射加载失败'))
  } finally {
    symptomLoading.value = false
  }
}

const saveSymptomMapping = async () => {
  if (!symptomForm.conceptCode.trim()) {
    ElMessage.warning('请先选择疾病')
    return
  }
  if (!symptomForm.symptomTerm.trim() && !symptomForm.chiefComplaintTerm.trim()) {
    ElMessage.warning('症状词和主诉词至少填写一个')
    return
  }
  symptomSaving.value = true
  try {
    await apiClient.post('/api/aimed/admin/medical-standards/symptoms', {
      id: symptomForm.id || null,
      conceptCode: symptomForm.conceptCode.trim(),
      symptomTerm: symptomForm.symptomTerm.trim() || null,
      chiefComplaintTerm: symptomForm.chiefComplaintTerm.trim() || null,
      mappingType: symptomForm.mappingType,
      weight: Number(symptomForm.weight || 1),
      enabled: symptomForm.enabled,
    })
    ElMessage.success('症状主诉映射已保存')
    resetSymptomForm({ keepDisease: true })
    await Promise.all([loadSymptomMappings(), showAllMappingsDialog.value ? loadAllMappings() : Promise.resolve(), loadSummary()])
    emit('audit-refresh')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '症状主诉映射保存失败'))
  } finally {
    symptomSaving.value = false
  }
}

const editSymptomMapping = (row) => {
  mappingTarget.value = {
    conceptCode: row?.conceptCode || '',
    diseaseName: row?.diseaseTitle || row?.standardCode || '',
    standardCode: row?.standardCode || '',
  }
  symptomForm.id = row?.id || null
  symptomForm.conceptCode = row?.conceptCode || ''
  symptomForm.symptomTerm = row?.symptomTerm || ''
  symptomForm.chiefComplaintTerm = row?.chiefComplaintTerm || ''
  symptomForm.mappingType = row?.mappingType || 'BOTH'
  symptomForm.weight = Number(row?.weight || 1)
  symptomForm.enabled = Boolean(row?.enabled)
}

const editMappingFromDialog = async (row) => {
  editSymptomMapping(row)
  const concept = await findDiseaseByConceptCode(row?.conceptCode)
  if (concept) {
    selectedConcept.value = concept
  }
  showAllMappingsDialog.value = false
  await loadSymptomMappings()
}

const removeSymptomMapping = async (row) => {
  if (!row?.id) {
    ElMessage.warning('请先选择需要删除的映射')
    return
  }
  try {
    await apiClient.delete(`/api/aimed/admin/medical-standards/symptoms/${row.id}`)
    ElMessage.success('症状主诉映射已删除')
    if (symptomForm.id === row.id) {
      resetSymptomForm({ keepDisease: true })
    }
    await Promise.all([loadSymptomMappings(), showAllMappingsDialog.value ? loadAllMappings() : Promise.resolve(), loadSummary()])
    emit('audit-refresh')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '症状主诉映射删除失败'))
  }
}

const resetSymptomForm = ({ keepDisease = false } = {}) => {
  const currentConceptCode = keepDisease ? symptomForm.conceptCode : selectedConcept.value?.conceptCode || ''
  symptomForm.id = null
  symptomForm.conceptCode = currentConceptCode
  symptomForm.symptomTerm = ''
  symptomForm.chiefComplaintTerm = ''
  symptomForm.mappingType = 'BOTH'
  symptomForm.weight = 1
  symptomForm.enabled = true
  if (!keepDisease && selectedConcept.value) {
    mappingTarget.value = {
      conceptCode: selectedConcept.value.conceptCode,
      diseaseName: selectedConcept.value.diseaseName || selectedConcept.value.standardCode,
      standardCode: selectedConcept.value.standardCode,
    }
  }
}

const openAllMappingsDialog = async () => {
  showAllMappingsDialog.value = true
  await loadAllMappings()
}

const closeAllMappingsDialog = () => {
  showAllMappingsDialog.value = false
  if (allMappingDebounceTimer) {
    clearTimeout(allMappingDebounceTimer)
    allMappingDebounceTimer = null
  }
}

const loadAllMappings = async () => {
  allMappingsLoading.value = true
  try {
    const { data } = await apiClient.get('/api/aimed/admin/medical-standards/symptoms', {
      params: { keyword: allMappingKeyword.value.trim() || undefined },
    })
    allMappings.value = Array.isArray(data) ? data : []
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '全部映射加载失败'))
  } finally {
    allMappingsLoading.value = false
  }
}

const clearAllMappingSearch = async () => {
  allMappingKeyword.value = ''
  await loadAllMappings()
}

const findDiseaseByConceptCode = async (conceptCode) => {
  if (!conceptCode) {
    return null
  }
  const found = (await loadDiseaseSearchIndex()).find((item) => item.conceptCode === conceptCode)
  return found || null
}

const clearDiseaseCaches = () => {
  categoryChildrenCache.clear()
  diseaseSearchIndex.value = []
  diseaseSearchResults.value = []
  searchPerformed.value = false
  searchLoading.value = false
}

const splitSearchTokens = (text) => String(text || '')
  .split(/[\s,，;；、]+/)
  .map((item) => item.trim().toLowerCase())
  .filter(Boolean)

const sanitizeCategoryCode = (value) => String(value || '').replace(/[^A-Za-z0-9_-]+/g, '_')

const enrichDiseaseNode = (item) => ({
  ...item,
  searchText: buildDiseaseSearchText(item),
})

const buildDiseaseSearchText = (item) => [
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

const displayAliases = (item) => (item?.aliases || []).filter(Boolean).slice(0, 8)
const previewAliases = (item) => (item?.aliases || []).filter(Boolean).slice(0, 2)
const mappingTypeLabel = (value) => mappingTypeOptions.find((item) => item.value === value)?.label || '症状 + 主诉'
const resolveErrorMessage = (error, fallback) => error?.response?.data?.message || fallback
const formatCount = (value) => Number(value || 0).toLocaleString('zh-CN')
</script>

<style scoped>
.medicalStandard-panel {
  display: grid;
  gap: 16px;
}

.admin-toolbar,
.admin-toolbar-actions,
.disease-search-row,
.mapping-actions,
.mapping-search-row,
.row-actions {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
}

.disease-search-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 12px;
}

.search-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  justify-content: flex-end;
}

.tree-mode-strip,
.mapping-summary-strip,
.mapping-list-head,
.mapping-editor-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.search-status-pill {
  display: inline-flex;
  align-items: center;
  min-height: 36px;
  padding: 0 12px;
  border-radius: 12px;
  background: rgba(232, 246, 246, 0.9);
  color: #2d6870;
  font-size: 12px;
  font-weight: 800;
}

.mode-pill,
.mapping-list-count {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(76, 166, 164, 0.12);
  color: #2e6c73;
  font-size: 12px;
  font-weight: 900;
}

.tree-mode-copy {
  color: #6f9197;
  font-size: 13px;
  line-height: 1.5;
}

.admin-toolbar {
  justify-content: space-between;
}

.admin-toolbar-copy h3 {
  margin: 0;
  color: #214f56;
  font-size: 22px;
}

.admin-toolbar-copy p,
.medicalStandard-card-head p,
.mapping-dialog-head p {
  margin: 8px 0 0;
  color: #6f9197;
  font-size: 13px;
  line-height: 1.6;
}

.admin-primary-button,
.admin-secondary-button {
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid rgba(96, 170, 171, 0.24);
  border-radius: 14px;
  cursor: pointer;
  font: inherit;
  font-weight: 800;
}

.admin-primary-button {
  background: linear-gradient(135deg, #4aa7a5, #66c0a8);
  color: #fff;
  box-shadow: 0 18px 28px rgba(76, 160, 157, 0.22);
}

.admin-secondary-button {
  background: rgba(243, 250, 250, 0.94);
  color: #265e67;
}

.admin-primary-button.compact,
.admin-secondary-button.compact {
  min-height: 36px;
  padding: 0 12px;
}

.admin-secondary-button.danger {
  border-color: rgba(205, 108, 108, 0.24);
  color: #9d4f4f;
  background: rgba(255, 243, 243, 0.94);
}

.admin-field {
  display: flex;
  flex: 1 1 220px;
  flex-direction: column;
  gap: 6px;
  color: #517a81;
  font-size: 12px;
  font-weight: 800;
}

.admin-field input,
.admin-field select {
  min-height: 36px;
  border: 1px solid rgba(136, 191, 193, 0.28);
  border-radius: 12px;
  background: rgba(247, 252, 252, 0.95);
  padding: 0 10px;
  color: #1f555d;
  font: inherit;
  outline: none;
}

.admin-switch-field {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #517a81;
  font-size: 13px;
  font-weight: 800;
}

.medicalStandard-grid {
  display: grid;
  grid-template-columns: minmax(360px, 0.95fr) minmax(0, 1.05fr);
  gap: 16px;
  align-items: start;
}

.medicalStandard-card {
  display: grid;
  min-width: 0;
  gap: 12px;
  padding: 16px;
  border: 1px solid rgba(145, 197, 198, 0.22);
  border-radius: 20px;
  background: rgba(248, 252, 252, 0.94);
}

.medicalStandard-card-head h4,
.mapping-dialog-head h4 {
  margin: 0;
  color: #214f56;
  font-size: 18px;
}

.medicalStandard-metrics,
.alias-chip-row,
.mapping-term-row,
.mapping-item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.medicalStandard-metrics span,
.alias-chip,
.inline-tag {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(232, 246, 246, 0.92);
  color: #2d6870;
  font-size: 12px;
  font-weight: 800;
}

.alias-chip.muted {
  color: #7f9da2;
}

.disease-tree-shell {
  max-height: 560px;
  overflow-y: auto;
  padding: 8px;
  border: 1px solid rgba(145, 197, 198, 0.2);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.62);
}

.disease-tree-widget {
  min-width: 0;
  background: transparent;
  color: #225760;
}

.disease-tree-widget :deep(.el-tree-node__content) {
  position: relative;
  min-height: 46px;
  height: auto;
  margin: 2px 0;
  border-radius: 12px;
  padding: 5px 8px;
  transition: background-color 160ms ease, box-shadow 160ms ease, transform 160ms ease;
}

.disease-tree-widget :deep(.el-tree-node__content:hover),
.disease-tree-widget :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: rgba(232, 247, 246, 0.95);
  box-shadow: inset 0 0 0 1px rgba(88, 170, 168, 0.18);
}

.disease-tree-widget :deep(.el-tree-node.is-current > .el-tree-node__content::before) {
  content: '';
  position: absolute;
  left: -8px;
  top: 7px;
  bottom: 7px;
  width: 4px;
  border-radius: 999px;
  background: linear-gradient(180deg, #4aa7a5, #66c0a8);
  box-shadow: 0 0 0 1px rgba(80, 168, 165, 0.08);
}

.disease-tree-widget :deep(.el-tree-node__content:hover) {
  transform: translateX(2px);
}

.disease-tree-widget :deep(.el-tree-node__expand-icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-right: 6px;
  border-radius: 999px;
  background: rgba(232, 246, 246, 0.92);
  color: #5d8f95;
  transition: transform 180ms ease, background-color 180ms ease, color 180ms ease;
}

.disease-tree-widget :deep(.el-tree-node__expand-icon.expanded) {
  background: rgba(76, 166, 164, 0.14);
  color: #2d6f76;
  transform: rotate(90deg);
}

.disease-tree-widget :deep(.el-tree-node__expand-icon.is-leaf) {
  background: transparent;
  color: transparent;
}

.disease-tree-widget :deep(.el-tree-node__children) {
  margin-left: 12px;
  padding-left: 10px;
  border-left: 1px solid rgba(106, 178, 179, 0.2);
}

.disease-tree-node,
.selected-disease-title,
.mapping-head,
.mapping-dialog-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.disease-tree-node {
  width: 100%;
  min-width: 0;
}

.disease-tree-node div,
.selected-disease-title div,
.mapping-target-card,
.mapping-item-main {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.disease-tree-main {
  align-content: start;
}

.tree-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: nowrap;
}

.disease-tree-node strong,
.mapping-item-main strong {
  overflow: hidden;
  color: #1d4d54;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.disease-tree-node.category strong {
  font-weight: 900;
}

.tree-node-badge {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  min-height: 20px;
  padding: 0 8px;
  flex: 0 0 auto;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.02em;
}

.tree-node-badge.category {
  background: rgba(76, 166, 164, 0.14);
  color: #2d6f76;
}

.tree-node-badge.disease {
  background: rgba(98, 134, 201, 0.12);
  color: #446a9c;
}

.disease-tree-node span,
.disease-tree-node small,
.selected-disease-title span,
.selected-disease-title small,
.selected-disease-meta span,
.mapping-target-card span,
.mapping-target-card small,
.mapping-item-card span {
  color: #6f9197;
  font-size: 12px;
}

.selected-disease-card,
.mapping-target-card,
.mapping-editor-card,
.mapping-empty-state {
  display: grid;
  gap: 10px;
  padding: 12px;
  border-radius: 18px;
  background: rgba(243, 250, 250, 0.82);
}

.selected-disease-card {
  border: 1px solid rgba(91, 174, 170, 0.2);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.mapping-summary-strip span {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(232, 246, 246, 0.92);
  color: #2d6870;
  font-size: 12px;
  font-weight: 800;
}

.selected-disease-title strong,
.mapping-target-card strong {
  color: #173f46;
  font-size: 20px;
}

.selected-disease-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.mapping-count-pill {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 11px;
  border-radius: 999px;
  background: rgba(232, 246, 246, 0.92);
  color: #2d6870;
  font-size: 12px;
  font-weight: 900;
}

.mapping-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.mapping-editor-head strong,
.mapping-list-head strong {
  color: #1d4d54;
  font-size: 15px;
}

.mapping-editor-head div,
.mapping-list-head div {
  display: grid;
  gap: 4px;
}

.mapping-editor-head span,
.mapping-list-head span {
  color: #6f9197;
  font-size: 12px;
}

.mapping-inline-list,
.mapping-dialog-list {
  display: grid;
  gap: 10px;
}

.mapping-inline-shell {
  display: grid;
  gap: 0;
  border: 1px solid rgba(145, 197, 198, 0.2);
  border-radius: 18px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.72);
}

.mapping-inline-header {
  position: sticky;
  top: 0;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) 0.55fr 0.45fr 0.35fr;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid rgba(145, 197, 198, 0.18);
  background: rgba(238, 248, 248, 0.98);
  color: #62868c;
  font-size: 12px;
  font-weight: 900;
}

.mapping-inline-list {
  max-height: 430px;
  overflow-y: auto;
  padding: 8px;
}

.mapping-inline-row {
  display: block;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid rgba(145, 197, 198, 0.22);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.9);
  color: #225760;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.mapping-inline-row:hover,
.mapping-inline-row.active {
  border-color: rgba(75, 164, 164, 0.42);
  background: rgba(232, 247, 246, 0.95);
  box-shadow: 0 12px 24px rgba(60, 135, 134, 0.08);
}

.mapping-row-main {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) 0.55fr 0.45fr 0.35fr;
  gap: 10px;
  align-items: center;
}

.mapping-row-lines {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.mapping-row-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 30px;
  padding: 0 9px;
  border-radius: 10px;
  background: rgba(243, 250, 250, 0.9);
  color: #315f66;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mapping-row-line strong {
  flex: 0 0 auto;
  color: #214f56;
  font-size: 12px;
}

.mapping-item-card {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid rgba(145, 197, 198, 0.22);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.9);
  color: #225760;
}

.mapping-term-row span {
  padding: 7px 9px;
  border-radius: 12px;
  background: rgba(243, 250, 250, 0.9);
  color: #315f66;
  font-size: 13px;
}

.inline-tag {
  min-height: 26px;
  font-size: 11px;
  justify-content: center;
}

.inline-tag-active {
  background: rgba(96, 186, 130, 0.12);
  color: #2f7a49;
}

.inline-tag-strong {
  background: rgba(76, 166, 164, 0.14);
  color: #2d6f76;
}

.inline-tag-neutral {
  background: rgba(76, 166, 164, 0.12);
  color: #2e6c73;
}

.inline-tag-disabled {
  background: rgba(205, 108, 108, 0.12);
  color: #9d4f4f;
}

.mapping-empty-state {
  min-height: 360px;
  place-content: center;
  text-align: center;
}

.mapping-empty-state strong {
  color: #214f56;
  font-size: 22px;
}

.mapping-empty-state span,
.medicalStandard-empty {
  color: #6f9197;
  font-size: 13px;
}

.medicalStandard-empty {
  margin: 0;
}

.mapping-dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(23, 54, 58, 0.38);
  backdrop-filter: blur(8px);
}

.mapping-dialog {
  display: grid;
  width: min(920px, 100%);
  max-height: min(760px, 88vh);
  gap: 14px;
  overflow: hidden;
  padding: 18px;
  border: 1px solid rgba(145, 197, 198, 0.28);
  border-radius: 22px;
  background: rgba(250, 254, 254, 0.98);
  box-shadow: 0 28px 80px rgba(25, 68, 72, 0.24);
}

.mapping-dialog-head {
  padding-bottom: 4px;
  border-bottom: 1px solid rgba(145, 197, 198, 0.18);
}

.mapping-dialog-list {
  overflow-y: auto;
  padding-right: 4px;
}

@media (max-width: 1180px) {
  .medicalStandard-grid,
  .selected-disease-meta,
  .mapping-form-grid,
  .mapping-inline-header,
  .mapping-row-main {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .admin-primary-button,
  .admin-secondary-button,
  .mapping-actions,
  .mapping-search-row,
  .search-actions {
    width: 100%;
  }

  .mapping-actions button,
  .mapping-search-row button {
    flex: 1 1 100%;
  }

  .search-actions button,
  .search-status-pill {
    flex: 1 1 100%;
  }

  .mapping-inline-header {
    display: none;
  }

  .mapping-inline-list {
    padding: 8px;
  }

  .mapping-row-line {
    white-space: normal;
  }

  .tree-mode-strip,
  .mapping-summary-strip,
  .mapping-list-head,
  .mapping-editor-head {
    align-items: flex-start;
  }
}
</style>
