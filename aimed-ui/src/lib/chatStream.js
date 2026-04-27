export const STREAM_METADATA_MARKER = '[[AIMED_STREAM_METADATA]]'
export const STREAM_EVENT_MARKER = '[[AIMED_STREAM_EVENT]]'

const skipWhitespace = (text, startIndex) => {
  let cursor = startIndex
  while (cursor < text.length && /\s/.test(text[cursor])) {
    cursor += 1
  }
  return cursor
}

const findJsonObjectEnd = (text, startIndex) => {
  let cursor = skipWhitespace(text, startIndex)
  if (cursor >= text.length || text[cursor] !== '{') {
    return -1
  }
  let depth = 0
  let inString = false
  let escaping = false
  for (let index = cursor; index < text.length; index += 1) {
    const current = text[index]
    if (inString) {
      if (escaping) {
        escaping = false
      } else if (current === '\\') {
        escaping = true
      } else if (current === '"') {
        inString = false
      }
      continue
    }
    if (current === '"') {
      inString = true
      continue
    }
    if (current === '{') {
      depth += 1
    } else if (current === '}') {
      depth -= 1
      if (depth === 0) {
        return index + 1
      }
    }
  }
  return -1
}

const extractMarkedJsonChunks = (text, marker) => {
  const chunks = []
  let searchFrom = 0
  while (searchFrom >= 0 && searchFrom < text.length) {
    const markerIndex = text.indexOf(marker, searchFrom)
    if (markerIndex < 0) {
      break
    }
    const jsonStart = markerIndex + marker.length
    const jsonEnd = findJsonObjectEnd(text, jsonStart)
    if (jsonEnd < 0) {
      break
    }
    const jsonText = text.slice(skipWhitespace(text, jsonStart), jsonEnd)
    let payload = null
    try {
      payload = JSON.parse(jsonText)
    } catch {
      break
    }
    const start = markerIndex >= 2 && text.slice(markerIndex - 2, markerIndex) === '\n\n'
      ? markerIndex - 2
      : markerIndex
    chunks.push({ start, end: jsonEnd, payload })
    searchFrom = jsonEnd
  }
  return chunks
}

const removeChunks = (text, chunks) => {
  if (!chunks.length) {
    return text
  }
  let cursor = 0
  let output = ''
  for (const chunk of chunks) {
    output += text.slice(cursor, chunk.start)
    cursor = chunk.end
  }
  output += text.slice(cursor)
  return output
}

export const extractStreamPayload = (rawContent) => {
  if (!rawContent) {
    return { content: '', metadata: null, events: [] }
  }

  const eventChunks = extractMarkedJsonChunks(rawContent, STREAM_EVENT_MARKER)
  const withoutEvents = removeChunks(rawContent, eventChunks)

  if (!withoutEvents.includes(STREAM_METADATA_MARKER)) {
    return {
      content: withoutEvents,
      metadata: null,
      events: eventChunks.map((chunk) => chunk.payload).filter(Boolean),
    }
  }

  const metadataMarkerIndex = withoutEvents.lastIndexOf(STREAM_METADATA_MARKER)
  const contentEnd = metadataMarkerIndex >= 2 && withoutEvents.slice(metadataMarkerIndex - 2, metadataMarkerIndex) === '\n\n'
    ? metadataMarkerIndex - 2
    : metadataMarkerIndex
  const content = withoutEvents.slice(0, contentEnd)
  const metadataText = withoutEvents.slice(metadataMarkerIndex + STREAM_METADATA_MARKER.length).trim()

  try {
    return {
      content,
      metadata: metadataText ? JSON.parse(metadataText) : null,
      events: eventChunks.map((chunk) => chunk.payload).filter(Boolean),
    }
  } catch {
    return {
      content: withoutEvents,
      metadata: null,
      events: eventChunks.map((chunk) => chunk.payload).filter(Boolean),
    }
  }
}
