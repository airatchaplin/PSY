# Деплой PSY на хостинг

## Что уже подготовлено

- Docker Compose: Postgres, Qdrant, MinIO, Keycloak, Vault, API, Web
- Секреты через `.env` + Vault (`secret/psy`)
- Keycloak realm собирается из шаблона с паролями из env
- Frontend собирается с `VITE_*` URL под ваш домен
- Тема UI по умолчанию следует системной светлой/тёмной теме устройства

## Перед запуском на сервере

1. Скопируйте `.env.example` → `.env`
2. Задайте **сложные** пароли (`VAULT_TOKEN`, Postgres, Keycloak, MinIO)
3. Пропишите публичные URL:
   - `APP_PUBLIC_URL` — сайт (web)
   - `VITE_API_URL` — API
   - `VITE_KEYCLOAK_URL` — Keycloak
   - `PSY_CORS_ORIGINS` — origin фронта (через запятую)
4. LLM: после старта зайдите под `admin` в `/admin` и сохраните Base URL + API key

Локально сгенерированные пароли лежат в `credentials.local.txt` (не коммитится).

## Запуск

```bash
cp .env.example .env
# отредактируйте .env
docker compose up --build -d
```

Сервисы:

| Сервис | Порт (host) |
|--------|-------------|
| web | 80 |
| api | 8081 |
| keycloak | 8082 |
| vault | 8200 |
| postgres | 5444 |
| qdrant | 6333 |
| minio | 9000 / 9001 |

На проде обычно ставят reverse-proxy (Caddy/Nginx/Traefik) с TLS и проксируют 80/8081/8082.

## Важно про Keycloak

Realm импортируется **один раз** при первом старте пустого Keycloak.  
Смена паролей пользователей в `.env` после первого импорта **не** перезапишет уже созданных пользователей — меняйте их в Keycloak Admin.

## Тема

По умолчанию: «Как на устройстве» (`prefers-color-scheme`).  
В настройках можно зафиксировать светлую/тёмную или другую тему.
