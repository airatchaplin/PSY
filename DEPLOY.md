# Деплой PSY на хостинг

## U1Host VDS (рекомендуемый путь)

1. Заказать VDS: [U1Host BILLmanager](https://my.u1host.com/billmgr?startform=vds) — Ubuntu 22.04/24.04, **4 vCPU / 8–16 GB RAM / ≥40 GB SSD**, публичный IP.
2. SSH: `ssh root@ВАШ_IP`
3. Установить Docker:
   ```bash
   curl -fsSL https://get.docker.com | sh
   ```
4. Клонировать и настроить:
   ```bash
   git clone https://github.com/airatchaplin/PSY.git
   cd PSY
   cp .env.example .env
   nano .env   # сложные пароли + URL с IP/доменом
   ```
5. В `.env` минимум:
   ```env
   APP_PUBLIC_URL=http://ВАШ_IP
   VITE_API_URL=http://ВАШ_IP:8081
   PSY_CORS_ORIGINS=http://ВАШ_IP
   PSY_JWT_SECRET=...длинный...
   PSY_SEED_ADMIN_PASSWORD=...сложный...
   POSTGRES_PASSWORD=...сложный...
   PSY_MINIO_ACCESS_KEY=...
   PSY_MINIO_SECRET_KEY=...
   ```
6. Запуск:
   ```bash
   docker compose up --build -d
   ```
7. Открыть `http://ВАШ_IP`, войти как `admin`, в `/admin` задать LLM.

Локальные готовые секреты — в `credentials.local.txt` (не в git).

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
