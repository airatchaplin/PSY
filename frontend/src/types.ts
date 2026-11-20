export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'
export type SessionStatus = 'ACTIVE' | 'CLOSED'
export type MessageRole = 'THERAPIST' | 'CLIENT' | 'SYSTEM' | 'SUPERVISOR'
export type CaseSource = 'BUILTIN' | 'MANUAL' | 'AI_PROMPT' | 'AI_AUTO' | 'SHARED_COPY'

export interface TagDto {
  code: string
  labelRu: string
}

export interface Me {
  id: string
  displayName: string
  email?: string
  disclaimerAccepted: boolean
  disclaimerAcceptedAt?: string
  admin?: boolean
  roles?: string[]
  /** Internal PSY balance in ₽ */
  balanceRub?: number | null
  /** false when balanceRub < 0 */
  llmAllowed?: boolean
  /** RouterAI /credits — admins only */
  appBalanceRub?: number | null
  /** Sum of all user balances — admins only */
  usersBalanceTotalRub?: number | null
}

export interface AdminOverview {
  balanceRub?: number | null
  keyName?: string | null
  keyUsageRub?: number | null
  keyLimitRub?: number | null
  keyLimitRemainingRub?: number | null
  keyUsageMonthlyRub?: number | null
  keyLimitReset?: string | null
  keyDisabled: boolean
  currentModel: string
  baseUrl: string
  embeddingModel: string
  apiKeyMasked: string
  apiKeyConfigured: boolean
  billingError?: string | null
  activityUrl: string
  docsUrl: string
}

export interface AdminModelOption {
  id: string
  name: string
  contextLength?: number | null
  /** ₽ за 1 млн входных токенов */
  promptPer1mRub?: number | null
  /** ₽ за 1 млн выходных токенов */
  completionPer1mRub?: number | null
}

export interface PromptSummary {
  id: string
  label: string
  length: number
}

export interface PromptDetail {
  id: string
  label: string
  content: string
}

export interface AdminUsageStats {
  days: number
  from?: string | null
  to?: string | null
  granularity?: 'hour' | 'day' | string
  totalCalls: number
  totalTokens: number
  totalCostRub: number
  modelCount?: number
  availableModels?: string[]
  availableSources?: string[]
  byDay: { day: string; calls: number; tokens: number; costRub: number }[]
  byModel: { model: string; calls: number; tokens: number; costRub: number }[]
  byUser: {
    therapistId?: string | null
    displayName: string
    email?: string | null
    calls: number
    tokens: number
    costRub: number
  }[]
  bySource?: { source: string; calls: number; tokens: number; costRub: number }[]
  byClient?: {
    clientId?: string | null
    clientName: string
    clientAge?: number | null
    calls: number
    tokens: number
    costRub: number
    byPurpose: { source: string; calls: number; tokens: number; costRub: number }[]
    bySession?: {
      sessionId?: string | null
      sessionLabel: string
      startedAt?: string | null
      status?: string | null
      hasAnalysis?: boolean
      calls: number
      tokens: number
      costRub: number
      byPurpose: { source: string; calls: number; tokens: number; costRub: number }[]
    }[]
  }[]
  byGeneral?: {
    source: string
    calls: number
    tokens: number
    costRub: number
    items: {
      refId?: string | null
      label: string
      createdAt?: string | null
      calls: number
      tokens: number
      costRub: number
    }[]
  }[]
}

export type AdminUsageChartMetric = 'cost' | 'tokens' | 'calls'

export interface AdminUsageQuery {
  from?: string
  to?: string
  model?: string
  source?: string
  therapistId?: string
  days?: number
}

export interface AppUser {
  id: string
  username: string
  email?: string | null
  displayName?: string | null
  enabled: boolean
  roles: string[]
  balanceRub?: number | null
}

export interface BalanceTopUp {
  id: string
  userId: string
  username: string
  displayName?: string | null
  amountRub: number
  balanceAfterRub: number
  createdByUserId?: string | null
  createdByUsername?: string | null
  createdAt: string
}

export interface CreateAppUserPayload {
  username: string
  password: string
  email?: string
  displayName?: string
  admin?: boolean
}

export type ClientGender = 'MALE' | 'FEMALE'
/** UI / API preference: ANY = random */
export type GenderPreference = ClientGender | 'ANY'

export interface Client {
  id: string
  name: string
  age: number
  gender?: ClientGender | string | null
  presentingRequest: string
  difficulty: Difficulty
  difficultyLabelRu?: string
  behaviorTags: TagDto[] | string[]
  defenses: string[]
  personaJson: string
  status: string
  caseId?: string
  therapyProgress?: number
  coreProblem?: string | null
  activeSessionId?: string | null
  activeSessionElapsedSeconds?: number | null
  activeSessionTimerPaused?: boolean | null
  createdAt: string
  updatedAt: string
}

export interface StartReceptionPayload {
  difficulty?: Difficulty | string
  caseId?: string
  gender?: GenderPreference | string
  age?: number | null
}

export interface Session {
  id: string
  clientId: string
  status: SessionStatus
  startedAt: string
  endedAt?: string | null
  elapsedSeconds?: number
  timerPaused?: boolean
}

export interface SessionSummary {
  id: string
  clientId: string
  status: SessionStatus
  startedAt: string
  endedAt?: string | null
  elapsedSeconds?: number
  timerPaused?: boolean
  messageCount: number
  hasAnalysis: boolean
  preview: string
}

export interface Reception {
  client: Client
  session: Session
  openingLine: string
}

export interface Message {
  id: string
  sessionId?: string | null
  clientId: string
  role: MessageRole
  content: string
  createdAt: string
}

export interface Note {
  id: string
  clientId: string
  sessionId?: string | null
  content: string
  createdAt: string
  updatedAt: string
}

export interface Analysis {
  id: string
  scope: string
  clientId?: string | null
  sessionId?: string | null
  reportMd: string
  rubricJson: string
  alternativesJson?: string
  createdAt: string
}

export interface Progress {
  clientsTotal: number
  sessionsTotal: number
  sessionsClosed: number
  practiceMinutes: number
  byBehaviorTag: { tag: string; labelRu: string; sessions: number; minutes: number; avgRubric?: number | null }[]
  byDifficulty: { difficulty: string; labelRu?: string; clients: number; sessions: number }[]
  rubricAverages: { dimension: string; average: number }[]
  weakZones: string[]
  recentClients: {
    id: string
    name: string
    age: number
    behaviorTags: TagDto[] | string[]
    sessions: number
    therapyProgress: number
  }[]
}

export interface CaseItem {
  id: string
  slug?: string | null
  title: string
  presentingRequest: string
  difficulty: Difficulty
  difficultyLabelRu?: string
  suggestedTags: TagDto[] | string[]
  notes?: string | null
  source?: CaseSource
  createdAt?: string
  mine?: boolean
  builtin?: boolean
  canShare?: boolean
  visibilityLabel?: string
}

export interface TherapistBrief {
  id: string
  displayName: string
  email?: string | null
}

export interface ShareCaseResult {
  sharedCount: number
  recipients: string[]
}

export interface CaseShareLink {
  token: string
  path: string
}

export interface ImportSharedCaseResult {
  caseItem: CaseItem
  alreadyHad: boolean
}

export interface CreateCasePayload {
  title: string
  presentingRequest: string
  difficulty?: Difficulty
  suggestedTags?: string[]
  notes?: string
}
