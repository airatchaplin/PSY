#!/bin/sh
set -eu

VAULT_ADDR="${VAULT_ADDR:-http://vault:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-psy-local-root}"
export VAULT_ADDR VAULT_TOKEN

echo "Waiting for Vault at ${VAULT_ADDR}..."
i=0
until vault status >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    echo "Vault did not become ready in time" >&2
    exit 1
  fi
  sleep 1
done

echo "Seeding secret/psy..."

# Dev server already enables KV v2 at "secret/".
# Non-secret LLM defaults (base-url, model, embedding-model) stay in application.yml / admin UI.
if [ -n "${PSY_LLM_API_KEY:-}" ]; then
  vault kv put secret/psy \
    "spring.datasource.password=${POSTGRES_PASSWORD:-psy}" \
    "psy.minio.secret-key=${PSY_MINIO_SECRET_KEY:-psysecret}" \
    "psy.keycloak.admin-password=${KEYCLOAK_ADMIN_PASSWORD:-admin}" \
    "psy.llm.api-key=${PSY_LLM_API_KEY}"
else
  vault kv put secret/psy \
    "spring.datasource.password=${POSTGRES_PASSWORD:-psy}" \
    "psy.minio.secret-key=${PSY_MINIO_SECRET_KEY:-psysecret}" \
    "psy.keycloak.admin-password=${KEYCLOAK_ADMIN_PASSWORD:-admin}"
fi

echo "Vault seed complete."
vault kv get secret/psy >/dev/null
echo "secret/psy OK"
