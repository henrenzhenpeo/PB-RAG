import DOMPurify from 'dompurify'
import { marked } from 'marked'

export function renderUntrustedMarkdown(source: string) {
  const html = marked.parse(source, { async: false })
  return DOMPurify.sanitize(html)
}
