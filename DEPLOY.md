# Деплой PSY на хостинг

## Что уже подготовлено

- Docker Compose: Postgres, Qdrant, MinIO, API, Web
- Секреты через `.env` (JWT, БД, MinIO, seed-пользователи)
- Авторизация: Spring Security + JWT (роли `THERAPIST` / `ADMIN`), пароли BCrypt в `app_user`
- Frontend собирается с `VITE_API_URL` под ваш домен
- Тема UI по умолчанию следует системной светлой/тёмной теме устройства

## Перед запуском на сервере

1. Скопируйте `.env.example` → `.env`
2. Задайте **сложные** пароли (Postgres, MinIO, `PSY_JWT_SECRET` ≥ 32 символов, seed admin)
3. Пропишите публичные URL:
   - `APP_PUBLIC_URL` — сайт (web)
   - `VITE_API_URL` — API
   - `PSY_CORS_ORIGINS` — origin фронта (через запятую)
4. LLM: после старта зайдите под `admin` в `/admin` — Base URL, API key, embedding model, модель чата

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
| postgres | 5444 |
| qdrant | 6333 |
| minio | 9000 / 9001 |

На проде обычно ставят reverse-proxy (Caddy/Nginx/Traefik) с TLS и проксируют 80/8081.

## Важно про пользователей

Seed только `admin` создаётся **один раз**, когда таблица `app_user` пуста.  
Смена `PSY_SEED_ADMIN_PASSWORD` после первого старта **не** перезапишет пароль — меняйте через Admin UI (новые пользователи) или на чистой БД.

## Тема

По умолчанию: «Как на устройстве» (`prefers-color-scheme`).  
В настройках можно зафиксировать светлую/тёмную или другую тему.
