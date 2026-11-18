# PSY — симулятор терапевтической практики

Учебное приложение для практики гештальт-сессий: ИИ-клиент, ИИ-супервизор, история в Postgres, RAG в Qdrant, Keycloak, Vue.

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
- Vault: http://localhost:8200 (token из `VAULT_TOKEN`)
- Postgres: localhost:5444
- Keycloak: http://localhost:8082 (логин/пароль из `.env`)
- Тема UI по умолчанию следует светлой/тёмной теме устройства

Секреты сидятся в Vault (`secret/psy`). LLM Base URL / API key / модель — в Admin UI (`/admin`).

### Dev frontend отдельно

```bash
cd frontend
npm install
npm run dev
```

Нужны поднятые `postgres`, `qdrant`, `keycloak`, `vault`, `api` (или `docker compose up postgres qdrant keycloak vault vault-init api`).
