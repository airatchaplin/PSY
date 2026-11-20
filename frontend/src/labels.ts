import type { TagDto } from './types'

const BEHAVIOR_TAGS_RU: Record<string, string> = {
  ANXIOUS: 'тревожный',
  AVOIDANT: 'избегающий',
  RATIONALIZING: 'рационализирующий',
  BOUNDARY_TESTING: 'тестирующий границы',
  SHAME_PRONE: 'стыдящийся',
  AFFECTIVE_CRYING: 'аффектный / плачущий',
  DEVALUING: 'обесценивающий',
  FUSED_DEPENDENT: 'слипшийся / зависимый',
  INTELLECTUALIZING: 'интеллектуализирующий'
}

const DIFFICULTY_RU: Record<string, string> = {
  EASY: 'лёгкий',
  MEDIUM: 'средний',
  HARD: 'сложный'
}

const RUBRIC_DIMENSIONS_RU: Record<string, string> = {
  contact_quality: 'Качество контакта',
  phenomenological_attention: 'Феноменологическое внимание',
  figure_ground: 'Фигура / фон',
  experiment_use: 'Использование эксперимента',
  boundary_awareness: 'Осознание границ',
  empathy_presence: 'Эмпатия и присутствие',
  question_vs_interpretation_balance: 'Баланс вопросов и интерпретаций'
}

const USAGE_SOURCE_LABELS: Record<string, string> = {
  chat: 'Чаты с клиентом',
  stream: 'Чаты с клиентом',
  supervisor: 'Супервизор',
  analysis_session: 'Анализы сеанса',
  analysis_client: 'Анализ клиента',
  analysis_global: 'Анализ тетради',
  analysis: 'Анализ',
  reception: 'Чаты с клиентом', // legacy purpose; new calls use `chat`
  session_return: 'Чаты с клиентом', // legacy purpose; new calls use `chat`
  progress: 'Прогресс терапии',
  case: 'Генерация кейса',
  embed: 'Память и поиск (векторный индекс)',
  json: 'JSON / прочее',
  prompt: 'Промпт'
}

/** Singular forms for a single session card. */
const USAGE_SOURCE_LABELS_SESSION: Record<string, string> = {
  chat: 'Чат с клиентом',
  stream: 'Чат с клиентом',
  reception: 'Чат с клиентом',
  session_return: 'Чат с клиентом',
  analysis_session: 'Анализ сеанса'
}

export type UsageSourceLabelScope = 'client' | 'session'

export function usageSourceLabel(source: string, scope: UsageSourceLabelScope = 'client'): string {
  if (scope === 'session' && USAGE_SOURCE_LABELS_SESSION[source]) {
    return USAGE_SOURCE_LABELS_SESSION[source]
  }
  return USAGE_SOURCE_LABELS[source] || source
}

export function tagLabel(tag: string | TagDto): string {
  if (tag && typeof tag === 'object') {
    return tag.labelRu || tag.code
  }
  const key = tag?.toUpperCase?.() ?? tag
  return BEHAVIOR_TAGS_RU[key] || tag
}

export function difficultyLabel(difficulty: string, labelRu?: string): string {
  if (labelRu) return labelRu
  const key = difficulty?.toUpperCase?.() ?? difficulty
  return DIFFICULTY_RU[key] || difficulty
}

export function genderLabel(gender?: string | null): string {
  if (!gender) return ''
  const key = gender.toUpperCase()
  if (key === 'MALE' || key === 'M') return 'М'
  if (key === 'FEMALE' || key === 'F') return 'Ж'
  return gender
}

export function rubricLabel(dimension: string): string {
  const key = dimension?.toLowerCase?.() ?? dimension
  return RUBRIC_DIMENSIONS_RU[key] || dimension
}

export function tagCode(tag: string | TagDto): string {
  if (tag && typeof tag === 'object') return tag.code
  return tag
}
