import test from 'node:test'
import assert from 'node:assert/strict'

import { compactCitationLabel, groupedCitations, normalizeCitation } from '../src/lib/chatCitation.js'
import { extractStreamPayload } from '../src/lib/chatStream.js'
import { audienceLabel, docTypeLabel, knowledgeStage, stageLabel } from '../src/lib/knowledgeUi.js'

test('knowledge stage helpers keep business-facing status mapping stable', () => {
  assert.equal(knowledgeStage('PROCESSING'), 'processing')
  assert.equal(knowledgeStage('READY'), 'ready')
  assert.equal(knowledgeStage('PUBLISHED'), 'online')
  assert.equal(knowledgeStage('ARCHIVED'), 'online')
  assert.equal(stageLabel('READY'), '待发布')
  assert.equal(stageLabel('ARCHIVED'), '已归档')
  assert.equal(docTypeLabel('DOCTOR'), '医生专家')
  assert.equal(audienceLabel('BOTH'), '通用')
})

test('citation helpers merge same-document references into a lightweight card', () => {
  const message = {
    citations: [
      normalizeCitation({
        fileHash: 'hash-1',
        documentName: '原发性肝癌诊疗指南（2024年版）',
        retrievalType: 'vector',
        snippet: '第一段内容',
      }),
      normalizeCitation({
        fileHash: 'hash-1',
        documentName: '原发性肝癌诊疗指南（2024年版）',
        retrievalType: 'keyword',
        snippet: '第二段内容',
      }),
    ],
  }

  const grouped = groupedCitations(message)
  assert.equal(grouped.length, 1)
  assert.equal(grouped[0].retrievalTypeLabel, '混合')
  assert.deepEqual(grouped[0].snippets, ['第一段内容'])
  assert.match(compactCitationLabel(grouped[0]), /混合/)
})

test('citation helpers hide heading-only snippets and support legacy excerpt fields', () => {
  const message = {
    citations: [
      normalizeCitation({
        fileHash: 'hash-1',
        documentName: '原发性肝癌指南_2024.pdf',
        retrievalType: 'keyword',
        snippet: '其他:',
        excerpt: '手术切除是早期肝癌的首选根治性方案。',
      }),
    ],
  }

  const grouped = groupedCitations(message)
  assert.equal(grouped.length, 1)
  assert.deepEqual(grouped[0].snippets, ['手术切除是早期肝癌的首选根治性方案。'])
})

test('stream payload parser extracts events and metadata without leaking markers into content', () => {
  const rawContent = [
    '先给你结论。',
    '\n\n[[AIMED_STREAM_EVENT]]{"type":"MCP","phase":"PLAN","status":"RUNNING","label":"工具规划中","detail":"正在选择工具","toolName":"","durationMs":0}',
    '\n\n[[AIMED_STREAM_EVENT]]{"type":"MCP","phase":"CALL","status":"DONE","label":"工具执行完成","detail":"天气查询完成","toolName":"weather","durationMs":130}',
    '\n\n[[AIMED_STREAM_METADATA]]{"traceId":"trace-1","provider":"LOCAL_OMLX","toolMode":"MCP","citations":[]}'
  ].join('')

  const parsed = extractStreamPayload(rawContent)

  assert.equal(parsed.content, '先给你结论。')
  assert.equal(parsed.events.length, 2)
  assert.equal(parsed.events[0].label, '工具规划中')
  assert.equal(parsed.events[1].toolName, 'weather')
  assert.equal(parsed.metadata.toolMode, 'MCP')
})
