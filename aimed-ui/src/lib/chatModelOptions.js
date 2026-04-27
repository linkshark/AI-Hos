export const LOCAL_OMLX = 'LOCAL_OMLX'
export const LOCAL_OLLAMA = 'LOCAL_OLLAMA'
export const QWEN_ONLINE = 'QWEN_ONLINE'
export const QWEN_ONLINE_FAST = 'QWEN_ONLINE_FAST'
export const QWEN_ONLINE_DEEP = 'QWEN_ONLINE_DEEP'

export const modelProviderOptions = [
  {
    value: LOCAL_OMLX,
    label: '本地 Qwen3.6-35B',
    shortLabel: 'Qwen3.6-35B',
    chipLabel: '本地 · Qwen3.6-35B',
    menuTitle: 'Qwen3.6-35B',
    menuSubtitle: '适合院内基础问答和隐私内容处理',
    modeLabel: '本地',
    modeShortLabel: '本地',
    logoKind: 'local',
    description: '适合院内基础问答和隐私内容处理',
  },
  {
    value: QWEN_ONLINE_FAST,
    label: '在线 qwen-flash',
    shortLabel: 'qwen-flash',
    chipLabel: '在线快答 · qwen-flash',
    menuTitle: 'qwen-flash',
    menuSubtitle: '更适合低延迟问答、普通症状咨询和院内信息查询',
    modeLabel: '在线快答',
    modeShortLabel: '快答',
    logoKind: 'fast',
    description: '更适合低延迟问答、普通症状咨询和院内信息查询',
  },
  {
    value: QWEN_ONLINE_DEEP,
    label: '在线 qwen3.6-plus',
    shortLabel: 'qwen3.6-plus',
    chipLabel: '在线深答 · qwen3.6-plus',
    menuTitle: 'qwen3.6-plus',
    menuSubtitle: '更适合复杂总结、多资料整合和长回答',
    modeLabel: '在线深答',
    modeShortLabel: '深答',
    logoKind: 'deep',
    description: '更适合复杂总结、多资料整合和长回答',
  },
]

export const normalizeStoredProvider = (storedProvider) => {
  if (storedProvider === QWEN_ONLINE || storedProvider === QWEN_ONLINE_FAST) {
    return QWEN_ONLINE_FAST
  }
  if (storedProvider === QWEN_ONLINE_DEEP) {
    return QWEN_ONLINE_DEEP
  }
  return LOCAL_OMLX
}

export const resolveModelProviderTip = (provider) => {
  if (provider === LOCAL_OMLX) {
    return '本地模型适合院内基础问答和隐私内容处理。'
  }
  if (provider === QWEN_ONLINE_FAST) {
    return '在线快答适合普通问答和低延迟场景。'
  }
  return '在线深答更适合复杂病情说明、长总结和多资料综合判断。'
}

export const resolveComposerPlaceholder = (provider, isMobileViewport) => {
  if (isMobileViewport) {
    if (provider === LOCAL_OMLX) {
      return '直接提问或短句确认，回车发送'
    }
    if (provider === QWEN_ONLINE_FAST) {
      return '输入普通症状或院内问题，回车发送'
    }
    return '输入复杂问题或多资料需求，回车发送'
  }
  if (provider === LOCAL_OMLX) {
    return '帮我概括这份资料的重点，结论尽量简短'
  }
  if (provider === QWEN_ONLINE_FAST) {
    return '脑袋有点疼、鼻塞，应该先怎么处理'
  }
  return '请综合最新上传资料，说明诊疗要点、风险和下一步建议'
}
