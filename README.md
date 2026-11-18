# PSY — симулятор терапевтической практики

Учебное приложение для практики гештальт-сессий: ИИ-клиент, ИИ-супервизор, история в Postgres, RAG в Qdrant, Spring Security JWT, Vue.

Спецификация для агентов: [`AGENTS.md`](AGENTS.md).

## Быстрый старт

```bash
cp .env.example .env
# задайте сильные пароли и публичные URL (см. DEPLOY.md)
docker compose up --build
```

Деплой на хостинг: [`DEPLOY.md`](DEPLOY.md). Локальные пароли — в `credentials.local.txt` (не в git).

- Web: http://localhost
- API: http://localhost:8081
- Postgres: localhost:5444
- Логин: seed `admin` из `.env` (`PSY_SEED_ADMIN_*`)
- Тема UI по умолчанию следует светлой/тёмной теме устройства

Секреты — в `.env`. LLM (Base URL / API key / модели) — только в Admin UI (`/admin`).

### Dev frontend отдельно

```bash
cd frontend
npm install
npm run dev
```

Нужны поднятые `postgres`, `qdrant`, `minio` (или `docker compose up postgres qdrant minio`).
