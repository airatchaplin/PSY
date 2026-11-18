#!/bin/sh
set -eu

TEMPLATE="/opt/keycloak/data/import-src/realm-psy.json.template"
TARGET="/opt/keycloak/data/import/realm-psy.json"

THERAPIST_PW="${KEYCLOAK_THERAPIST_PASSWORD:-therapist}"
ADMIN_USER_PW="${KEYCLOAK_USER_ADMIN_PASSWORD:-admin}"
API_SECRET="${PSY_API_CLIENT_SECRET:-psy-api-secret}"
APP_URL="${APP_PUBLIC_URL:-http://localhost}"

# Escape sed replacement specials: \ & |
esc() {
  printf '%s' "$1" | sed -e 's/[\\|&]/\\&/g'
}

mkdir -p /opt/keycloak/data/import
sed \
  -e "s|__KEYCLOAK_THERAPIST_PASSWORD__|$(esc "$THERAPIST_PW")|g" \
  -e "s|__KEYCLOAK_USER_ADMIN_PASSWORD__|$(esc "$ADMIN_USER_PW")|g" \
  -e "s|__PSY_API_CLIENT_SECRET__|$(esc "$API_SECRET")|g" \
  -e "s|__APP_PUBLIC_URL__|$(esc "$APP_URL")|g" \
  "$TEMPLATE" > "$TARGET"

echo "Keycloak realm rendered for APP_PUBLIC_URL=${APP_URL}"
exec /opt/keycloak/bin/kc.sh "$@"
