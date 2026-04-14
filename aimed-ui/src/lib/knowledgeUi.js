export const docTypeOptions = [
  { value: 'HOSPITAL_OVERVIEW', label: '医院总览' },
  { value: 'DEPARTMENT', label: '科室信息' },
  { value: 'DOCTOR', label: '医生专家' },
  { value: 'GUIDE', label: '指南规范' },
  { value: 'PROCESS', label: '流程服务' },
  { value: 'NOTICE', label: '公告通知' },
  { value: 'DEVICE', label: '设备能力' },
]

export const audienceOptions = [
  { value: 'PATIENT', label: '患者' },
  { value: 'DOCTOR', label: '医生' },
  { value: 'BOTH', label: '通用' },
]

export const knowledgeStage = (status) => {
  if (status === 'READY') {
    return 'ready'
  }
  if (status === 'PUBLISHED' || status === 'ARCHIVED') {
    return 'online'
  }
  return 'processing'
}

export const stageLabel = (status) => {
  const stage = knowledgeStage(status)
  if (stage === 'processing') {
    return '处理中'
  }
  if (stage === 'ready') {
    return '待发布'
  }
  return status === 'ARCHIVED' ? '已归档' : '已上线'
}

export const docTypeLabel = (value) =>
  docTypeOptions.find((item) => item.value === value)?.label || '未分类'

export const audienceLabel = (value) =>
  audienceOptions.find((item) => item.value === value)?.label || '通用'
