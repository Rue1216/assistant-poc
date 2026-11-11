import { describe, expect, it } from 'vitest'
import { extractExampleQuestions } from '@/utils/markdown/example-questions'

describe('extractExampleQuestions', () => {
  it('returns the bullets under ## 範例問題 only', () => {
    const md = [
      '## 適用問題',
      '- 不是範例',
      '',
      '## 範例問題',
      '',
      '- 整理近期互動，幫我準備下次聯繫重點',
      '-   上次跟這位顧客聊了什麼？  ',
      '1. 編號清單不算',
      '',
      '## 注意事項',
      '- 也不是範例',
    ].join('\n')
    expect(extractExampleQuestions(md)).toEqual(['整理近期互動，幫我準備下次聯繫重點', '上次跟這位顧客聊了什麼？'])
  })

  it('returns an empty list without the section or with empty bullets', () => {
    expect(extractExampleQuestions('## 適用問題\n- a')).toEqual([])
    expect(extractExampleQuestions('## 範例問題\n- \n-')).toEqual([])
    expect(extractExampleQuestions('')).toEqual([])
  })

  it('ignores ### sub-headings as section ends and accepts CRLF', () => {
    expect(extractExampleQuestions('## 範例問題\r\n### 小節\r\n- 問題一\r\n')).toEqual(['問題一'])
  })
})
