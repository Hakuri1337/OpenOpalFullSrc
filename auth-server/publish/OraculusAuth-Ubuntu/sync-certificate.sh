#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

DOMAIN_FILE="/etc/oraculus-auth/domain"
DATA_DIR="/var/lib/oraculus-auth"

[[ -r "$DOMAIN_FILE" ]] || { echo "Missing $DOMAIN_FILE" >&2; exit 1; }
DOMAIN="$(tr -d '\r\n' < "$DOMAIN_FILE")"
[[ "$DOMAIN" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]] || { echo "Invalid Oraculus domain" >&2; exit 1; }

SOURCE_DIR="/etc/letsencrypt/live/$DOMAIN"
DESTINATION_DIR="$DATA_DIR/certificates"
[[ -s "$SOURCE_DIR/fullchain.pem" && -s "$SOURCE_DIR/privkey.pem" ]] || {
  echo "Certificate files for $DOMAIN do not exist" >&2
  exit 1
}

install -d -m 0700 -o oraculus-auth -g oraculus-auth "$DESTINATION_DIR"
install -m 0600 -o oraculus-auth -g oraculus-auth "$SOURCE_DIR/fullchain.pem" "$DESTINATION_DIR/$DOMAIN.crt.new"
install -m 0600 -o oraculus-auth -g oraculus-auth "$SOURCE_DIR/privkey.pem" "$DESTINATION_DIR/$DOMAIN.key.new"
mv -f "$DESTINATION_DIR/$DOMAIN.crt.new" "$DESTINATION_DIR/$DOMAIN.crt"
mv -f "$DESTINATION_DIR/$DOMAIN.key.new" "$DESTINATION_DIR/$DOMAIN.key"

if [[ "${ORACULUS_SKIP_RESTART:-0}" != "1" ]] && systemctl is-enabled --quiet oraculus-auth.service 2>/dev/null; then
  systemctl try-restart oraculus-auth.service
fi
