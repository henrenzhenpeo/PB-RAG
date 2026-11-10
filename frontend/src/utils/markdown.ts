import DOMPurify from 'dompurify'
import { marked } from 'marked'

/** 渲染模型或文档产生的不可信 Markdown，并在进入 DOM 前执行白名单清洗。 */
export function renderUntrustedMarkdown(source: string) {
  const html = marked.parse(source, { async: false })
  return DOMPurify.sanitize(html)
}
