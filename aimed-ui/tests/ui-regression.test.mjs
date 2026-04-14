import test from 'node:test'
import assert from 'node:assert/strict'

import { compactCitationLabel, groupedCitations, normalizeCitation } from '../src/lib/chatCitation.js'
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
