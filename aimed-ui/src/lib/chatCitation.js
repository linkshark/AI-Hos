export const normalizeCitation = (citation) => ({
  ...citation,
  retrievalTypeLabel:
    citation?.retrievalType === 'hybrid'
      ? '混合'
      : citation?.retrievalType === 'keyword'
        ? '关键词'
        : '向量',
})

export const compactCitationLabel = (citation) => {
  const documentName = citation?.documentName || '未命名文档'
  const shortName = documentName.length > 14 ? `${documentName.slice(0, 14)}…` : documentName
  return citation?.retrievalTypeLabel ? `${shortName} · ${citation.retrievalTypeLabel}` : shortName
}

export const groupedCitations = (message) => {
  const citations = Array.isArray(message?.citations) ? message.citations : []
  const grouped = new Map()
  citations.forEach((citation) => {
    const key = citation?.fileHash || citation?.documentName || citation?.segmentId
    if (!key) {
      return
    }
    const existing = grouped.get(key)
    if (existing) {
      if (citation?.snippet && !existing.snippets.includes(citation.snippet) && existing.snippets.length < 1) {
        existing.snippets.push(citation.snippet)
      }
      if (existing.retrievalTypeLabel !== '混合' && citation?.retrievalTypeLabel && citation.retrievalTypeLabel !== existing.retrievalTypeLabel) {
        existing.retrievalTypeLabel = '混合'
      }
      return
    }
    grouped.set(key, {
      key,
      fileHash: citation?.fileHash,
      documentName: citation?.documentName || '未命名文档',
      version: citation?.version,
      updatedAt: citation?.updatedAt,
      effectiveAt: citation?.effectiveAt,
      retrievalTypeLabel: citation?.retrievalTypeLabel || '引用',
      snippets: citation?.snippet ? [citation.snippet] : [],
    })
  })
  return Array.from(grouped.values())
}
