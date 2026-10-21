const HEADING = '## 範例問題'

/**
 * The "- " bullets under the "## 範例問題" heading, as the backend ExampleQuestionParser reads them;
 * used for the quick-question chips preview of unsaved content.
 */
export function extractExampleQuestions(markdown: string): string[] {
  const questions: string[] = []
  let inSection = false
  for (const line of markdown.split(/\r\n|\r|\n/)) {
    if (line.startsWith('## ')) {
      inSection = line.trim() === HEADING
    } else if (inSection && line.startsWith('- ')) {
      const text = line.slice(2).trim()
      if (text) questions.push(text)
    }
  }
  return questions
}
