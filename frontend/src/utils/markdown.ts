import { rubricLabel } from '../labels'

const DUPLICATE_SECTION_RE =
  /(?:^|\n)##\s*(Оценка по шкалам|Сильные стороны(?:\s+доступного материала)?|Зоны роста(?:\s+терапевта)?|Альтернативные ответы|Заметки по безопасности|Рубрика)\b[\s\S]*?(?=\n##\s|$)/gi

const SCORE_LINE_RE =
  /`?(contact_quality|phenomenological_attention|figure_ground|experiment_use|boundary_awareness|empathy_presence|question_vs_interpretation_balance)`?/gi

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function inlineFormat(text: string): string {
  return escapeHtml(text)
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
}

/** Drop structured sections that the UI already shows separately. */
export function stripStructuredSections(md: string): string {
  return md.replace(DUPLICATE_SECTION_RE, '\n').replace(/\n{3,}/g, '\n\n').trim()
}

/** Replace English rubric keys with Russian labels in prose. */
export function localizeRubricKeys(md: string): string {
  return md.replace(SCORE_LINE_RE, (_, key: string) => rubricLabel(key))
}

export function renderMarkdown(md: string): string {
  const prepared = localizeRubricKeys(stripStructuredSections(md || ''))
  if (!prepared) return ''

  const lines = prepared.replace(/\r\n/g, '\n').split('\n')
  const html: string[] = []
  let inUl = false
  let inOl = false
  let paragraph: string[] = []

  const closeLists = () => {
    if (inUl) {
      html.push('</ul>')
      inUl = false
    }
    if (inOl) {
      html.push('</ol>')
      inOl = false
    }
  }

  const flushParagraph = () => {
    if (!paragraph.length) return
    html.push(`<p>${inlineFormat(paragraph.join(' '))}</p>`)
    paragraph = []
  }

  for (const raw of lines) {
    const line = raw.trimEnd()
    const trimmed = line.trim()

    if (!trimmed) {
      flushParagraph()
      closeLists()
      continue
    }

    const heading = /^(#{1,3})\s+(.+)$/.exec(trimmed)
    if (heading) {
      flushParagraph()
      closeLists()
      const level = heading[1].length
      html.push(`<h${level}>${inlineFormat(heading[2])}</h${level}>`)
      continue
    }

    const ul = /^[-*•]\s+(.+)$/.exec(trimmed)
    if (ul) {
      flushParagraph()
      if (inOl) {
        html.push('</ol>')
        inOl = false
      }
      if (!inUl) {
        html.push('<ul>')
        inUl = true
      }
      html.push(`<li>${inlineFormat(ul[1])}</li>`)
      continue
    }

    const ol = /^\d+[.)]\s+(.+)$/.exec(trimmed)
    if (ol) {
      flushParagraph()
      if (inUl) {
        html.push('</ul>')
        inUl = false
      }
      if (!inOl) {
        html.push('<ol>')
        inOl = true
      }
      html.push(`<li>${inlineFormat(ol[1])}</li>`)
      continue
    }

    closeLists()
    paragraph.push(trimmed)
  }

  flushParagraph()
  closeLists()
  return html.join('\n')
}
