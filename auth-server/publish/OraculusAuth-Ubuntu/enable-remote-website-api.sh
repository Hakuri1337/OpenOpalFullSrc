#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# Enables the encrypted, IP-restricted Auth bridge used by a separately hosted
# Oraculus website. Run on the Ubuntu Auth host as root after deploying the
# matching current auth-server source.

WEBSITE_IP="${1:-160.202.238.53}"
WEBSITE_SECRET="${2:-}"
CONFIG_PATH="${ORACULUS_AUTH_CONFIG:-/etc/oraculus-auth/server.json}"
APP_DIR="${ORACULUS_AUTH_APP_DIR:-/opt/oraculus-auth}"
NODE_BIN="${APP_DIR}/runtime/bin/node"
SERVICE_NAME="${ORACULUS_AUTH_SERVICE:-oraculus-auth.service}"
PORT=3101

fail() { echo "ERROR: $*" >&2; exit 1; }
[[ ${EUID} -eq 0 ]] || fail "Run as root: sudo bash enable-remote-website-api.sh"
[[ "$WEBSITE_IP" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || fail "Website IPv4 is invalid"
[[ -x "$NODE_BIN" ]] || fail "Node runtime not found: $NODE_BIN"
[[ -f "$CONFIG_PATH" ]] || fail "Auth configuration not found: $CONFIG_PATH"
grep -q 'InternalWebAllowedIps' "$APP_DIR/server.js" || fail "The installed Auth server is too old. Deploy the current auth-server package first."

if [[ -z "$WEBSITE_SECRET" ]]; then
  WEBSITE_SECRET="$(openssl rand -base64 48 | tr -d '\r\n')"
fi
[[ ${#WEBSITE_SECRET} -ge 32 ]] || fail "Website secret must be at least 32 characters"

TMP_CONFIG="$(mktemp "${CONFIG_PATH}.new.XXXXXX")"
trap 'rm -f -- "$TMP_CONFIG"' EXIT
WEBSITE_IP="$WEBSITE_IP" WEBSITE_SECRET="$WEBSITE_SECRET" "$NODE_BIN" - "$CONFIG_PATH" > "$TMP_CONFIG" <<'NODE'
const fs = require('fs');
const config = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
config.InternalWebsiteSecret = process.env.WEBSITE_SECRET;
config.InternalWebHost = '0.0.0.0';
config.InternalWebPort = 3101;
config.InternalWebTls = true;
config.InternalWebAllowedIps = [process.env.WEBSITE_IP];
process.stdout.write(JSON.stringify(config, null, 2) + '\n');
NODE
chown root:oraculus-auth "$TMP_CONFIG" 2>/dev/null || chown root:root "$TMP_CONFIG"
chmod 0640 "$TMP_CONFIG"
mv -- "$TMP_CONFIG" "$CONFIG_PATH"

if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q '^Status: active'; then
  ufw allow from "$WEBSITE_IP" to any port "$PORT" proto tcp
fi

"$NODE_BIN" "$APP_DIR/server.js" --config "$CONFIG_PATH" --check-config
systemctl restart "$SERVICE_NAME"
sleep 1
ss -H -ltnp | grep -Eq "[:.]${PORT}[[:space:]]" || fail "Auth did not bind TCP $PORT"

cat <<RESULT

Remote website Auth bridge is enabled.
Website IP: $WEBSITE_IP
Auth bridge URL: https://auth.hakuri.tech:$PORT
Website secret (copy it into the Windows website installer when prompted):
$WEBSITE_SECRET

Also restrict TCP $PORT to $WEBSITE_IP in your cloud firewall/security group.
RESULT
