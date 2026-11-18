# PSY — симулятор терапевтической практики

## Цель

Учебное веб-приложение для практики ведения терапевтических приёмов (гештальт, 2 ступень).  
Терапевт (человек) общается с ИИ-клиентом, может консультироваться с ИИ-супервизором/коучем в отдельной панели, завершает сеансы кнопкой и получает анализ по гештальт-рубрике.

**Дисклеймер:** это учебный симулятор. Не является реальной психотерапией, медицинской помощью или заменой супервизии у живого специалиста.

## Роли

| Роль | Кто | Где |
|------|-----|-----|
| Therapist | Человек (JWT user, роль THERAPIST) | Основной чат, заметки, анализ, прогресс |
| Client | LLM | Чат клиента; никогда не смешивается с супервизором |
| Supervisor / Coach | LLM | Отдельная панель; реплики не попадают в память клиента |

## Инварианты домена

1. **1 клиент = 1 чат-тред** в UI; история включает все сеансы клиента хронологически с разделителями.
2. У клиента **много сеансов**; одновременно не более одного `ACTIVE` session.
3. Сообщения клиента/терапевта пишутся только в `ACTIVE` session.
4. Завершение сеанса — **только кнопкой** «Завершить сеанс».
5. Супервизор видит контекст клиента/сессии; его сообщения **не** индексируются в `client_memory`.
6. Приватные заметки терапевта **не** попадают в промпт клиента.
7. Тон/поведение клиента варьируется (1–3 тега из справочника); прогресс считает статистику по тегам.

## Стек

- Java 21, Spring Boot 3.x, Flyway, Spring Security + JWT (HS256)
- PostgreSQL 16
- Qdrant (RAG)
- MinIO (промпты)
- Vue 3 + Vite + TypeScript
- Docker Compose (локальный деплой)
- LLM: OpenAI-compatible HTTP API / RouterAI — только через Admin UI (`app_setting`)

## Docker-сервисы и порты

| Сервис | Порт | Назначение |
|--------|------|------------|
| web | 5173 / 80 | Vue frontend |
| api | 8081 | Spring Boot |
| postgres | 5444 | Основная БД (host; в Docker внутри 5432) |
| qdrant | 6333 | Векторное хранилище |
| minio | 9000 / 9001 | Промпты (S3) + console |

## Модель данных (Postgres)

- `app_user` — username, BCrypt `password_hash`, roles (`THERAPIST`/`ADMIN`), enabled
- `therapist_profile` — `auth_sub` (JWT `sub` = app_user.id), display name, disclaimer_accepted_at
- `client` — персона: name, age, presenting_request, difficulty, behavior_tags[], defenses[], persona_json, status, therapist_id
- `therapy_session` — client_id, started_at, ended_at, status (`ACTIVE`/`CLOSED`)
- `message` — session_id, role (`THERAPIST`/`CLIENT`/`SYSTEM`), content, created_at
- `therapist_note` — client_id, session_id?, content, created_at
- `supervisor_thread` — therapist_id, client_id, session_id?
- `supervisor_message` — thread_id, role (`THERAPIST`/`SUPERVISOR`), content
- `session_analysis` — session_id или scope (CLIENT/GLOBAL), report_md, rubric_json, alternatives_json
- Behavior tags (справочник): anxious, avoidant, rationalizing, boundary_testing, shame_prone, affective_crying, devaluing, fused_dependent, intellectualizing

## Qdrant

1. `client_memory` — чанки сообщений/саммари; payload: clientId, sessionId, role, createdAt, therapistId
2. `gestalt_knowledge` — учебные материалы из `docs/knowledge`

Retrieval: client reply = top-k `client_memory` + last N Postgres messages.  
Supervisor/analysis = + top-k `gestalt_knowledge`.

## API (черновик)

`/api/auth/login` — публичный. Остальные `/api/**` требуют Bearer JWT.

- `POST /api/auth/login` — `{username,password}` → JWT
- `GET /api/me` — профиль; `POST /api/me/accept-disclaimer`
- `GET /api/clients` — список клиентов терапевта
- `POST /api/receptions/start` — новый клиент + ACTIVE session (опционально difficulty, caseId)
- `POST /api/clients/{id}/sessions/start` — продолжить: новый ACTIVE session
- `POST /api/sessions/{id}/end` — закрыть сеанс
- `GET /api/clients/{id}/messages` — вся история по клиенту
- `POST /api/sessions/{id}/messages` — реплика терапевта → ответ клиента (SSE stream)
- `GET/POST /api/clients/{id}/notes`
- `GET/POST /api/clients/{id}/supervisor/messages`
- `POST /api/sessions/{id}/analysis` — анализ текущего (закрытого) сеанса
- `POST /api/clients/{id}/analysis` — анализ всех сеансов клиента
- `POST /api/analysis/global` — анализ всех сеансов терапевта
- `GET /api/progress` — дашборд
- `GET /api/cases` — библиотека кейсов (базовые всем + свои/полученные)
- `GET /api/cases/peers` — коллеги для шаринга
- `POST /api/cases/{id}/share` — копия кейса выбранным / всем терапевтам
- `GET /api/clients/{id}/export` — Markdown экспорт

## Правила промптов

### Client

- Говорит от первого лица, держит персону и behavior tags.
- Не раскрывает, что он ИИ; не даёт супервизорских советов терапевту.
- Реагирует реалистично: сопротивление, аффект, уход в голову — по тегам.
- Помнит факты из RAG/истории этого клиента.

### Supervisor

- Гештальт-фрейм: цикл контакта, фигура/фон, феноменология, эксперимент, границы, полярности.
- Не отыгрывает клиента; комментирует процесс и предлагает гипотезы/формулировки.
- Тон поддерживающий, конкретный, с примерами реплик.

### Analysis rubric (JSON)

Шкалы 1–5: contact_quality, phenomenological_attention, figure_ground, experiment_use, boundary_awareness, empathy_presence, question_vs_interpretation_balance.  
Плюс: strengths[], growth_areas[], alternative_replies[{original, alternative, why}], summary_md.

## Прогресс (метрики)

- clients_total, sessions_total, sessions_closed, practice_minutes
- by_behavior_tag: sessions, minutes, avg_rubric
- by_difficulty
- weak_zones: lowest rubric dimensions across analyses
- recent_clients / streak

## Этика / безопасность

- Клиенты симуляции — взрослые (18+).
- При явных маркерах суицида/насилия клиент остаётся в роли, но супервизор и анализ подсвечивают необходимость живой супервизии и протоколов; UI показывает safety notice.
- Нет генерации сексуализированного контента с несовершеннолетними.
- Данные локальные (Docker volumes).

## Структура репозитория

```
AGENTS.md
docker-compose.yml
.env.example
backend/          # Spring Boot
frontend/         # Vue 3
docker/           # nginx и т.п.
docs/knowledge/   # seed для gestalt_knowledge
```

## Фазы реализации

1. AGENTS.md + каркас монорепо
2. Docker: Postgres, Qdrant, MinIO
3. Backend + Security (JWT) + Flyway
4. Генерация клиента, чат, завершение сеанса
5. RAG Qdrant
6. Супервизор, заметки, анализ
7. Дашборд прогресса
8. Кейсы, экспорт, knowledge seed
9. Vue UI + Compose end-to-end

## Локальный запуск

```bash
cp .env.example .env
# сильные пароли + публичные URL — см. DEPLOY.md / credentials.local.txt
docker compose up --build
```

- App: http://localhost:5173 (dev) или http://localhost (prod web)
- API: http://localhost:8081
- Postgres: localhost:5444
- MinIO: http://localhost:9000 / console :9001
- Логин: seed `admin` из `.env` (`PSY_SEED_ADMIN_*`)
- Тема: по умолчанию `system` (prefers-color-scheme → ios-light / ios-dark)
- Admin UI (`/admin`): LLM connection, баланс, промпты, usage, пользователи
- Деплой: [`DEPLOY.md`](DEPLOY.md)
