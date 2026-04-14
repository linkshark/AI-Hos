function normalizeMessageMarkdown(message) {
  if (typeof message?.rawContent === 'string' && message.rawContent.trim()) {
    return normalizeMarkdown(message.rawContent)
  }
  if (typeof message?.content === 'string' && message.content.trim()) {
    return normalizeMarkdown(htmlToMarkdown(message.content))
  }
  return '（无内容）'
}

function formatAttachmentSize(bytes) {
  const normalized = Number(bytes || 0)
  if (!Number.isFinite(normalized) || normalized <= 0) {
    return ''
  }
  if (normalized < 1024) {
    return `${normalized} B`
  }
  if (normalized < 1024 * 1024) {
    return `${(normalized / 1024).toFixed(normalized >= 1024 * 100 ? 0 : 1)} KB`
  }
  return `${(normalized / 1024 / 1024).toFixed(2)} MB`
}

function normalizeAttachmentMarkdown(message) {
  const attachments = Array.isArray(message?.attachments) ? message.attachments : []
  if (!attachments.length) {
    return ''
  }
  const lines = attachments.map((attachment) => {
    const kind = attachment?.kindLabel || ((attachment?.isImage || attachment?.image) ? '图片' : '文件')
    const name = attachment?.name || '未命名附件'
    const sizeText = formatAttachmentSize(attachment?.size)
    return `- ${kind}：${name}${sizeText ? `（${sizeText}）` : ''}`
  })
  return `### 附件\n\n${lines.join('\n')}`
}

function normalizeMarkdown(value) {
  return String(value || '')
    .replace(/\r\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function htmlToMarkdown(html) {
  if (!html || typeof DOMParser === 'undefined') {
    return ''
  }

  const documentNode = new DOMParser().parseFromString(html, 'text/html')
  const blocks = Array.from(documentNode.body.childNodes)
    .map((node) => nodeToMarkdown(node).trimEnd())
    .filter(Boolean)

  return blocks.join('\n\n')
}

function nodeToMarkdown(node, depth = 0) {
  if (!node) {
    return ''
  }

  if (node.nodeType === Node.TEXT_NODE) {
    return node.textContent?.replace(/\s+/g, ' ') || ''
  }

  if (node.nodeType !== Node.ELEMENT_NODE) {
    return ''
  }

  const tag = node.tagName.toLowerCase()
  const children = Array.from(node.childNodes).map((child) => nodeToMarkdown(child, depth + 1)).join('')

  switch (tag) {
    case 'br':
      return '\n'
    case 'strong':
    case 'b':
      return `**${children.trim()}**`
    case 'em':
    case 'i':
      return `*${children.trim()}*`
    case 'code':
      return `\`${children.trim()}\``
    case 'pre':
      return `\`\`\`\n${node.textContent?.trim() || ''}\n\`\`\``
    case 'a': {
      const href = node.getAttribute('href') || ''
      return href ? `[${children.trim() || href}](${href})` : children
    }
    case 'h1':
      return `# ${children.trim()}`
    case 'h2':
      return `## ${children.trim()}`
    case 'h3':
      return `### ${children.trim()}`
    case 'h4':
      return `#### ${children.trim()}`
    case 'h5':
      return `##### ${children.trim()}`
    case 'h6':
      return `###### ${children.trim()}`
    case 'li': {
      const prefix = `${'  '.repeat(Math.max(0, depth - 2))}- `
      return `${prefix}${children.trim()}`
    }
    case 'ul':
    case 'ol':
      return Array.from(node.children).map((child) => nodeToMarkdown(child, depth + 1)).filter(Boolean).join('\n')
    case 'blockquote':
      return children
        .split('\n')
        .filter(Boolean)
        .map((line) => `> ${line}`)
        .join('\n')
    case 'p':
    case 'div':
    case 'section':
      return children.trim()
    default:
      return children
  }
}

function formatExportTime(date) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

function formatFileTime(date) {
  const parts = new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(date)

  const lookup = Object.fromEntries(parts.map((item) => [item.type, item.value]))
  return `${lookup.year}${lookup.month}${lookup.day}-${lookup.hour}${lookup.minute}${lookup.second}`
}

export function buildChatMarkdown({ messages, memoryId, modelLabel, sessionTitle, exportedAt = new Date() }) {
  const chatMessages = (Array.isArray(messages) ? messages : [])
    .filter((message) => message?.kind !== 'system')
    .filter((message) => message?.isUser === true || message?.isUser === false)

  const sections = chatMessages.map((message) => {
    const roleTitle = message.isUser ? '## 用户' : '## 树兰智能助手'
    const body = normalizeMessageMarkdown(message)
    const attachments = normalizeAttachmentMarkdown(message)
    return [roleTitle, body, attachments].filter(Boolean).join('\n\n')
  })

  return [
    '# 杭州树兰医院 AI 对话记录',
    '',
    `> 会话标题：${sessionTitle || `#${memoryId || '--'}`}`,
    `> 导出时间：${formatExportTime(exportedAt)}`,
    `> 当前会话：#${memoryId || '--'}`,
    `> 当前模型：${modelLabel || '未知模型'}`,
    '',
    '---',
    '',
    sections.length ? sections.join('\n\n---\n\n') : '## 树兰智能助手\n\n（当前会话暂无可导出的正式问答内容）',
    '',
  ].join('\n')
}

function sanitizeFilenameSegment(value) {
  return String(value || '')
    .replace(/[\\/:*?"<>|]/g, '-')
    .replace(/\s+/g, ' ')
    .trim()
}

export function downloadChatMarkdown({ messages, memoryId, modelLabel, sessionTitle }) {
  const exportedAt = new Date()
  const markdown = buildChatMarkdown({ messages, memoryId, modelLabel, sessionTitle, exportedAt })
  const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  const titleSegment = sanitizeFilenameSegment(sessionTitle)
  anchor.download = titleSegment
    ? `aimed-chat-${titleSegment}-${formatFileTime(exportedAt)}.md`
    : `aimed-chat-${memoryId || 'session'}-${formatFileTime(exportedAt)}.md`
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}
