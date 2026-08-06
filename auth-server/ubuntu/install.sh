#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

DOMAIN="${ORACULUS_DOMAIN:-auth.hakuri.tech}"
EMAIL="${ORACULUS_EMAIL:-admin@${DOMAIN}}"
EXPECTED_IP="${ORACULUS_EXPECTED_IP:-}"
NODE_VERSION="22.17.1"

PACKAGE_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
APP_DIR="/opt/oraculus-auth"
CONFIG_DIR="/etc/oraculus-auth"
DATA_DIR="/var/lib/oraculus-auth"
BACKUP_ROOT="/var/backups/oraculus-auth/deployment-$(date +%Y%m%d-%H%M%S)"
RESULT_FILE="/root/oraculus-auth-deployment-result.txt"
LOG_FILE="/var/log/oraculus-auth-install-$(date +%Y%m%d-%H%M%S).log"
WORK_ROOT=""
MUTATION_STARTED=0
PREVIOUS_SERVICE_ACTIVE=0

MANAGED_PATHS=(
  "/opt/oraculus-auth"
  "/etc/oraculus-auth"
  "/etc/systemd/system/oraculus-auth.service"
  "/etc/systemd/system/oraculus-auth-renew.service"
  "/etc/systemd/system/oraculus-auth-renew.timer"
  "/usr/local/sbin/oraculus-auth-sync-certificate"
  "/etc/letsencrypt/renewal-hooks/deploy/oraculus-auth"
)
declare -A PATH_EXISTED

step() {
  printf '\n[%s] %s\n' "$(date +%H:%M:%S)" "$1"
}

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  return 1
}

safe_remove_managed_path() {
  local path="$1"
  case "$path" in
    "/opt/oraculus-auth"|"/etc/oraculus-auth"|\
    "/etc/systemd/system/oraculus-auth.service"|\
    "/etc/systemd/system/oraculus-auth-renew.service"|\
    "/etc/systemd/system/oraculus-auth-renew.timer"|\
    "/usr/local/sbin/oraculus-auth-sync-certificate"|\
    "/etc/letsencrypt/renewal-hooks/deploy/oraculus-auth")
      rm -rf -- "$path"
      ;;
    *)
      printf 'Refusing to remove unexpected rollback path: %s\n' "$path" >&2
      return 1
      ;;
  esac
}

backup_managed_paths() {
  local path destination
  install -d -m 0700 "$BACKUP_ROOT"
  for path in "${MANAGED_PATHS[@]}"; do
    destination="$BACKUP_ROOT$path"
    if [[ -e "$path" || -L "$path" ]]; then
      PATH_EXISTED["$path"]=1
      install -d -m 0700 "$(dirname -- "$destination")"
      cp -a -- "$path" "$destination"
    else
      PATH_EXISTED["$path"]=0
    fi
  done
}

rollback() {
  local exit_code="$1"
  local path source
  trap - ERR
  set +e
  if [[ "$MUTATION_STARTED" -eq 1 ]]; then
    printf '\nDeployment failed; restoring program and service configuration from %s\n' "$BACKUP_ROOT" >&2
    systemctl stop oraculus-auth.service >/dev/null 2>&1
    for path in "${MANAGED_PATHS[@]}"; do
      safe_remove_managed_path "$path"
      if [[ "${PATH_EXISTED[$path]:-0}" -eq 1 ]]; then
        source="$BACKUP_ROOT$path"
        install -d -m 0755 "$(dirname -- "$path")"
        cp -a -- "$source" "$path"
      fi
    done
    systemctl daemon-reload >/dev/null 2>&1
    if [[ "$PREVIOUS_SERVICE_ACTIVE" -eq 1 ]]; then
      systemctl start oraculus-auth.service >/dev/null 2>&1
    fi
  fi
  printf 'Deployment failed with exit code %s. Log: %s\n' "$exit_code" "$LOG_FILE" >&2
  exit "$exit_code"
}

cleanup() {
  if [[ -n "$WORK_ROOT" && -d "$WORK_ROOT" ]]; then
    rm -rf -- "$WORK_ROOT"
  fi
}

port_owners() {
  local port="$1"
  ss -H -ltnp 2>/dev/null | awk -v suffix=":${port}" '$4 ~ (suffix "$") { print }' || true
}

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run this installer as root: sudo bash install.sh" >&2
  exit 1
fi

install -d -m 0755 /var/log
touch "$LOG_FILE"
chmod 0600 "$LOG_FILE"
exec > >(tee -a "$LOG_FILE") 2>&1
trap cleanup EXIT
trap 'rollback $?' ERR

step "Checking Ubuntu, package integrity, DNS, and architecture"
[[ -r /etc/os-release ]] || fail "Cannot identify the operating system"
# shellcheck disable=SC1091
source /etc/os-release
[[ "${ID:-}" == "ubuntu" ]] || fail "This package supports Ubuntu only; detected ${ID:-unknown}"
[[ "$DOMAIN" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]] || fail "Invalid ORACULUS_DOMAIN"
[[ "$EMAIL" == *"@"* ]] || fail "Invalid ORACULUS_EMAIL"
[[ -f "$PACKAGE_DIR/payload-manifest.sha256" ]] || fail "payload-manifest.sha256 is missing"
(cd "$PACKAGE_DIR" && sha256sum --check --strict payload-manifest.sha256)

case "$(uname -m)" in
  x86_64) NODE_PLATFORM="linux-x64" ;;
  aarch64|arm64) NODE_PLATFORM="linux-arm64" ;;
  *) fail "Unsupported CPU architecture: $(uname -m)" ;;
esac

DNS_IPV4="$(getent ahostsv4 "$DOMAIN" 2>/dev/null | awk '{print $1}' | sort -u || true)"
[[ -n "$DNS_IPV4" ]] || fail "$DOMAIN has no resolvable IPv4 A record"
printf 'DNS IPv4 for %s:\n%s\n' "$DOMAIN" "$DNS_IPV4"
if [[ -n "$EXPECTED_IP" ]] && ! grep -Fxq "$EXPECTED_IP" <<< "$DNS_IPV4"; then
  fail "$DOMAIN does not resolve to expected server IP $EXPECTED_IP"
fi

step "Installing Ubuntu prerequisites"
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends ca-certificates curl certbot openssl xz-utils

step "Downloading and verifying the private Node.js runtime"
WORK_ROOT="$(mktemp -d /tmp/oraculus-auth-install.XXXXXX)"
NODE_ARCHIVE="node-v${NODE_VERSION}-${NODE_PLATFORM}.tar.xz"
NODE_BASE_URL="https://nodejs.org/dist/v${NODE_VERSION}"
curl --fail --silent --show-error --location "$NODE_BASE_URL/SHASUMS256.txt" --output "$WORK_ROOT/SHASUMS256.txt"
curl --fail --silent --show-error --location "$NODE_BASE_URL/$NODE_ARCHIVE" --output "$WORK_ROOT/$NODE_ARCHIVE"
EXPECTED_NODE_SHA="$(awk -v file="$NODE_ARCHIVE" '$2 == file { print $1 }' "$WORK_ROOT/SHASUMS256.txt")"
[[ "$EXPECTED_NODE_SHA" =~ ^[0-9a-fA-F]{64}$ ]] || fail "Official Node.js checksum for $NODE_ARCHIVE was not found"
printf '%s  %s\n' "$EXPECTED_NODE_SHA" "$WORK_ROOT/$NODE_ARCHIVE" | sha256sum --check --strict

STAGED_APP="$WORK_ROOT/app"
install -d -m 0755 "$STAGED_APP/runtime"
tar -xJf "$WORK_ROOT/$NODE_ARCHIVE" -C "$STAGED_APP/runtime" --strip-components=1
install -m 0644 "$PACKAGE_DIR/server.js" "$STAGED_APP/server.js"
"$STAGED_APP/runtime/bin/node" "$STAGED_APP/server.js" --self-test

step "Backing up prior Oraculus program and service configuration"
if systemctl is-active --quiet oraculus-auth.service 2>/dev/null; then
  PREVIOUS_SERVICE_ACTIVE=1
fi
backup_managed_paths
MUTATION_STARTED=1
systemctl stop oraculus-auth.service >/dev/null 2>&1 || true

step "Checking local TCP ports 80 and 443"
PORT_80_OWNERS="$(port_owners 80)"
PORT_443_OWNERS="$(port_owners 443)"
if [[ -n "$PORT_80_OWNERS" ]]; then
  printf '%s\n' "$PORT_80_OWNERS" >&2
  fail "TCP 80 is occupied. Stop or reconfigure the listed service before deployment"
fi
if [[ -n "$PORT_443_OWNERS" ]]; then
  printf '%s\n' "$PORT_443_OWNERS" >&2
  fail "TCP 443 is occupied. Stop or reconfigure the listed service before deployment"
fi

step "Creating the dedicated service account and protected directories"
if ! getent group oraculus-auth >/dev/null; then
  groupadd --system oraculus-auth
fi
if ! id -u oraculus-auth >/dev/null 2>&1; then
  useradd --system --gid oraculus-auth --home-dir "$DATA_DIR" --shell /usr/sbin/nologin oraculus-auth
fi
install -d -m 0750 -o root -g oraculus-auth "$CONFIG_DIR"
install -d -m 0700 -o oraculus-auth -g oraculus-auth "$DATA_DIR" "$DATA_DIR/keys" "$DATA_DIR/certificates"

step "Installing the application and preserving its existing configuration"
safe_remove_managed_path "$APP_DIR"
mv -- "$STAGED_APP" "$APP_DIR"
chown -R root:root "$APP_DIR"
find "$APP_DIR" -type d -exec chmod 0755 {} +
find "$APP_DIR" -type f -exec chmod 0644 {} +
chmod 0755 "$APP_DIR/runtime/bin/node"

if [[ ! -f "$CONFIG_DIR/server.json" ]]; then
  sed "s/__ORACULUS_DOMAIN__/${DOMAIN}/g" "$PACKAGE_DIR/server.ubuntu.json.template" > "$CONFIG_DIR/server.json"
fi
printf '%s\n' "$DOMAIN" > "$CONFIG_DIR/domain"
chown root:oraculus-auth "$CONFIG_DIR/server.json" "$CONFIG_DIR/domain"
chmod 0640 "$CONFIG_DIR/server.json" "$CONFIG_DIR/domain"

CONFIGURED_DOMAIN="$("$APP_DIR/runtime/bin/node" -e 'const fs=require("fs");const c=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));process.stdout.write(new URL(c.PublicBaseUrl).hostname)' "$CONFIG_DIR/server.json")"
[[ "$CONFIGURED_DOMAIN" == "$DOMAIN" ]] || fail "Existing server.json is configured for $CONFIGURED_DOMAIN, not $DOMAIN"
"$APP_DIR/runtime/bin/node" "$APP_DIR/server.js" --config "$CONFIG_DIR/server.json" --check-config
chown -R oraculus-auth:oraculus-auth "$DATA_DIR"
chmod 0700 "$DATA_DIR" "$DATA_DIR/keys" "$DATA_DIR/certificates"

step "Opening local firewall ports when UFW is active"
if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q '^Status: active'; then
  ufw allow 80/tcp
  ufw allow 443/tcp
else
  echo "UFW is not active; no local firewall policy was changed."
fi

step "Issuing or reusing the Let's Encrypt certificate"
certbot certonly \
  --non-interactive \
  --agree-tos \
  --no-eff-email \
  --email "$EMAIL" \
  --cert-name "$DOMAIN" \
  --domains "$DOMAIN" \
  --authenticator standalone \
  --preferred-challenges http \
  --keep-until-expiring

step "Installing certificate synchronization and systemd units"
install -m 0755 "$PACKAGE_DIR/sync-certificate.sh" /usr/local/sbin/oraculus-auth-sync-certificate
install -d -m 0755 /etc/letsencrypt/renewal-hooks/deploy
ln -sfn /usr/local/sbin/oraculus-auth-sync-certificate /etc/letsencrypt/renewal-hooks/deploy/oraculus-auth
ORACULUS_SKIP_RESTART=1 /usr/local/sbin/oraculus-auth-sync-certificate

install -m 0644 "$PACKAGE_DIR/oraculus-auth.service" /etc/systemd/system/oraculus-auth.service
install -m 0644 "$PACKAGE_DIR/oraculus-auth-renew.service" /etc/systemd/system/oraculus-auth-renew.service
install -m 0644 "$PACKAGE_DIR/oraculus-auth-renew.timer" /etc/systemd/system/oraculus-auth-renew.timer
systemctl daemon-reload

step "Creating or retaining the super administrator"
ADMIN_PASSWORD="$(openssl rand -base64 36 | tr -d '\r\n')!aA7"
ADMIN_OUTPUT="$(printf '%s\n' "$ADMIN_PASSWORD" | runuser -u oraculus-auth -- "$APP_DIR/runtime/bin/node" "$APP_DIR/server.js" --config "$CONFIG_DIR/server.json" --ensure-admin root_admin)"
printf '%s\n' "$ADMIN_OUTPUT"
if [[ "$ADMIN_OUTPUT" != *"ADMIN CREATED"* ]]; then
  ADMIN_PASSWORD=""
fi

step "Starting the service and automatic certificate renewal"
systemctl enable oraculus-auth.service
systemctl restart oraculus-auth.service
systemctl enable --now oraculus-auth-renew.timer

step "Running local HTTPS readiness checks"
HEALTH_URL="https://${DOMAIN}/health/ready"
HEALTHY=0
for _ in $(seq 1 30); do
  if RESPONSE="$(curl --fail --silent --show-error --max-time 4 --resolve "${DOMAIN}:443:127.0.0.1" "$HEALTH_URL" 2>/dev/null)" \
    && grep -Eq '"ok"[[:space:]]*:[[:space:]]*true' <<< "$RESPONSE"; then
    HEALTHY=1
    break
  fi
  sleep 1
done
[[ "$HEALTHY" -eq 1 ]] || {
  systemctl status oraculus-auth.service --no-pager || true
  journalctl -u oraculus-auth.service -n 100 --no-pager || true
  fail "The service started but its local HTTPS readiness check failed"
}

{
  echo "Oraculus Ubuntu authentication server deployment succeeded"
  echo "Deployment time: $(date --iso-8601=seconds)"
  echo "URL: https://${DOMAIN}"
  echo "Health: ${HEALTH_URL}"
  echo "Admin: https://${DOMAIN}/admin/login"
  echo "Program: ${APP_DIR}"
  echo "Configuration: ${CONFIG_DIR}/server.json"
  echo "Data: ${DATA_DIR}"
  echo "Log: ${LOG_FILE}"
  echo "Backup: ${BACKUP_ROOT}"
  if [[ -n "$ADMIN_PASSWORD" ]]; then
    echo
    echo "Initial super administrator: root_admin"
    echo "Initial password: ${ADMIN_PASSWORD}"
    echo "Change this password after the first login and securely delete this result file."
  else
    echo
    echo "Existing root_admin account retained; its password was not changed."
  fi
} > "$RESULT_FILE"
chmod 0600 "$RESULT_FILE"
ADMIN_PASSWORD=""
trap - ERR

printf '\nDeployment succeeded.\nResult: %s\nHealth: %s\n' "$RESULT_FILE" "$HEALTH_URL"
