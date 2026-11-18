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
