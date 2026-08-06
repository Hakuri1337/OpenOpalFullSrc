#!/usr/bin/env node
'use strict';

/*
 * Oraculus authentication server.
 *
 * Deliberately dependency-free: it runs on the portable Node.js binary that
 * the deployment package contains.  No IIS, HTTP.sys, .NET runtime, native
 * addon, or npm install is needed on the target server.
 */

const crypto = require('crypto');
const fs = require('fs');
const http = require('http');
const https = require('https');
const net = require('net');
const os = require('os');
const path = require('path');
const querystring = require('querystring');

const MAX_BODY_BYTES = 64 * 1024;
const MAX_CONCURRENT_REQUESTS = 64;
const MAX_IRC_MESSAGE_CODEPOINTS = 280;
const MAX_IRC_CONNECTIONS = 2000;
const IRC_MESSAGE_WINDOW_SECONDS = 10;
const IRC_MESSAGE_LIMIT = 5;
const ADMIN_PAGE_SIZE = 200;
const PBKDF2_ITERATIONS = 240000;
const USER_CHANGE_COOLDOWN = 7 * 86400;
const PERMANENT_BETA_EXPIRES_AT_UTC = 4102444799;
const MAX_DURATION_BETA_SECONDS = 3650 * 86400;
const MAX_BETA_EXPIRY_SECONDS = 20 * 365 * 86400;
const MAX_BETA_CODES_PER_BATCH = 100;
const BETA_CODE_ISSUE_DELIVERY_SECONDS = 15 * 60;
const BETA_CODE_ISSUE_RATE_WINDOW_SECONDS = 10 * 60;
const BETA_CODE_ISSUE_USER_RATE_LIMIT = 30;
const BETA_CODE_ISSUE_IP_RATE_LIMIT = 100;
const BETA_CODE_ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
const BETA_CODE_VALUE_RULES = Object.freeze([
  Object.freeze({ key: 'DAY_1', label: '1 天卡', days: 1, unitValue: 3 }),
  Object.freeze({ key: 'DAY_2', label: '2 天卡', days: 2, unitValue: 5 }),
  Object.freeze({ key: 'DAY_7', label: '周卡（7 天）', days: 7, unitValue: 10 }),
  Object.freeze({ key: 'DAY_30', label: '月卡（30 天）', days: 30, unitValue: 25 }),
  Object.freeze({ key: 'DAY_365', label: '年卡（365 天）', days: 365, unitValue: 40 }),
  Object.freeze({ key: 'PERMANENT', label: '永久卡（含超过 400 天）', days: null, unitValue: 50 })
]);
const COMMON_PASSWORDS = new Set([
  '123456789012', '123456789123', 'password1234', 'password12345',
  'qwerty123456', 'qwertyuiop123', 'admin12345678', 'letmein123456',
  'welcome123456', 'iloveyou12345', 'minecraft1234', 'oraculus1234'
]);

function now() { return Math.floor(Date.now() / 1000); }
function id() { return crypto.randomUUID().replace(/-/g, ''); }
function randomToken(bytes = 32) { return crypto.randomBytes(bytes).toString('base64url'); }
function sha256(value) { return crypto.createHash('sha256').update(String(value || ''), 'utf8').digest('base64'); }
function hmac(key, value) { return crypto.createHmac('sha256', key).update(String(value || ''), 'utf8').digest('base64'); }
function upper(value) { return String(value || '').trim().toUpperCase(); }
function normalizeBetaCode(value) {
  const normalized = upper(value).replace(/[\s-]+/g, '');
  return /^ORABETA[0-9A-HJKMNP-TV-Z]{26}$/.test(normalized) ? normalized : '';
}
function betaCodeLookup(pepper, value) {
  const normalized = normalizeBetaCode(value);
  return normalized ? hmac(pepper, normalized) : '';
}
function randomBetaCode() {
  const bytes = crypto.randomBytes(16);
  let accumulator = 0; let bits = 0; let body = '';
  for (const byte of bytes) {
    accumulator = (accumulator << 8) | byte;
    bits += 8;
    while (bits >= 5) {
      bits -= 5;
      body += BETA_CODE_ALPHABET[(accumulator >>> bits) & 31];
      accumulator &= (1 << bits) - 1;
    }
  }
  if (bits > 0) body += BETA_CODE_ALPHABET[(accumulator << (5 - bits)) & 31];
  const groups = body.match(/.{1,4}/g) || [];
  return `ORA-BETA-${groups.join('-')}`;
}
function betaCodeValueCategory(batch) {
  const durationDays = Number(batch && batch.durationSeconds) / 86400;
  if ((batch && batch.product) === 'BETA_PERMANENT' || durationDays > 400)
    return BETA_CODE_VALUE_RULES.find(rule => rule.key === 'PERMANENT');
  const exact = BETA_CODE_VALUE_RULES.find(rule => rule.days === durationDays);
  if (exact) return exact;
  const durationLabel = Number.isFinite(durationDays) && durationDays > 0
    ? `${Number.isInteger(durationDays) ? durationDays : durationDays.toFixed(2)} 天`
    : '未知时长';
  return { key: `UNPRICED:${durationLabel}`, label: `${durationLabel}（未定价）`, days: durationDays, unitValue: null };
}
function safeEqual(left, right) {
  const a = Buffer.from(String(left || ''), 'utf8');
  const b = Buffer.from(String(right || ''), 'utf8');
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}
function escapeHtml(value) {
  return String(value == null ? '' : value).replace(/[&<>'"]/g, char => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
  }[char]));
}
function formatTime(value) {
  if (!value) return '-';
  return new Date(value * 1000).toLocaleString('zh-CN', { hour12: false, timeZoneName: 'short' });
}
function error(code, message) { return { Ok: false, Error: code, Message: message }; }
function apiStatus(code) {
  if (code === 'INVALID_CREDENTIALS' || code === 'SESSION_REVOKED') return 401;
  if (['ACCOUNT_BANNED', 'ACCOUNT_DELETED', 'LICENSE_REQUIRED', 'LICENSE_EXPIRED',
    'HWID_MISMATCH', 'PASSWORD_CHANGE_REQUIRED'].includes(code)) return 403;
  if (code === 'RATE_LIMITED' || code === 'REGISTRATION_RATE_LIMITED' || code === 'REDEEM_RATE_LIMITED'
    || code === 'BETA_CODE_ISSUE_RATE_LIMITED' || code === 'BETA_CODE_DAILY_LIMIT') return 429;
  if (code === 'ACCOUNT_NOT_ACTIVE' || code === 'ADMIN_PERMISSION_DENIED') return 403;
  if (code === 'USERNAME_TAKEN' || code === 'BETA_EXPIRY_LIMIT' || code === 'IDEMPOTENCY_KEY_REUSED'
    || code === 'ISSUE_RESULT_EXPIRED') return 409;
  if (code === 'BETA_CODE_ISSUE_FAILED') return 500;
  return 400;
}

class AppConfig {
  static defaults(configPath) {
    const folder = path.dirname(configPath);
    return {
      ListenHost: '127.0.0.1', HttpPort: 18080, HttpsPort: 18443,
      EnableHttp: true, PublicBaseUrl: 'http://127.0.0.1:18080',
      AllowedHosts: ['127.0.0.1', 'localhost'], DataDirectory: path.join(folder, 'data'),
      AccessTokenMinutes: 10, RefreshTokenDays: 7, WebSessionHours: 8,
      SecureCookies: false, RequireHttps: false, RegistrationEnabled: true,
      AllowedClientVersions: ['b8'], AllowedBuildIds: ['b8-free', 'b8-beta'],
      AllowedLauncherVersions: ['v0.9.21'],
      TlsCertificatePath: '', TlsPrivateKeyPath: '', InternalWebsiteSecret: '',
      InternalWebHost: '127.0.0.1', InternalWebPort: 0, InternalWebTls: false,
      InternalWebAllowedIps: []
    };
  }

  static load(configPath) {
    const absolute = path.resolve(configPath);
    let raw;
    if (!fs.existsSync(absolute)) {
      raw = AppConfig.defaults(absolute);
      fs.mkdirSync(path.dirname(absolute), { recursive: true });
      fs.writeFileSync(absolute, JSON.stringify(raw, null, 2) + '\n', 'utf8');
    } else {
      try { raw = JSON.parse(fs.readFileSync(absolute, 'utf8')); }
      catch (cause) { throw new Error(`server.json is invalid: ${cause.message}`); }
    }
    const config = Object.assign(AppConfig.defaults(absolute), raw || {});
    config._configPath = absolute;
    config.AllowedHosts = normalizeList(config.AllowedHosts, 'AllowedHosts').map(host => host.toLowerCase());
    config.AllowedClientVersions = normalizeList(config.AllowedClientVersions, 'AllowedClientVersions');
    config.AllowedBuildIds = normalizeList(config.AllowedBuildIds, 'AllowedBuildIds');
    config.AllowedLauncherVersions = normalizeList(config.AllowedLauncherVersions, 'AllowedLauncherVersions');
    config.DataDirectory = path.isAbsolute(config.DataDirectory)
      ? path.normalize(config.DataDirectory) : path.resolve(path.dirname(absolute), config.DataDirectory);
    config.TlsCertificatePath = resolveOptional(path.dirname(absolute), config.TlsCertificatePath);
    config.TlsPrivateKeyPath = resolveOptional(path.dirname(absolute), config.TlsPrivateKeyPath);
    config.AccessTokenMinutes = clamp(config.AccessTokenMinutes, 2, 60, 10);
    config.RefreshTokenDays = clamp(config.RefreshTokenDays, 1, 30, 7);
    config.WebSessionHours = clamp(config.WebSessionHours, 1, 24, 8);
    config.HttpPort = clamp(config.HttpPort, 1, 65535, 80);
    config.HttpsPort = clamp(config.HttpsPort, 1, 65535, 443);
    config.InternalWebPort = clamp(config.InternalWebPort, 0, 65535, 0);
    config.EnableHttp = Boolean(config.EnableHttp);
    config.RequireHttps = Boolean(config.RequireHttps);
    config.SecureCookies = Boolean(config.SecureCookies);
    config.RegistrationEnabled = Boolean(config.RegistrationEnabled);
    config.InternalWebsiteSecret = text(config.InternalWebsiteSecret);
    config.InternalWebHost = text(config.InternalWebHost).trim();
    config.InternalWebTls = Boolean(config.InternalWebTls);
    config.InternalWebAllowedIps = normalizeIpList(config.InternalWebAllowedIps, 'InternalWebAllowedIps');
    if (!config.ListenHost || typeof config.ListenHost !== 'string') throw new Error('ListenHost is invalid');
    let publicUrl;
    try { publicUrl = new URL(config.PublicBaseUrl); } catch { throw new Error('PublicBaseUrl is invalid'); }
    if (!['http:', 'https:'].includes(publicUrl.protocol)) throw new Error('PublicBaseUrl must be HTTP(S)');
    if (!config.AllowedHosts.includes(publicUrl.hostname.toLowerCase()))
      throw new Error('PublicBaseUrl host must be present in AllowedHosts');
    if (config.RequireHttps && (!config.TlsCertificatePath || !config.TlsPrivateKeyPath))
      throw new Error('RequireHttps requires TlsCertificatePath and TlsPrivateKeyPath');
    if (config.InternalWebsiteSecret && config.InternalWebsiteSecret.length < 32)
      throw new Error('InternalWebsiteSecret must be at least 32 characters when configured');
    const internalLoopback = ['127.0.0.1', '::1', 'localhost'].includes(config.InternalWebHost.toLowerCase());
    if (!internalLoopback && !net.isIP(config.InternalWebHost) && config.InternalWebHost !== '0.0.0.0' && config.InternalWebHost !== '::')
      throw new Error('InternalWebHost must be a loopback address or a literal bind address');
    if (!internalLoopback && config.InternalWebPort && !config.InternalWebTls)
      throw new Error('A non-loopback InternalWebHost requires InternalWebTls=true');
    if (!internalLoopback && config.InternalWebPort && !config.InternalWebAllowedIps.length)
      throw new Error('A non-loopback InternalWebHost requires InternalWebAllowedIps');
    if (config.InternalWebTls && (!config.TlsCertificatePath || !config.TlsPrivateKeyPath))
      throw new Error('InternalWebTls requires TlsCertificatePath and TlsPrivateKeyPath');
    fs.mkdirSync(config.DataDirectory, { recursive: true });
    fs.mkdirSync(path.join(config.DataDirectory, 'keys'), { recursive: true });
    return config;
  }

  static allowedClient(config, edition, version, buildId) {
    const normalizedEdition = upper(edition);
    return (normalizedEdition === 'FREE' || normalizedEdition === 'BETA')
      && config.AllowedClientVersions.includes(String(version || ''))
      && config.AllowedBuildIds.includes(String(buildId || ''))
      && String(buildId || '') === `${version}-${normalizedEdition.toLowerCase()}`;
  }

  static allowedLauncher(config, launcherVersion) {
    return config.AllowedLauncherVersions.includes(String(launcherVersion || ''));
  }

  static versionGate(config, edition, clientVersion, buildId, launcherVersion) {
    if (text(launcherVersion).trim()) {
      return AppConfig.allowedLauncher(config, launcherVersion)
        ? null : error('LAUNCHER_VERSION_BLOCKED', '当前启动器版本不受支持，请更新启动器');
    }
    return AppConfig.allowedClient(config, edition, clientVersion, buildId)
      ? null : error('CLIENT_VERSION_BLOCKED', '当前客户端版本不受支持，请更新客户端');
  }
}

function resolveOptional(base, value) {
  if (!value) return '';
  return path.isAbsolute(value) ? path.normalize(value) : path.resolve(base, value);
}
function normalizeList(values, name) {
  if (!Array.isArray(values)) throw new Error(`${name} must be a non-empty array`);
  const cleaned = [...new Set(values.map(value => String(value || '').trim()).filter(Boolean))];
  if (!cleaned.length) throw new Error(`${name} must be a non-empty array`);
  return cleaned;
}
function normalizeIpList(values, name) {
  if (!Array.isArray(values)) throw new Error(`${name} must be an array`);
  const cleaned = [...new Set(values.map(value => normalizeRemoteIp(value)).filter(Boolean))];
  if (cleaned.length !== values.filter(value => text(value).trim()).length)
    throw new Error(`${name} must only contain IPv4 or IPv6 addresses`);
  return cleaned;
}
function clamp(value, min, max, fallback) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? Math.max(min, Math.min(max, parsed)) : fallback;
}

class JsonStore {
  constructor(dataDirectory) {
    this.file = path.join(dataDirectory, 'oraculus-auth.json');
    this.data = this.load();
  }

  empty() {
    return {
      schemaVersion: 1, users: [], sessions: [], usedRefreshTokens: [],
      webSessions: [], rateLimitEvents: [], auditLogs: [], betaCodeBatches: [], betaCodes: [], betaRedemptions: [],
      betaCodeIssueRequests: [],
      serviceSettings: { betaPublicAccessUntilUtc: 0, redemptionSchemaVersion: 1 }
    };
  }

  load() {
    if (!fs.existsSync(this.file)) {
      const initial = this.empty();
      this.write(initial);
      return initial;
    }
    let parsed;
    try { parsed = JSON.parse(fs.readFileSync(this.file, 'utf8')); }
    catch (cause) { throw new Error(`authentication data is unreadable: ${cause.message}`); }
    const initial = this.empty();
    let migrated = false;
    for (const key of Object.keys(initial)) {
      if (key === 'schemaVersion' || key === 'serviceSettings') continue;
      if (parsed[key] == null && ['betaCodeBatches', 'betaCodes', 'betaRedemptions', 'betaCodeIssueRequests'].includes(key)) {
        parsed[key] = [];
        migrated = true;
      }
      if (!Array.isArray(parsed[key])) throw new Error(`authentication data has invalid ${key}`);
    }
    if (parsed.schemaVersion !== 1) throw new Error(`unsupported authentication data schema: ${parsed.schemaVersion}`);
    if (parsed.serviceSettings == null) {
      parsed.serviceSettings = initial.serviceSettings;
      this.write(parsed);
    } else if (typeof parsed.serviceSettings !== 'object' || Array.isArray(parsed.serviceSettings)) {
      throw new Error('authentication data has invalid serviceSettings');
    }
    parsed.serviceSettings.betaPublicAccessUntilUtc = clamp(
      parsed.serviceSettings.betaPublicAccessUntilUtc, 0, 253402300799, 0
    );
    if (parsed.serviceSettings.redemptionSchemaVersion == null) migrated = true;
    parsed.serviceSettings.redemptionSchemaVersion = 1;
    if (migrated) this.write(parsed);
    return parsed;
  }

  write(data = this.data) {
    const temporary = `${this.file}.new`;
    fs.writeFileSync(temporary, JSON.stringify(data) + '\n', { encoding: 'utf8', mode: 0o600 });
    try { fs.renameSync(temporary, this.file); }
    catch (cause) {
      try { fs.copyFileSync(temporary, this.file); fs.unlinkSync(temporary); }
      catch { throw cause; }
    }
  }

  mutate(callback) {
    const result = callback(this.data);
    this.write();
    return result;
  }

  transaction(callback) {
    const next = JSON.parse(JSON.stringify(this.data));
    const result = callback(next);
    this.write(next);
    this.data = next;
    return result;
  }
}

class AuthService {
  constructor(config) {
    this.config = config;
    this.store = new JsonStore(config.DataDirectory);
    const keyDir = path.join(config.DataDirectory, 'keys');
    this.hwidPepper = secret(keyDir, 'hwid-pepper');
    this.ipPepper = secret(keyDir, 'ip-pepper');
    this.passwordPepper = secret(keyDir, 'password-pepper');
    this.redeemCodePepper = secret(keyDir, 'redeem-code-pepper');
    this.betaCodeDeliveryKey = secret(keyDir, 'beta-code-delivery-key');
    this.entitlementSigner = entitlementSigner(keyDir);
  }

  ready() {
    const data = this.store.data;
    return ['users', 'sessions', 'usedRefreshTokens', 'webSessions', 'rateLimitEvents', 'auditLogs',
      'betaCodeBatches', 'betaCodes', 'betaRedemptions']
      .concat('betaCodeIssueRequests')
      .every(key => Array.isArray(data[key]));
  }

  hasActiveSuperAdmin() {
    return this.store.data.users.some(user => user.role === 'SUPER_ADMIN' && user.status === 'ACTIVE');
  }

  register(request, remoteIp, requestId) {
    if (!this.config.RegistrationEnabled) return error('REGISTRATION_DISABLED', '当前未开放注册');
    const username = text(request.username).trim();
    const password = text(request.password);
    const fingerprint = text(request.deviceFingerprint);
    const hwidVersion = text(request.hwidVersion);
    const hwidQuality = hwidQualityOf(request.hwidQuality);
    const edition = upper(request.edition);
    const clientVersion = text(request.clientVersion);
    const buildId = text(request.buildId);
    const launcherVersion = text(request.launcherVersion);
    if (!validUsername(username)) return error('INVALID_USERNAME', '用户名须为 3-24 位字母、数字或下划线');
    const passwordError = validatePassword(password, username);
    if (passwordError) return error('INVALID_PASSWORD', passwordError);
    if (!validFingerprint(fingerprint, hwidVersion)) return error('HWID_UNAVAILABLE', '无法取得有效设备标识');
    const versionError = AppConfig.versionGate(this.config, edition, clientVersion, buildId, launcherVersion);
    if (versionError) return versionError;
    const ipHash = this.hashIp(remoteIp);
    const hwidHash = this.hashHwid(fingerprint);
    if (!this.consumeRate(`register-ip-hour:${ipHash}`, 3, 3600)
      || !this.consumeRate(`register-ip-day:${ipHash}`, 8, 86400)
      || !this.consumeRate(`register-hwid:${hwidHash}`, 2, USER_CHANGE_COOLDOWN)) {
      this.audit(null, null, 'SELF_REGISTER', false, remoteIp, 'rate_limited', requestId);
      return error('REGISTRATION_RATE_LIMITED', '注册过于频繁，请稍后再试');
    }
    let user;
    const created = this.store.mutate(data => {
      if (findUserByName(data, username)) return false;
      user = newUser(username, this.hashPassword(password), {
        tier: 'FREE', hwidHash, hwidVersion, hwidQuality, hwidChangedAtUtc: now(),
        creationSource: 'SELF_REGISTRATION', registrationIpHash: ipHash
      });
      data.users.push(user);
      return true;
    });
    if (!created) return error('USERNAME_TAKEN', '该用户名已被使用');
    this.audit(null, user.id, 'SELF_REGISTER', true, remoteIp, 'tier=FREE', requestId);
    if (edition === 'BETA') {
      if (this.betaPublicAccess().enabled) {
        return this.issueSession(user, hwidHash, fingerprint, 'BETA', clientVersion, buildId, launcherVersion, remoteIp, requestId);
      }
      return { Ok: true, Message: '注册成功，当前账号为 Free，Beta 需要管理员开通', Account: accountView(user) };
    }
    return this.issueSession(user, hwidHash, fingerprint, 'FREE', clientVersion, buildId, launcherVersion, remoteIp, requestId);
  }

  login(request, remoteIp, requestId) {
    const username = text(request.username).trim();
    const password = text(request.password);
    const fingerprint = text(request.deviceFingerprint);
    const hwidVersion = text(request.hwidVersion);
    const hwidQuality = hwidQualityOf(request.hwidQuality);
    const edition = upper(request.edition);
    const clientVersion = text(request.clientVersion);
    const buildId = text(request.buildId);
    const launcherVersion = text(request.launcherVersion);
    const versionError = AppConfig.versionGate(this.config, edition, clientVersion, buildId, launcherVersion);
    if (versionError) return versionError;
    if (!this.consumeRate(`login:${this.hashIp(remoteIp)}:${normalizeUsername(username)}`, 10, 300))
      return error('RATE_LIMITED', '登录尝试过于频繁，请稍后再试');
    if (!validFingerprint(fingerprint, hwidVersion)) return error('HWID_UNAVAILABLE', '无法取得有效设备标识');
    const user = findUserByName(this.store.data, username);
    if (!user || !this.verifyPassword(password, user.passwordHash)) {
      this.audit(user && user.id, user && user.id, 'CLIENT_LOGIN', false, remoteIp, 'invalid_credentials', requestId);
      return error('INVALID_CREDENTIALS', '用户名或密码错误');
    }
    const accountError = this.validateAccount(user, edition);
    if (accountError) return accountError;
    const hwidHash = this.hashHwid(fingerprint);
    if (!user.hwidHash) {
      this.store.mutate(data => {
        const fresh = findUserById(data, user.id);
        if (!fresh.hwidHash) {
          fresh.hwidHash = hwidHash; fresh.hwidVersion = hwidVersion;
          fresh.hwidQuality = hwidQuality; fresh.hwidChangedAtUtc = now();
        }
      });
      const fresh = findUserById(this.store.data, user.id);
      if (!safeEqual(fresh.hwidHash, hwidHash)) return error('HWID_MISMATCH', '账号已被另一台设备绑定');
      Object.assign(user, fresh);
    } else if (!safeEqual(user.hwidHash, hwidHash)) {
      this.audit(user.id, user.id, 'CLIENT_LOGIN', false, remoteIp, 'hwid_mismatch', requestId);
      return error('HWID_MISMATCH', '当前设备与账号绑定设备不一致');
    }
    return this.issueSession(user, hwidHash, fingerprint, edition, clientVersion, buildId, launcherVersion, remoteIp, requestId);
  }

  launcherLogin(request, accessToken, remoteIp, requestId) {
    const username = text(request.username).trim();
    const fingerprint = text(request.deviceFingerprint);
    const hwidVersion = text(request.hwidVersion);
    const edition = upper(request.edition);
    const clientVersion = text(request.clientVersion);
    const buildId = text(request.buildId);
    const versionError = AppConfig.versionGate(this.config, edition, clientVersion, buildId, '');
    if (versionError) return versionError;
    if (!validUsername(username) || !validFingerprint(fingerprint, hwidVersion))
      return error('SESSION_REVOKED', '启动器登录信息无效或已过期');
    if (!this.consumeRate(`launcher-login:${this.hashIp(remoteIp)}:${normalizeUsername(username)}`, 10, 300))
      return error('RATE_LIMITED', '登录尝试过于频繁，请稍后再试');
    const validated = this.validateClientAction(accessToken);
    if (!validated.Ok) return validated;
    const { user, session } = validated;
    if (normalizeUsername(user.username) !== normalizeUsername(username)) {
      this.audit(user.id, user.id, 'CLIENT_LAUNCHER_LOGIN', false, remoteIp, 'username_mismatch', requestId);
      return error('SESSION_REVOKED', '启动器登录信息无效或已过期');
    }
    const accountError = this.validateAccount(user, edition);
    if (accountError) return accountError;
    const hwidHash = this.hashHwid(fingerprint);
    if (!safeEqual(user.hwidHash, hwidHash) || !safeEqual(session.hwidHash, hwidHash)) {
      this.audit(user.id, user.id, 'CLIENT_LAUNCHER_LOGIN', false, remoteIp, 'hwid_mismatch', requestId);
      return error('HWID_MISMATCH', '当前设备与启动器会话不一致');
    }
    const result = this.issueSession(
      user, hwidHash, fingerprint, edition, clientVersion, buildId, '', remoteIp, requestId
    );
    this.audit(user.id, user.id, 'CLIENT_LAUNCHER_LOGIN', true, remoteIp, `edition=${edition}`, requestId);
    return result;
  }

  refresh(request, remoteIp, requestId) {
    const refreshToken = text(request.refreshToken);
    const fingerprint = text(request.deviceFingerprint);
    const edition = upper(request.edition);
    const clientVersion = text(request.clientVersion);
    const buildId = text(request.buildId);
    const launcherVersion = text(request.launcherVersion);
    const versionError = AppConfig.versionGate(this.config, edition, clientVersion, buildId, launcherVersion);
    if (versionError) return versionError;
    if (refreshToken.length < 32 || fingerprint.length < 20) return error('SESSION_REVOKED', '会话无效');
    const refreshHash = sha256(refreshToken);
    const hwidHash = this.hashHwid(fingerprint);
    const session = this.store.data.sessions.find(item => !item.revokedAtUtc && safeEqual(item.refreshTokenHash, refreshHash));
    if (!session) {
      this.revokeReplayedRefresh(refreshHash, remoteIp, requestId);
      return error('SESSION_REVOKED', '会话已失效，请重新登录');
    }
    const user = findUserById(this.store.data, session.userId);
    if (!user || session.refreshExpiresAtUtc <= now()) return error('SESSION_REVOKED', '会话已过期，请重新登录');
    if (session.clientEdition !== edition) return error('SESSION_REVOKED', '客户端版本与会话不一致');
    const accountError = this.validateAccount(user, edition);
    if (accountError) return accountError;
    if (!safeEqual(user.hwidHash, hwidHash) || !safeEqual(session.hwidHash, hwidHash))
      return error('HWID_MISMATCH', '当前设备与会话不一致');
    const access = randomToken();
    const refresh = randomToken();
    const timestamp = now();
    const accessExpiresAt = timestamp + this.config.AccessTokenMinutes * 60;
    const refreshExpiresAt = timestamp + this.config.RefreshTokenDays * 86400;
    const rotated = this.store.mutate(data => {
      const fresh = data.sessions.find(item => item.id === session.id);
      if (!fresh || fresh.revokedAtUtc || !safeEqual(fresh.refreshTokenHash, refreshHash)) return false;
      fresh.accessTokenHash = sha256(access); fresh.refreshTokenHash = sha256(refresh);
      fresh.accessExpiresAtUtc = accessExpiresAt; fresh.refreshExpiresAtUtc = refreshExpiresAt;
      fresh.lastSeenAtUtc = timestamp; fresh.clientVersion = clientVersion; fresh.buildId = buildId;
      fresh.launcherVersion = launcherVersion;
      data.usedRefreshTokens.push({ tokenHash: refreshHash, sessionId: fresh.id, usedAtUtc: timestamp, expiresAtUtc: session.refreshExpiresAtUtc });
      data.usedRefreshTokens = data.usedRefreshTokens.filter(item => item.expiresAtUtc >= timestamp - 86400);
      return true;
    });
    if (!rotated) {
      this.revokeReplayedRefresh(refreshHash, remoteIp, requestId);
      return error('SESSION_REVOKED', '刷新令牌已被使用，会话已撤销');
    }
    this.audit(user.id, user.id, 'TOKEN_REFRESH', true, remoteIp, null, requestId);
    const account = this.clientAccountView(user, edition);
    return successSession(account, access, refresh, accessExpiresAt, refreshExpiresAt,
      this.entitlementProof(user, account, fingerprint, edition, clientVersion, buildId,
        access, timestamp, accessExpiresAt, refreshExpiresAt));
  }

  validateClientAction(accessToken) {
    const found = this.clientSession(accessToken);
    if (!found) return error('SESSION_REVOKED', '会话无效');
    const { user, session } = found;
    if (!safeEqual(user.hwidHash, session.hwidHash)) return error('SESSION_REVOKED', '设备绑定已变更');
    const versionError = AppConfig.versionGate(
      this.config, session.clientEdition, session.clientVersion, session.buildId, session.launcherVersion
    );
    if (versionError) return versionError;
    const accountError = this.validateAccount(user, session.clientEdition);
    if (accountError) return accountError;
    return { Ok: true, user, session };
  }

  heartbeat(accessToken, remoteIp, requestId) {
    const validated = this.validateClientAction(accessToken);
    if (!validated.Ok) return validated;
    const { user, session } = validated;
    this.store.mutate(data => { const current = data.sessions.find(item => item.id === session.id); if (current) current.lastSeenAtUtc = now(); });
    return { Ok: true, Message: 'ok', Account: this.clientAccountView(user, session.clientEdition) };
  }

  legality(accessToken) {
    const session = this.clientSessionRecord(accessToken);
    if (!session) return error('SESSION_REVOKED', '登录会话无效或已过期');
    const user = findUserById(this.store.data, session.userId);
    if (!user) {
      return {
        Ok: true, Exists: false, Active: false, BetaEligible: false,
        Username: '', Tier: '', ServerTimeUtc: now()
      };
    }
    const exists = user.status !== 'DELETED';
    const versionValid = !AppConfig.versionGate(
      this.config, session.clientEdition, session.clientVersion, session.buildId, session.launcherVersion
    );
    const deviceValid = Boolean(user.hwidHash && session.hwidHash && safeEqual(user.hwidHash, session.hwidHash));
    const active = exists && user.status === 'ACTIVE' && !user.forcePasswordChange && versionValid && deviceValid;
    const account = this.clientAccountView(user, session.clientEdition);
    return {
      Ok: true,
      Exists: exists,
      Active: active,
      BetaEligible: active && this.hasActiveBetaEntitlement(user),
      Username: account.username,
      Tier: account.tier,
      ServerTimeUtc: now()
    };
  }

  logout(accessToken, remoteIp, requestId) {
    if (accessToken) this.store.mutate(data => {
      const session = data.sessions.find(item => !item.revokedAtUtc && safeEqual(item.accessTokenHash, sha256(accessToken)));
      if (session) { session.revokedAtUtc = now(); session.revokeReason = 'logout'; }
    });
    this.audit(null, null, 'CLIENT_LOGOUT', true, remoteIp, null, requestId);
  }

  createWebSession(username, password, adminRequired, remoteIp, requestId) {
    const action = adminRequired ? 'ADMIN_LOGIN' : 'USER_WEB_LOGIN';
    if (!this.consumeRate(`web-login:${this.hashIp(remoteIp)}:${normalizeUsername(username)}`, 10, 600)) {
      this.audit(null, null, action, false, remoteIp, 'rate_limited', requestId);
      return { error: '尝试过于频繁' };
    }
    const user = findUserByName(this.store.data, username);
    if (!user || !this.verifyPassword(password, user.passwordHash) || user.status !== 'ACTIVE') {
      this.audit(null, user && user.id, action, false, remoteIp, 'invalid_credentials_or_status', requestId);
      return { error: '用户名或密码错误' };
    }
    if (adminRequired && !isAdmin(user)) {
      this.audit(user.id, user.id, action, false, remoteIp, 'role_denied', requestId);
      return { error: '没有管理员权限' };
    }
    if (adminRequired && user.forcePasswordChange) {
      this.audit(user.id, user.id, action, false, remoteIp, 'password_change_required', requestId);
      return { error: '请先登录用户面板修改临时密码' };
    }
    const token = randomToken();
    this.store.mutate(data => data.webSessions.push({
      id: id(), userId: user.id, tokenHash: sha256(token), csrfToken: randomToken(24), isAdmin: adminRequired,
      expiresAtUtc: now() + this.config.WebSessionHours * 3600, createdAtUtc: now(), lastSeenAtUtc: now()
    }));
    this.audit(user.id, user.id, action, true, remoteIp, null, requestId);
    return { token };
  }

  registerWeb(username, password, remoteIp, requestId) {
    if (!this.config.RegistrationEnabled) return error('REGISTRATION_DISABLED', '当前未开放注册');
    username = text(username).trim(); password = text(password);
    if (!validUsername(username)) return error('INVALID_USERNAME', '用户名须为 3-24 位字母、数字或下划线');
    const passwordError = validatePassword(password, username);
    if (passwordError) return error('INVALID_PASSWORD', passwordError);
    const ipHash = this.hashIp(remoteIp);
    if (!this.consumeRate(`web-register-ip-hour:${ipHash}`, 3, 3600)
      || !this.consumeRate(`web-register-ip-day:${ipHash}`, 8, 86400)) {
      this.audit(null, null, 'WEB_REGISTER', false, remoteIp, 'rate_limited', requestId);
      return error('REGISTRATION_RATE_LIMITED', '注册过于频繁，请稍后再试');
    }
    let user;
    const created = this.store.mutate(data => {
      if (findUserByName(data, username)) return false;
      user = newUser(username, this.hashPassword(password), {
        tier: 'FREE', creationSource: 'WEB_REGISTRATION', registrationIpHash: ipHash
      });
      data.users.push(user);
      return true;
    });
    if (!created) return error('USERNAME_TAKEN', '该用户名已被使用');
    this.audit(null, user.id, 'WEB_REGISTER', true, remoteIp, 'tier=FREE', requestId);
    const session = this.createWebSession(username, password, false, remoteIp, requestId);
    if (session.error) return error('SESSION_CREATE_FAILED', '账号已创建，请使用登录页进入账户中心');
    return { Ok: true, Token: session.token, Account: accountView(user) };
  }

  getWebSession(token, adminRequired) {
    if (!token) return null;
    const session = this.store.data.webSessions.find(item => item.expiresAtUtc > now() && safeEqual(item.tokenHash, sha256(token)));
    if (!session) return null;
    const user = findUserById(this.store.data, session.userId);
    if (!user || user.status !== 'ACTIVE' || (adminRequired && (!session.isAdmin || !isAdmin(user)))) return null;
    return { ...session, user };
  }

  webAccount(token) {
    const session = this.getWebSession(token, false);
    if (!session) return null;
    this.store.mutate(data => {
      const fresh = data.webSessions.find(item => item.id === session.id);
      if (fresh) fresh.lastSeenAtUtc = now();
    });
    return { session, account: Object.assign(accountView(session.user), {
      betaRedemptions: this.listBetaRedemptions(session.user.id, 5)
    }) };
  }

  logoutWeb(token, remoteIp, requestId) {
    if (token) this.store.mutate(data => { data.webSessions = data.webSessions.filter(item => !safeEqual(item.tokenHash, sha256(token))); });
    this.audit(null, null, 'WEB_LOGOUT', true, remoteIp, null, requestId);
  }

  changePassword(webSession, currentPassword, newPassword, remoteIp, requestId) {
    const user = findUserById(this.store.data, webSession.userId);
    if (!user || !this.verifyPassword(currentPassword, user.passwordHash)) return '当前密码错误';
    const validation = validatePassword(newPassword, user.username);
    if (validation) return validation;
    if (this.verifyPassword(newPassword, user.passwordHash)) return '新密码不能与当前密码相同';
    const timestamp = now();
    if (!user.forcePasswordChange && user.passwordChangedAtUtc
      && timestamp < user.passwordChangedAtUtc + USER_CHANGE_COOLDOWN)
      return '密码修改后需等待 168 小时，当前仍在冷却中';
    this.store.mutate(data => {
      const fresh = findUserById(data, user.id);
      fresh.passwordHash = this.hashPassword(newPassword); fresh.passwordChangedAtUtc = timestamp; fresh.forcePasswordChange = false;
      revokeAll(data, fresh.id, 'password_changed');
    });
    this.audit(user.id, user.id, 'USER_CHANGE_PASSWORD', true, remoteIp, null, requestId);
    return null;
  }

  resetHwid(webSession, currentPassword, remoteIp, requestId) {
    const user = findUserById(this.store.data, webSession.userId);
    if (!user || !this.verifyPassword(currentPassword, user.passwordHash)) return '当前密码错误';
    const timestamp = now();
    if (user.hwidChangedAtUtc && timestamp < user.hwidChangedAtUtc + USER_CHANGE_COOLDOWN) return 'HWID 修改后需等待 168 小时，当前仍在冷却中';
    this.store.mutate(data => {
      const fresh = findUserById(data, user.id);
      fresh.hwidHash = null; fresh.hwidVersion = null; fresh.hwidQuality = null; fresh.hwidChangedAtUtc = timestamp;
      revokeAll(data, fresh.id, 'hwid_reset');
    });
    this.audit(user.id, user.id, 'USER_RESET_HWID', true, remoteIp, null, requestId);
    return null;
  }

  normalizeBetaCodeBatchInput(values, strictJson = false) {
    const input = values && typeof values === 'object' && !Array.isArray(values) ? values : {};
    const allowed = new Set(['product', 'durationDays', 'quantity', 'label', 'note', 'codeExpiresAtUtc']);
    if (strictJson && Object.keys(input).some(key => !allowed.has(key)))
      return error('INVALID_BETA_CODE_REQUEST', '发卡请求包含未知字段');
    if (strictJson && (typeof input.product !== 'string' || !Number.isInteger(input.quantity)))
      return error('INVALID_BETA_CODE_REQUEST', 'product 必须为字符串，quantity 必须为整数');
    if (strictJson && input.label != null && typeof input.label !== 'string')
      return error('INVALID_BETA_CODE_REQUEST', 'label 必须为字符串');
    if (strictJson && input.note != null && typeof input.note !== 'string')
      return error('INVALID_BETA_CODE_REQUEST', 'note 必须为字符串');
    const product = upper(input.product);
    const quantity = strictJson ? input.quantity : Number.parseInt(input.quantity, 10);
    const durationDays = strictJson ? input.durationDays : Number.parseInt(input.durationDays, 10);
    const label = text(input.label).trim();
    const note = text(input.note).trim();
    const codeExpiresAtUtc = input.codeExpiresAtUtc == null
      ? null : (strictJson ? input.codeExpiresAtUtc : Number.parseInt(input.codeExpiresAtUtc, 10));
    if (!['BETA_DURATION', 'BETA_PERMANENT'].includes(product))
      return error('INVALID_BETA_PRODUCT', '卡密类型无效');
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > MAX_BETA_CODES_PER_BATCH)
      return error('INVALID_BETA_CODE_QUANTITY', `单批卡密数量必须为 1-${MAX_BETA_CODES_PER_BATCH}`);
    if (product === 'BETA_DURATION' && (!Number.isInteger(durationDays) || durationDays < 1 || durationDays > 3650))
      return error('INVALID_BETA_DURATION', '时长卡有效期必须为 1-3650 天');
    if (strictJson && product === 'BETA_PERMANENT' && input.durationDays != null)
      return error('INVALID_BETA_DURATION', '永久卡不得提供 durationDays');
    if (label.length > 80) return error('INVALID_BETA_CODE_LABEL', '批次标签最多 80 个字符');
    if (note.length > 200) return error('INVALID_BETA_CODE_NOTE', '批次备注最多 200 个字符');
    if (codeExpiresAtUtc != null && (!Number.isInteger(codeExpiresAtUtc) || codeExpiresAtUtc <= now()))
      return error('INVALID_BETA_CODE_EXPIRY', '卡密失效时间必须晚于当前时间');
    return {
      Ok: true, product, quantity, durationDays: product === 'BETA_DURATION' ? durationDays : null,
      label, note, codeExpiresAtUtc
    };
  }

  createBetaCodeBatchInTransaction(data, actorUserId, normalized, timestamp, metadata = {}) {
    const generatedToday = data.betaCodeBatches
      .filter(batch => batch.createdByUserId === actorUserId && batch.createdAtUtc >= timestamp - 86400)
      .reduce((sum, batch) => sum + Number(batch.quantity || 0), 0);
    if (generatedToday + normalized.quantity > 1000)
      return error('BETA_CODE_DAILY_LIMIT', '单个账号滚动 24 小时最多生成 1000 张卡密');
    const existing = new Set(data.betaCodes.map(code => code.codeLookup));
    const batch = Object.assign({
      id: id(),
      label: normalized.label || `${normalized.product === 'BETA_PERMANENT' ? 'Permanent' : `${normalized.durationDays}-day`} Beta`,
      product: normalized.product,
      durationSeconds: normalized.product === 'BETA_DURATION' ? normalized.durationDays * 86400 : null,
      quantity: normalized.quantity,
      status: 'ACTIVE',
      codeExpiresAtUtc: normalized.codeExpiresAtUtc,
      createdAtUtc: timestamp,
      createdByUserId: actorUserId,
      disabledAtUtc: null,
      disabledByUserId: null,
      note: normalized.note
    }, metadata);
    const plaintextCodes = [];
    for (let index = 0; index < normalized.quantity; index += 1) {
      let plaintext; let lookup;
      do {
        plaintext = randomBetaCode();
        lookup = betaCodeLookup(this.redeemCodePepper, plaintext);
      } while (existing.has(lookup));
      existing.add(lookup);
      const normalizedCode = normalizeBetaCode(plaintext);
      data.betaCodes.push({
        id: id(), batchId: batch.id, codeLookup: lookup, displaySuffix: normalizedCode.slice(-4),
        status: 'UNUSED', createdAtUtc: timestamp, redeemedAtUtc: null,
        redeemedByUserId: null, redemptionId: null
      });
      plaintextCodes.push(plaintext);
    }
    data.betaCodeBatches.push(batch);
    return { Ok: true, Batch: batch, Codes: plaintextCodes };
  }

  createBetaCodeBatch(admin, values, remoteIp, requestId) {
    if (!admin || !admin.user || admin.user.role !== 'SUPER_ADMIN') {
      this.audit(admin && admin.userId, null, 'ADMIN_CREATE_BETA_CODE_BATCH', false, remoteIp, 'role_denied', requestId);
      return error('ADMIN_PERMISSION_DENIED', '只有超级管理员可以生成 Beta 卡密');
    }
    const normalized = this.normalizeBetaCodeBatchInput(values);
    if (!normalized.Ok) return normalized;
    const timestamp = now();
    let created;
    try {
      created = this.store.transaction(data => this.createBetaCodeBatchInTransaction(
        data, admin.userId, normalized, timestamp, { issueSource: 'WEB_ADMIN', reviewStatus: 'NOT_REQUIRED' }
      ));
    } catch (cause) {
      this.audit(admin.userId, null, 'ADMIN_CREATE_BETA_CODE_BATCH', false, remoteIp, 'store_write_failed', requestId);
      throw cause;
    }
    this.audit(admin.userId, null, 'ADMIN_CREATE_BETA_CODE_BATCH', Boolean(created.Ok), remoteIp,
      created.Ok ? `batch=${created.Batch.id};product=${normalized.product};quantity=${normalized.quantity}`
        : `error=${created.Error}`, requestId);
    return created;
  }

  betaCodeDeliveryAad(issue) {
    return Buffer.from(JSON.stringify({
      version: 1, issueId: issue.id, actorUserId: issue.actorUserId,
      batchId: issue.batchId, requestHash: issue.requestHash
    }), 'utf8');
  }

  encryptBetaCodeDelivery(codes, issue) {
    const nonce = crypto.randomBytes(12);
    const cipher = crypto.createCipheriv('aes-256-gcm', this.betaCodeDeliveryKey, nonce);
    cipher.setAAD(this.betaCodeDeliveryAad(issue));
    const ciphertext = Buffer.concat([cipher.update(JSON.stringify(codes), 'utf8'), cipher.final()]);
    return {
      deliveryNonce: nonce.toString('base64'),
      deliveryTag: cipher.getAuthTag().toString('base64'),
      deliveryCiphertext: ciphertext.toString('base64')
    };
  }

  decryptBetaCodeDelivery(issue) {
    if (!issue.deliveryNonce || !issue.deliveryTag || !issue.deliveryCiphertext)
      throw new Error('beta code delivery data is unavailable');
    const decipher = crypto.createDecipheriv(
      'aes-256-gcm', this.betaCodeDeliveryKey, Buffer.from(issue.deliveryNonce, 'base64')
    );
    decipher.setAAD(this.betaCodeDeliveryAad(issue));
    decipher.setAuthTag(Buffer.from(issue.deliveryTag, 'base64'));
    const plaintext = Buffer.concat([
      decipher.update(Buffer.from(issue.deliveryCiphertext, 'base64')), decipher.final()
    ]).toString('utf8');
    const codes = JSON.parse(plaintext);
    if (!Array.isArray(codes) || !codes.length || codes.some(code => typeof code !== 'string' || !normalizeBetaCode(code)))
      throw new Error('beta code delivery payload is invalid');
    return codes;
  }

  issueBetaCodesFromClient(accessToken, values, idempotencyKey, remoteIp, requestId) {
    const authenticated = this.validateClientAction(accessToken);
    if (!authenticated.Ok) return authenticated;
    const key = text(idempotencyKey).trim();
    if (!/^[\x21-\x7e]{16,128}$/.test(key))
      return error('INVALID_IDEMPOTENCY_KEY', 'Idempotency-Key 必须为 16-128 个可见 ASCII 字符');
    const normalized = this.normalizeBetaCodeBatchInput(values, true);
    if (!normalized.Ok) return normalized;
    const timestamp = now();
    const keyHash = sha256(key);
    const requestHash = sha256(JSON.stringify({
      product: normalized.product, durationDays: normalized.durationDays, quantity: normalized.quantity,
      label: normalized.label, note: normalized.note, codeExpiresAtUtc: normalized.codeExpiresAtUtc
    }));
    let issued;
    try {
      issued = this.store.transaction(data => {
        for (const record of data.betaCodeIssueRequests) {
          if (record.deliveryExpiresAtUtc <= timestamp && record.deliveryCiphertext) {
            record.deliveryNonce = null; record.deliveryTag = null; record.deliveryCiphertext = null;
          }
        }
        const prior = data.betaCodeIssueRequests.find(record => record.actorUserId === authenticated.user.id
          && safeEqual(record.idempotencyKeyHash, keyHash));
        if (prior) {
          if (!safeEqual(prior.requestHash, requestHash))
            return error('IDEMPOTENCY_KEY_REUSED', '该 Idempotency-Key 已用于不同的发卡请求');
          if (prior.deliveryExpiresAtUtc <= timestamp || !prior.deliveryCiphertext)
            return Object.assign(error('ISSUE_RESULT_EXPIRED', '该批卡密已生成，但明文交付窗口已过期'), { BatchId: prior.batchId });
          const batch = data.betaCodeBatches.find(item => item.id === prior.batchId);
          if (!batch) throw new Error('beta code issue references a missing batch');
          const currentSession = data.sessions.find(item => item.id === authenticated.session.id);
          if (currentSession) currentSession.lastSeenAtUtc = timestamp;
          prior.deliveredAtUtc = timestamp;
          return {
            Ok: true, Batch: batch, Codes: this.decryptBetaCodeDelivery(prior),
            IdempotentReplay: true, DeliveryExpiresAtUtc: prior.deliveryExpiresAtUtc
          };
        }
        const rateAllowed = this.consumeRateGroupInData(data, [
          { bucket: `beta-issue-user:${authenticated.user.id}`, limit: BETA_CODE_ISSUE_USER_RATE_LIMIT,
            seconds: BETA_CODE_ISSUE_RATE_WINDOW_SECONDS },
          { bucket: `beta-issue-ip:${this.hashIp(remoteIp)}`, limit: BETA_CODE_ISSUE_IP_RATE_LIMIT,
            seconds: BETA_CODE_ISSUE_RATE_WINDOW_SECONDS }
        ], timestamp);
        if (!rateAllowed) return error('BETA_CODE_ISSUE_RATE_LIMITED', '发卡请求过于频繁，请稍后再试');
        const issueId = id();
        const created = this.createBetaCodeBatchInTransaction(data, authenticated.user.id, normalized, timestamp, {
          issueSource: 'CLIENT_API',
          requestedByUserId: authenticated.user.id,
          requestedByUsernameSnapshot: authenticated.user.username,
          requestedByRoleSnapshot: authenticated.user.role,
          requestId,
          reviewStatus: 'PENDING_REVIEW',
          reviewedAtUtc: null,
          reviewedByUserId: null,
          reviewNote: ''
        });
        if (!created.Ok) return created;
        const issue = {
          id: issueId,
          actorUserId: authenticated.user.id,
          actorUsernameSnapshot: authenticated.user.username,
          actorRoleSnapshot: authenticated.user.role,
          clientSessionId: authenticated.session.id,
          clientEdition: authenticated.session.clientEdition,
          clientVersion: authenticated.session.clientVersion,
          buildId: authenticated.session.buildId,
          launcherVersion: authenticated.session.launcherVersion || '',
          remoteIpHash: this.hashIp(remoteIp),
          idempotencyKeyHash: keyHash,
          requestHash,
          batchId: created.Batch.id,
          status: 'COMPLETED',
          createdAtUtc: timestamp,
          deliveryExpiresAtUtc: timestamp + BETA_CODE_ISSUE_DELIVERY_SECONDS,
          deliveredAtUtc: timestamp,
          deliveryNonce: null,
          deliveryTag: null,
          deliveryCiphertext: null
        };
        Object.assign(issue, this.encryptBetaCodeDelivery(created.Codes, issue));
        data.betaCodeIssueRequests.push(issue);
        const currentSession = data.sessions.find(item => item.id === authenticated.session.id);
        if (currentSession) currentSession.lastSeenAtUtc = timestamp;
        return {
          Ok: true, Batch: created.Batch, Codes: created.Codes,
          IdempotentReplay: false, DeliveryExpiresAtUtc: issue.deliveryExpiresAtUtc
        };
      });
    } catch (cause) {
      try {
        this.audit(authenticated.user.id, null, 'CLIENT_ISSUE_BETA_CODE_BATCH', false, remoteIp,
          `error=store_or_delivery_failed;idempotency=${keyHash.slice(0, 12)}`, requestId);
      } catch { /* Preserve the original failure response if audit persistence also fails. */ }
      return error('BETA_CODE_ISSUE_FAILED', '发卡失败，请携带 requestId 联系管理员，禁止更换幂等键重试');
    }
    const detail = issued.Ok
      ? `batch=${issued.Batch.id};actor=${authenticated.user.id};actorRole=${authenticated.user.role};session=${authenticated.session.id};product=${normalized.product};durationDays=${normalized.durationDays};quantity=${normalized.quantity};idempotency=${keyHash.slice(0, 12)};replay=${issued.IdempotentReplay};review=${issued.Batch.reviewStatus || 'NOT_REQUIRED'}`
      : `actor=${authenticated.user.id};error=${issued.Error};idempotency=${keyHash.slice(0, 12)}`;
    this.audit(authenticated.user.id, null, 'CLIENT_ISSUE_BETA_CODE_BATCH', Boolean(issued.Ok), remoteIp, detail, requestId);
    return issued;
  }

  reviewBetaCodeBatch(admin, batchId, reviewNote, remoteIp, requestId) {
    if (!admin || !admin.user || admin.user.role !== 'SUPER_ADMIN') {
      this.audit(admin && admin.userId, null, 'ADMIN_REVIEW_BETA_CODE_BATCH', false, remoteIp, 'role_denied', requestId);
      return error('ADMIN_PERMISSION_DENIED', '只有超级管理员可以审查客户端发卡批次');
    }
    const note = text(reviewNote).trim();
    if (note.length > 200) return error('INVALID_REVIEW_NOTE', '审查备注最多 200 个字符');
    const timestamp = now();
    const reviewed = this.store.transaction(data => {
      const batch = data.betaCodeBatches.find(item => item.id === batchId);
      if (!batch) return error('BETA_CODE_BATCH_NOT_FOUND', '卡密批次不存在');
      if (batch.issueSource !== 'CLIENT_API')
        return error('BETA_CODE_BATCH_NOT_REVIEWABLE', '该批次不是客户端自动发卡批次');
      batch.reviewStatus = 'REVIEWED';
      batch.reviewedAtUtc = timestamp;
      batch.reviewedByUserId = admin.userId;
      batch.reviewNote = note;
      return { Ok: true, Batch: batch };
    });
    this.audit(admin.userId, reviewed.Ok && reviewed.Batch ? reviewed.Batch.requestedByUserId : null,
      'ADMIN_REVIEW_BETA_CODE_BATCH', Boolean(reviewed.Ok), remoteIp,
      reviewed.Ok ? `batch=${batchId};requester=${reviewed.Batch.requestedByUserId}` : `batch=${batchId};error=${reviewed.Error}`,
      requestId);
    return reviewed;
  }

  disableBetaCodeBatch(admin, batchId, remoteIp, requestId) {
    if (!admin || !admin.user || admin.user.role !== 'SUPER_ADMIN') {
      this.audit(admin && admin.userId, null, 'ADMIN_DISABLE_BETA_CODE_BATCH', false, remoteIp, 'role_denied', requestId);
      return error('ADMIN_PERMISSION_DENIED', '只有超级管理员可以禁用 Beta 卡密批次');
    }
    const timestamp = now();
    const disabled = this.store.transaction(data => {
      const batch = data.betaCodeBatches.find(item => item.id === batchId);
      if (!batch) return false;
      if (batch.status === 'DISABLED') return true;
      batch.status = 'DISABLED'; batch.disabledAtUtc = timestamp; batch.disabledByUserId = admin.userId;
      for (const code of data.betaCodes) {
        if (code.batchId === batch.id && code.status === 'UNUSED') code.status = 'DISABLED';
      }
      return true;
    });
    this.audit(admin.userId, null, 'ADMIN_DISABLE_BETA_CODE_BATCH', disabled, remoteIp,
      disabled ? `batch=${batchId}` : 'batch_not_found', requestId);
    return disabled ? { Ok: true } : error('BETA_CODE_BATCH_NOT_FOUND', '卡密批次不存在');
  }

  listBetaCodeBatches() {
    const codes = this.store.data.betaCodes;
    const issues = new Map(this.store.data.betaCodeIssueRequests.map(issue => [issue.batchId, issue]));
    return [...this.store.data.betaCodeBatches].sort((a, b) => b.createdAtUtc - a.createdAtUtc).map(batch => {
      const members = codes.filter(code => code.batchId === batch.id);
      const issue = issues.get(batch.id);
      return Object.assign({}, batch, {
        unused: members.filter(code => code.status === 'UNUSED').length,
        redeemed: members.filter(code => code.status === 'REDEEMED').length,
        disabled: members.filter(code => code.status === 'DISABLED').length,
        issueClientEdition: issue && issue.clientEdition,
        issueClientVersion: issue && issue.clientVersion,
        issueBuildId: issue && issue.buildId,
        issueLauncherVersion: issue && issue.launcherVersion
      });
    });
  }

  calculateBetaCodeValue(admin, fromUtc, toExclusiveUtc) {
    if (!admin || !admin.user || !isAdmin(admin.user))
      return error('ADMIN_PERMISSION_DENIED', '没有管理员权限');
    if (!Number.isInteger(fromUtc) || !Number.isInteger(toExclusiveUtc) || fromUtc >= toExclusiveUtc)
      return error('INVALID_BILLING_RANGE', '计费时间范围无效');
    const categories = new Map(BETA_CODE_VALUE_RULES.map(rule => [rule.key, {
      key: rule.key, label: rule.label, unitValue: rule.unitValue, quantity: 0, subtotal: 0
    }]));
    let batchCount = 0;
    let totalQuantity = 0;
    let pricedQuantity = 0;
    let unpricedQuantity = 0;
    let totalValue = 0;
    for (const batch of this.store.data.betaCodeBatches) {
      if (batch.createdAtUtc < fromUtc || batch.createdAtUtc >= toExclusiveUtc) continue;
      const quantity = Math.max(0, Number.parseInt(batch.quantity, 10) || 0);
      const rule = betaCodeValueCategory(batch);
      if (!categories.has(rule.key)) categories.set(rule.key, {
        key: rule.key, label: rule.label, unitValue: null, quantity: 0, subtotal: 0
      });
      const category = categories.get(rule.key);
      category.quantity += quantity;
      batchCount += 1;
      totalQuantity += quantity;
      if (rule.unitValue == null) {
        unpricedQuantity += quantity;
      } else {
        const subtotal = quantity * rule.unitValue;
        category.subtotal += subtotal;
        pricedQuantity += quantity;
        totalValue += subtotal;
      }
    }
    return {
      Ok: true, FromUtc: fromUtc, ToExclusiveUtc: toExclusiveUtc, BatchCount: batchCount,
      TotalQuantity: totalQuantity, PricedQuantity: pricedQuantity,
      UnpricedQuantity: unpricedQuantity, TotalValue: totalValue,
      Categories: [...categories.values()].filter(category => category.quantity > 0 || category.unitValue != null)
    };
  }

  lookupBetaCode(admin, plaintextCode, remoteIp, requestId) {
    if (!admin || !admin.user || !isAdmin(admin.user))
      return error('ADMIN_PERMISSION_DENIED', '没有管理员权限');
    const normalized = normalizeBetaCode(plaintextCode);
    const lookup = normalized && betaCodeLookup(this.redeemCodePepper, normalized);
    const code = lookup && this.store.data.betaCodes.find(item => safeEqual(item.codeLookup, lookup));
    if (!code) {
      this.audit(admin.userId, null, 'ADMIN_LOOKUP_BETA_CODE', false, remoteIp, 'not_found', requestId);
      return error('INVALID_REDEEM_CODE', '卡密不存在');
    }
    const batch = this.store.data.betaCodeBatches.find(item => item.id === code.batchId);
    const redemption = code.redemptionId && this.store.data.betaRedemptions.find(item => item.id === code.redemptionId);
    const redeemedUser = redemption && findUserById(this.store.data, redemption.userId);
    this.audit(admin.userId, redeemedUser && redeemedUser.id, 'ADMIN_LOOKUP_BETA_CODE', true, remoteIp,
      `batch=${code.batchId};suffix=${code.displaySuffix};status=${code.status}`, requestId);
    return {
      Ok: true,
      Message: `卡密 ****${code.displaySuffix}：${code.status}；批次 ${batch ? batch.label : code.batchId}`
        + (redeemedUser ? `；兑换账号 ${redeemedUser.username}；兑换时间 ${formatTime(code.redeemedAtUtc)}` : '')
    };
  }

  listBetaRedemptions(userId, limit = 200) {
    const users = new Map(this.store.data.users.map(user => [user.id, user.username]));
    const batches = new Map(this.store.data.betaCodeBatches.map(batch => [batch.id, batch]));
    return this.store.data.betaRedemptions
      .filter(item => !userId || item.userId === userId)
      .slice(-Math.max(1, Math.min(1000, limit))).reverse()
      .map(item => redemptionView(item, batches.get(item.batchId), users.get(item.userId)));
  }

  redeemBetaCode(webSession, plaintextCode, remoteIp, requestId) {
    const user = webSession && findUserById(this.store.data, webSession.userId);
    if (!user || user.status !== 'ACTIVE')
      return error('ACCOUNT_NOT_ACTIVE', '账号不可用');
    const normalized = normalizeBetaCode(plaintextCode);
    const lookup = normalized && betaCodeLookup(this.redeemCodePepper, normalized);
    const rateAllowed = this.consumeRateGroup([
      { bucket: `redeem-user-15m:${user.id}`, limit: 5, seconds: 900 },
      { bucket: `redeem-user-day:${user.id}`, limit: 20, seconds: 86400 },
      { bucket: `redeem-ip-15m:${this.hashIp(remoteIp)}`, limit: 15, seconds: 900 }
    ]);
    if (!rateAllowed) {
      this.audit(user.id, user.id, 'USER_REDEEM_BETA_CODE_FAILED', false, remoteIp, 'rate_limited', requestId);
      return error('REDEEM_RATE_LIMITED', '兑换尝试过于频繁，请稍后再试');
    }
    const timestamp = now();
    let result;
    try {
      result = this.store.transaction(data => {
        const freshUser = findUserById(data, user.id);
        if (!freshUser || freshUser.status !== 'ACTIVE') return error('ACCOUNT_NOT_ACTIVE', '账号不可用');
        const code = lookup && data.betaCodes.find(item => safeEqual(item.codeLookup, lookup));
        if (!code) return error('INVALID_REDEEM_CODE', '卡密无效或已使用');
        if (code.status === 'REDEEMED') {
          if (code.redeemedByUserId !== freshUser.id) return error('INVALID_REDEEM_CODE', '卡密无效或已使用');
          const prior = data.betaRedemptions.find(item => item.id === code.redemptionId);
          if (!prior) return error('INVALID_REDEEM_CODE', '卡密无效或已使用');
          const priorBatch = data.betaCodeBatches.find(item => item.id === code.batchId);
          return { Ok: true, Account: accountView(freshUser), Redemption: redemptionView(prior, priorBatch), AlreadyApplied: true };
        }
        const batch = data.betaCodeBatches.find(item => item.id === code.batchId);
        if (!batch || batch.status !== 'ACTIVE' || code.status !== 'UNUSED'
          || (batch.codeExpiresAtUtc && batch.codeExpiresAtUtc <= timestamp))
          return error('INVALID_REDEEM_CODE', '卡密无效或已使用');
        const previousTier = freshUser.tier;
        const previousExpiry = freshUser.betaExpiresAtUtc || null;
        let newExpiry;
        if (batch.product === 'BETA_PERMANENT') {
          if (previousTier === 'BETA' && previousExpiry >= PERMANENT_BETA_EXPIRES_AT_UTC)
            return error('BETA_EXPIRY_LIMIT', '当前账号已是永久 Beta');
          newExpiry = PERMANENT_BETA_EXPIRES_AT_UTC;
        } else if (batch.product === 'BETA_DURATION') {
          const duration = Number.parseInt(batch.durationSeconds, 10);
          if (!Number.isFinite(duration) || duration < 86400 || duration > MAX_DURATION_BETA_SECONDS)
            return error('INVALID_REDEEM_CODE', '卡密无效或已使用');
          const base = previousTier === 'BETA' && previousExpiry > timestamp ? previousExpiry : timestamp;
          newExpiry = base + duration;
          if (!Number.isSafeInteger(newExpiry) || newExpiry > timestamp + MAX_BETA_EXPIRY_SECONDS)
            return error('BETA_EXPIRY_LIMIT', 'Beta 到期时间超过可叠加上限');
        } else {
          return error('INVALID_REDEEM_CODE', '卡密无效或已使用');
        }
        const redemption = {
          id: id(), codeId: code.id, batchId: batch.id, userId: freshUser.id,
          product: batch.product, durationSeconds: batch.durationSeconds,
          codeSuffix: code.displaySuffix,
          previousTier, previousBetaExpiresAtUtc: previousExpiry,
          newTier: 'BETA', newBetaExpiresAtUtc: newExpiry, redeemedAtUtc: timestamp,
          remoteIpHash: this.hashIp(remoteIp), requestId
        };
        code.status = 'REDEEMED'; code.redeemedAtUtc = timestamp;
        code.redeemedByUserId = freshUser.id; code.redemptionId = redemption.id;
        freshUser.tier = 'BETA'; freshUser.betaExpiresAtUtc = newExpiry;
        data.betaRedemptions.push(redemption);
        return { Ok: true, Account: accountView(freshUser), Redemption: redemptionView(redemption, batch), AlreadyApplied: false };
      });
    } catch (cause) {
      this.audit(user.id, user.id, 'USER_REDEEM_BETA_CODE_FAILED', false, remoteIp, 'store_write_failed', requestId);
      throw cause;
    }
    const suffix = normalized ? normalized.slice(-4) : '';
    this.audit(user.id, user.id, result.Ok ? 'USER_REDEEM_BETA_CODE' : 'USER_REDEEM_BETA_CODE_FAILED', result.Ok,
      remoteIp, result.Ok ? `suffix=${suffix};already=${Boolean(result.AlreadyApplied)}` : `suffix=${suffix};error=${result.Error}`, requestId);
    return result;
  }

  listUsers(search, requestedPage) {
    const query = text(search).trim().toLowerCase().slice(0, 64);
    const all = [...this.store.data.users].sort((a, b) => b.createdAtUtc - a.createdAtUtc);
    const filtered = query ? all.filter(user => [
      user.username, user.id, user.role, user.tier, user.status, user.creationSource
    ].some(value => text(value).toLowerCase().includes(query))) : all;
    const pageCount = Math.max(1, Math.ceil(filtered.length / ADMIN_PAGE_SIZE));
    const parsedPage = Number.parseInt(requestedPage, 10);
    const page = Math.min(pageCount, Math.max(1, Number.isFinite(parsedPage) ? parsedPage : 1));
    const offset = (page - 1) * ADMIN_PAGE_SIZE;
    return {
      users: filtered.slice(offset, offset + ADMIN_PAGE_SIZE),
      query,
      page,
      pageCount,
      matched: filtered.length,
      total: all.length
    };
  }
  listAuditLogs(limit = 200) {
    const users = new Map(this.store.data.users.map(user => [user.id, user.username]));
    return this.store.data.auditLogs.slice(-Math.max(1, Math.min(1000, limit))).reverse().map(entry => ({
      occurredAtUtc: entry.occurredAtUtc,
      actor: entry.actorUserId ? users.get(entry.actorUserId) || entry.actorUserId.slice(0, 8) : '-',
      target: entry.targetUserId ? users.get(entry.targetUserId) || entry.targetUserId.slice(0, 8) : '-',
      action: entry.action,
      success: Boolean(entry.success),
      details: text(entry.details).slice(0, 160),
      requestId: text(entry.requestId).slice(0, 32)
    }));
  }
  getUser(idValue) { return findUserById(this.store.data, idValue); }

  createUser(admin, username, password, role, tier, expiry, remoteIp, requestId) {
    if (!admin || !isAdmin(admin.user)) return '只有管理员可以创建账号';
    if (!validUsername(username)) return '用户名格式不正确';
    const validation = validatePassword(password, username);
    if (validation) return validation;
    role = upper(role || 'USER');
    if (!['USER', 'SUPPORT_ADMIN'].includes(role)) return '账号角色无效';
    if (role === 'SUPPORT_ADMIN' && admin.user.role !== 'SUPER_ADMIN') {
      this.audit(admin.userId, null, 'ADMIN_CREATE_SUPPORT', false, remoteIp, 'role_denied', requestId);
      return '只有超级管理员可以创建支持管理员';
    }
    tier = upper(tier || 'FREE');
    if (!['FREE', 'BETA'].includes(tier)) return '账号等级无效';
    if (tier === 'BETA' && (!expiry || expiry <= now())) return 'Beta 必须设置未来到期时间';
    const created = this.store.mutate(data => {
      if (findUserByName(data, username)) return null;
      const user = newUser(username, this.hashPassword(password), {
        role, tier, betaExpiresAtUtc: tier === 'BETA' ? expiry : null,
        createdBy: admin.userId, creationSource: role === 'SUPPORT_ADMIN' ? 'ADMIN_CREATED_SUPPORT' : 'ADMIN_CREATED',
        passwordChangedAtUtc: now(), forcePasswordChange: true
      });
      data.users.push(user); return user;
    });
    if (!created) return '用户名已存在';
    this.audit(admin.userId, created.id, role === 'SUPPORT_ADMIN' ? 'ADMIN_CREATE_SUPPORT' : 'ADMIN_CREATE_USER',
      true, remoteIp, `role=${role};tier=${tier}`, requestId);
    return null;
  }

  adminAction(admin, userId, action, values, remoteIp, requestId) {
    if (!admin || !isAdmin(admin.user)) return '没有管理员权限';
    const target = findUserById(this.store.data, userId);
    if (!target) return '账号不存在';
    if (admin.user.role === 'SUPPORT_ADMIN' && target.role !== 'USER') {
      this.audit(admin.userId, target.id, `ADMIN_${String(action).toUpperCase().replace(/-/g, '_')}`,
        false, remoteIp, 'administrator_target_denied', requestId);
      return '支持管理员只能管理普通用户';
    }
    const timestamp = now();
    let failure = null;
    this.store.mutate(data => {
      const user = findUserById(data, userId);
      switch (action) {
        case 'ban': user.status = 'BANNED'; user.banReason = text(values.reason) || '管理员封禁'; break;
        case 'unban': user.status = 'ACTIVE'; user.banReason = null; break;
        case 'delete': user.status = 'DELETED'; break;
        case 'reset-hwid': user.hwidHash = null; user.hwidVersion = null; user.hwidQuality = null; user.hwidChangedAtUtc = timestamp; break;
        case 'set-hwid':
          if (!validFingerprint(text(values.fingerprint), 'v1')) { failure = 'HWID 指纹无效'; return; }
          user.hwidHash = this.hashHwid(text(values.fingerprint)); user.hwidVersion = 'v1'; user.hwidQuality = 'ADMIN_SET'; user.hwidChangedAtUtc = timestamp; break;
        case 'password': {
          const validation = validatePassword(text(values.password), user.username);
          if (validation) { failure = validation; return; }
          user.passwordHash = this.hashPassword(text(values.password)); user.passwordChangedAtUtc = timestamp; user.forcePasswordChange = true; break;
        }
        case 'tier': {
          const tier = upper(values.tier || 'FREE');
          if (tier === 'BETA') {
            const expiry = Number.parseInt(values.expiry, 10);
            if (!Number.isFinite(expiry) || expiry <= timestamp) { failure = 'Beta 到期时间无效'; return; }
            user.tier = 'BETA'; user.betaExpiresAtUtc = expiry;
          } else if (tier === 'FREE') { user.tier = 'FREE'; user.betaExpiresAtUtc = null; }
          else { failure = '账号等级无效'; return; }
          break;
        }
        default: failure = '未知操作'; return;
      }
      revokeAll(data, user.id, `admin_${action}`);
    });
    if (failure) return failure;
    this.audit(admin.userId, target.id, `ADMIN_${String(action).toUpperCase().replace(/-/g, '_')}`, true, remoteIp, null, requestId);
    return null;
  }

  betaPublicAccess() {
    const expiresAtUtc = clamp(this.store.data.serviceSettings && this.store.data.serviceSettings.betaPublicAccessUntilUtc,
      0, 253402300799, 0);
    return { enabled: expiresAtUtc > now(), expiresAtUtc: expiresAtUtc || null };
  }

  setBetaPublicAccess(admin, enabled, expiry, remoteIp, requestId) {
    if (!admin || admin.user.role !== 'SUPER_ADMIN') {
      this.audit(admin && admin.userId, null, 'ADMIN_SET_BETA_PUBLIC_ACCESS', false, remoteIp, 'role_denied', requestId);
      return '只有超级管理员可以管理限时 Beta 公益';
    }
    const timestamp = now();
    const expiresAtUtc = enabled ? Number.parseInt(expiry, 10) : 0;
    if (enabled && (!Number.isFinite(expiresAtUtc) || expiresAtUtc <= timestamp)) {
      this.audit(admin.userId, null, 'ADMIN_SET_BETA_PUBLIC_ACCESS', false, remoteIp, 'invalid_expiry', requestId);
      return '限时 Beta 公益必须设置未来的结束时间';
    }
    this.store.mutate(data => {
      if (!data.serviceSettings || typeof data.serviceSettings !== 'object' || Array.isArray(data.serviceSettings)) {
        data.serviceSettings = {};
      }
      data.serviceSettings.betaPublicAccessUntilUtc = enabled ? expiresAtUtc : 0;
    });
    this.audit(admin.userId, null, 'ADMIN_SET_BETA_PUBLIC_ACCESS', true, remoteIp,
      enabled ? `enabled;expires=${expiresAtUtc}` : 'disabled', requestId);
    return null;
  }

  ensureAdmin(username, password) {
    if (!validUsername(username)) throw new Error('Administrator username is invalid');
    const validation = validatePassword(password, username);
    if (validation) throw new Error(validation);
    let created = false;
    this.store.mutate(data => {
      const existing = findUserByName(data, username);
      if (existing) {
        if (existing.role !== 'SUPER_ADMIN') throw new Error('Administrator username is already used by a non-administrator account');
      } else {
        data.users.push(newUser(username, this.hashPassword(password), { role: 'SUPER_ADMIN', creationSource: 'BOOTSTRAP' }));
        created = true;
      }
    });
    return created;
  }

  issueSession(user, hwidHash, fingerprint, edition, clientVersion, buildId, launcherVersion, remoteIp, requestId) {
    const access = randomToken(); const refresh = randomToken(); const timestamp = now();
    const accessExpiresAt = timestamp + this.config.AccessTokenMinutes * 60;
    const refreshExpiresAt = timestamp + this.config.RefreshTokenDays * 86400;
    this.store.mutate(data => data.sessions.push({
      id: id(), userId: user.id, accessTokenHash: sha256(access), refreshTokenHash: sha256(refresh), hwidHash,
      clientEdition: edition, clientVersion, buildId, launcherVersion,
      accessExpiresAtUtc: accessExpiresAt, refreshExpiresAtUtc: refreshExpiresAt,
      createdAtUtc: timestamp, lastSeenAtUtc: timestamp, revokedAtUtc: null, revokeReason: null
    }));
    this.audit(user.id, user.id, 'CLIENT_LOGIN', true, remoteIp, `edition=${edition}`, requestId);
    const account = this.clientAccountView(user, edition);
    return successSession(account, access, refresh, accessExpiresAt, refreshExpiresAt,
      this.entitlementProof(user, account, fingerprint, edition, clientVersion, buildId,
        access, timestamp, accessExpiresAt, refreshExpiresAt));
  }

  entitlementProof(user, account, fingerprint, edition, clientVersion, buildId,
                   accessToken, issuedAt, accessExpiresAt, refreshExpiresAt) {
    const claims = {
      version: 1,
      keyId: this.entitlementSigner.keyId,
      subject: user.id,
      username: account.username,
      edition: upper(edition),
      tier: account.tier,
      clientVersion: text(clientVersion),
      buildId: text(buildId),
      deviceFingerprintHash: sha256(fingerprint),
      accessTokenHash: sha256(accessToken),
      issuedAt,
      accessExpiresAt,
      refreshExpiresAt,
      betaExpiresAt: account.betaExpiresAt == null ? null : account.betaExpiresAt
    };
    const payload = Buffer.from(JSON.stringify(claims), 'utf8').toString('base64url');
    const signature = crypto.sign(null, Buffer.from(payload, 'ascii'), this.entitlementSigner.privateKey)
      .toString('base64url');
    return `${payload}.${signature}`;
  }

  clientSession(accessToken) {
    const session = this.clientSessionRecord(accessToken);
    if (!session) return null;
    const user = findUserById(this.store.data, session.userId);
    return user ? { user, session } : null;
  }

  clientSessionRecord(accessToken) {
    if (!accessToken) return null;
    return this.store.data.sessions.find(item => !item.revokedAtUtc && item.accessExpiresAtUtc > now()
      && safeEqual(item.accessTokenHash, sha256(accessToken))) || null;
  }

  validateAccount(user, edition) {
    if (user.status === 'BANNED') return error('ACCOUNT_BANNED', `账号已被封禁${user.banReason ? `：${user.banReason}` : ''}`);
    if (user.status === 'DELETED') return error('ACCOUNT_DELETED', '账号不存在或已删除');
    if (user.forcePasswordChange) return error('PASSWORD_CHANGE_REQUIRED', '请先登录用户面板修改临时密码');
    if (edition === 'BETA') {
      if (this.hasActiveBetaEntitlement(user)) return null;
      if (user.tier !== 'BETA') return error('LICENSE_REQUIRED', '当前账号未开通 Beta');
      return error('LICENSE_EXPIRED', 'Beta 授权已到期');
    }
    return null;
  }

  hasActiveBetaEntitlement(user) {
    return Boolean(user && user.tier === 'BETA' && user.betaExpiresAtUtc && user.betaExpiresAtUtc > now())
      || this.betaPublicAccess().enabled;
  }

  clientAccountView(user, edition) {
    const publicBeta = upper(edition) === 'BETA'
      && !(user.tier === 'BETA' && user.betaExpiresAtUtc && user.betaExpiresAtUtc > now())
      && this.betaPublicAccess();
    return accountView(user, publicBeta && publicBeta.enabled ? {
      tier: 'BETA', betaExpiresAt: publicBeta.expiresAtUtc, betaPublicAccess: true
    } : { betaPublicAccess: false });
  }

  revokeReplayedRefresh(refreshHash, remoteIp, requestId) {
    let userId = null;
    this.store.mutate(data => {
      const used = data.usedRefreshTokens.find(item => safeEqual(item.tokenHash, refreshHash));
      if (!used) return;
      const session = data.sessions.find(item => item.id === used.sessionId);
      if (session && !session.revokedAtUtc) { session.revokedAtUtc = now(); session.revokeReason = 'refresh_reuse'; userId = session.userId; }
    });
    if (userId) this.audit(userId, userId, 'REFRESH_TOKEN_REUSE', false, remoteIp, 'session_revoked', requestId);
  }

  consumeRate(bucket, limit, seconds) {
    return this.store.mutate(data => {
      return this.consumeRateGroupInData(data, [{ bucket, limit, seconds }], now());
    });
  }

  consumeRateGroupInData(data, groups, timestamp) {
    data.rateLimitEvents = data.rateLimitEvents.filter(item => item.occurredAtUtc >= timestamp - 8 * 86400);
    for (const group of groups) {
      const cutoff = timestamp - group.seconds;
      const count = data.rateLimitEvents.filter(item => item.bucketKey === group.bucket && item.occurredAtUtc >= cutoff).length;
      if (count >= group.limit) return false;
    }
    for (const group of groups) data.rateLimitEvents.push({ bucketKey: group.bucket, occurredAtUtc: timestamp });
    return true;
  }

  consumeRateGroup(groups) {
    return this.store.mutate(data => {
      const timestamp = now();
      return this.consumeRateGroupInData(data, groups, timestamp);
    });
  }

  audit(actor, target, action, success, remoteIp, details, requestId) {
    this.store.mutate(data => {
      data.auditLogs.push({ occurredAtUtc: now(), actorUserId: actor || null, targetUserId: target || null, action,
        success: Boolean(success), remoteIpHash: this.hashIp(remoteIp), details: details || null, requestId });
      if (data.auditLogs.length > 50000) data.auditLogs.splice(0, data.auditLogs.length - 50000);
    });
  }

  hashHwid(value) { return hmac(this.hwidPepper, value); }
  hashIp(value) { return hmac(this.ipPepper, value); }
  hashPassword(value) {
    const salt = crypto.randomBytes(16); const material = crypto.createHmac('sha256', this.passwordPepper).update(value, 'utf8').digest();
    const digest = crypto.pbkdf2Sync(material, salt, PBKDF2_ITERATIONS, 32, 'sha256');
    return `pbkdf2-sha256-pepper$${PBKDF2_ITERATIONS}$${salt.toString('base64')}$${digest.toString('base64')}`;
  }
  verifyPassword(value, encoded) {
    try {
      const parts = String(encoded || '').split('$');
      if (parts.length !== 4 || !['pbkdf2-sha256-pepper', 'pbkdf2-sha256'].includes(parts[0])) return false;
      const iterations = Number.parseInt(parts[1], 10);
      if (!Number.isFinite(iterations) || iterations < 100000 || iterations > 2000000) return false;
      const salt = Buffer.from(parts[2], 'base64'); const expected = Buffer.from(parts[3], 'base64');
      const material = parts[0] === 'pbkdf2-sha256-pepper'
        ? crypto.createHmac('sha256', this.passwordPepper).update(value, 'utf8').digest() : Buffer.from(value, 'utf8');
      const actual = crypto.pbkdf2Sync(material, salt, iterations, expected.length, 'sha256');
      return actual.length === expected.length && crypto.timingSafeEqual(actual, expected);
    } catch { return false; }
  }
}

class IrcHub {
  constructor(auth) {
    this.auth = auth;
    this.clients = new Map();
    this.presence = new Map();
    this.messageTimes = new Map();
    this.recentMessages = [];
    this.identityChallenges = new Map();
    this.sequence = 0;
    this.maintenanceTimer = null;
  }

  start() {
    if (this.maintenanceTimer) return;
    this.maintenanceTimer = setInterval(() => this.maintain(), 20000);
    this.maintenanceTimer.unref();
  }

  close() {
    if (this.maintenanceTimer) clearInterval(this.maintenanceTimer);
    this.maintenanceTimer = null;
    for (const client of this.clients.values()) {
      if (client.response && !client.response.writableEnded) client.response.end();
    }
    this.clients.clear();
    this.presence.clear();
    this.messageTimes.clear();
    this.identityChallenges.clear();
  }

  authorize(accessToken) {
    const authenticated = this.auth.clientSession(accessToken);
    if (!authenticated) return null;
    const accountError = this.auth.validateAccount(authenticated.user, authenticated.session.clientEdition);
    return accountError ? null : authenticated;
  }

  connect(request, response, accessToken, requestId) {
    const authenticated = this.authorize(accessToken);
    if (!authenticated) throw new RequestError(401, 'SESSION_REVOKED', '登录会话无效或已过期');
    if (this.clients.size >= MAX_IRC_CONNECTIONS)
      throw new RequestError(503, 'IRC_CAPACITY_REACHED', 'IRC 在线连接已满，请稍后重试');

    response.writeHead(200, {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      'Connection': 'keep-alive',
      'X-Accel-Buffering': 'no'
    });
    response.write(': Oraculus IRC stream\n\n');

    const clientId = id();
    const client = {
      id: clientId,
      userId: authenticated.user.id,
      accessToken,
      response,
      connectedAtUtc: now()
    };
    this.clients.set(clientId, client);
    this.send(client, 'session.ready', {
      requestId,
      account: this.publicAccount(authenticated.user),
      serverTimeUtc: now()
    });
    this.send(client, 'roster.snapshot', { users: this.roster(), serverTimeUtc: now() });
    for (const message of this.recentMessages.slice(-30)) this.send(client, 'chat.message', message);
    this.broadcastRoster();

    let closed = false;
    const cleanup = () => {
      if (closed) return;
      closed = true;
      this.disconnect(clientId);
    };
    request.once('close', cleanup);
    response.once('close', cleanup);
  }

  disconnect(clientId) {
    const client = this.clients.get(clientId);
    if (!client) return;
    this.clients.delete(clientId);
    if (!this.hasConnection(client.userId)) this.presence.delete(client.userId);
    this.broadcastRoster();
  }

  updatePresence(authenticated, payload) {
    if (!this.hasConnection(authenticated.user.id))
      throw new RequestError(409, 'IRC_STREAM_REQUIRED', '请先建立 IRC 在线连接');
    const state = upper(payload.state || 'MENU');
    if (!['MENU', 'IN_GAME', 'AWAY'].includes(state))
      throw new RequestError(400, 'IRC_INVALID_PRESENCE', '在线状态无效');
    const minecraftProfileId = text(payload.minecraftProfileId).replace(/-/g, '').toLowerCase();
    const minecraftProfileName = text(payload.minecraftProfileName).trim();
    if (!/^[0-9a-f]{32}$/.test(minecraftProfileId))
      throw new RequestError(400, 'IRC_INVALID_PROFILE', 'Minecraft UUID 无效');
    if (!/^[A-Za-z0-9_]{1,32}$/.test(minecraftProfileName))
      throw new RequestError(400, 'IRC_INVALID_PROFILE', 'Minecraft 名称无效');
    for (const [userId, current] of this.presence.entries()) {
      if (userId !== authenticated.user.id && current.minecraftProfileId === minecraftProfileId
        && this.hasConnection(userId))
        throw new RequestError(409, 'IRC_IDENTITY_CONFLICT', '该 Minecraft 身份已被另一个在线账号使用');
    }
    const previous = this.presence.get(authenticated.user.id);
    this.presence.set(authenticated.user.id, {
      minecraftProfileId,
      minecraftProfileName,
      state,
      verified: Boolean(previous && previous.minecraftProfileId === minecraftProfileId && previous.verified),
      updatedAtUtc: now()
    });
    this.broadcastRoster();
    return { Ok: true, Message: 'IRC 在线状态已更新', User: this.rosterUser(authenticated.user.id) };
  }

  postMessage(authenticated, payload) {
    if (!this.hasConnection(authenticated.user.id))
      throw new RequestError(409, 'IRC_STREAM_REQUIRED', '请先建立 IRC 在线连接');
    const content = text(payload.content).trim();
    const length = [...content].length;
    if (!length || length > MAX_IRC_MESSAGE_CODEPOINTS)
      throw new RequestError(400, 'IRC_INVALID_MESSAGE', `消息长度必须为 1-${MAX_IRC_MESSAGE_CODEPOINTS} 个字符`);
    const timestamp = now();
    const cutoff = timestamp - IRC_MESSAGE_WINDOW_SECONDS;
    const recent = (this.messageTimes.get(authenticated.user.id) || []).filter(value => value >= cutoff);
    if (recent.length >= IRC_MESSAGE_LIMIT)
      throw new RequestError(429, 'IRC_RATE_LIMITED', '消息发送过快，请稍后重试');
    recent.push(timestamp);
    this.messageTimes.set(authenticated.user.id, recent);
    const message = {
      id: id(),
      sender: this.publicAccount(authenticated.user),
      content,
      sentAtUtc: timestamp
    };
    this.recentMessages.push(message);
    if (this.recentMessages.length > 200) this.recentMessages.splice(0, this.recentMessages.length - 200);
    this.broadcast('chat.message', message);
    return { Ok: true, Message: '消息已发送', MessageId: message.id, SentAtUtc: timestamp };
  }

  createIdentityChallenge(authenticated, payload) {
    if (!this.hasConnection(authenticated.user.id))
      throw new RequestError(409, 'IRC_STREAM_REQUIRED', '请先建立 IRC 在线连接');
    const minecraftProfileId = text(payload.minecraftProfileId).replace(/-/g, '').toLowerCase();
    const minecraftProfileName = text(payload.minecraftProfileName).trim();
    if (!/^[0-9a-f]{32}$/.test(minecraftProfileId)
      || !/^[A-Za-z0-9_]{1,32}$/.test(minecraftProfileName))
      throw new RequestError(400, 'IRC_INVALID_PROFILE', 'Minecraft 身份无效');
    const serverId = crypto.randomBytes(20).toString('hex');
    this.identityChallenges.set(authenticated.user.id, {
      serverId,
      minecraftProfileId,
      minecraftProfileName,
      expiresAtUtc: now() + 90
    });
    return { Ok: true, ServerId: serverId, ExpiresAtUtc: now() + 90 };
  }

  async verifyIdentity(authenticated) {
    const challenge = this.identityChallenges.get(authenticated.user.id);
    this.identityChallenges.delete(authenticated.user.id);
    if (!challenge || challenge.expiresAtUtc <= now())
      throw new RequestError(400, 'IRC_IDENTITY_CHALLENGE_EXPIRED', 'Minecraft 身份验证挑战已过期');
    const profile = await minecraftHasJoined(challenge.minecraftProfileName, challenge.serverId);
    const verifiedId = profile && text(profile.id).replace(/-/g, '').toLowerCase();
    if (!profile || verifiedId !== challenge.minecraftProfileId)
      throw new RequestError(403, 'IRC_IDENTITY_VERIFICATION_FAILED', '无法验证 Minecraft 会话身份');
    const existing = this.presence.get(authenticated.user.id);
    this.presence.set(authenticated.user.id, {
      minecraftProfileId: challenge.minecraftProfileId,
      minecraftProfileName: text(profile.name) || challenge.minecraftProfileName,
      state: existing ? existing.state : 'IN_GAME',
      verified: true,
      updatedAtUtc: now()
    });
    this.broadcastRoster();
    return { Ok: true, Message: 'Minecraft 身份验证成功', User: this.rosterUser(authenticated.user.id) };
  }

  roster() {
    const userIds = [...new Set([...this.clients.values()].map(client => client.userId))];
    return userIds.map(userId => this.rosterUser(userId)).filter(Boolean)
      .sort((left, right) => left.username.localeCompare(right.username, 'en', { sensitivity: 'base' }));
  }

  rosterUser(userId) {
    const user = findUserById(this.auth.store.data, userId);
    if (!user || !this.hasConnection(userId)) return null;
    const current = this.presence.get(userId);
    const connectedAt = [...this.clients.values()]
      .filter(client => client.userId === userId).map(client => client.connectedAtUtc);
    return Object.assign(this.publicAccount(user), {
      minecraftProfileId: current ? current.minecraftProfileId : '',
      minecraftProfileName: current ? current.minecraftProfileName : '',
      presence: current ? current.state : 'MENU',
      profileVerified: Boolean(current && current.verified),
      connectedAtUtc: Math.min(...connectedAt)
    });
  }

  publicAccount(user) {
    return {
      username: user.username,
      tier: user.tier === 'BETA' && user.betaExpiresAtUtc && user.betaExpiresAtUtc > now() ? 'BETA' : 'FREE',
      role: user.role
    };
  }

  hasConnection(userId) {
    return [...this.clients.values()].some(client => client.userId === userId);
  }

  maintain() {
    const removedUsers = new Set();
    for (const [clientId, client] of this.clients.entries()) {
      if (!this.authorize(client.accessToken)) {
        removedUsers.add(client.userId);
        if (client.response && !client.response.writableEnded) {
          this.send(client, 'session.expired', { serverTimeUtc: now() });
          client.response.end();
        }
        this.clients.delete(clientId);
        continue;
      }
      this.send(client, 'heartbeat', { serverTimeUtc: now() });
    }
    for (const userId of removedUsers) if (!this.hasConnection(userId)) this.presence.delete(userId);
    if (removedUsers.size) this.broadcastRoster();
  }

  broadcastRoster() {
    this.broadcast('roster.snapshot', { users: this.roster(), serverTimeUtc: now() });
  }

  broadcast(type, data) {
    for (const client of this.clients.values()) this.send(client, type, data);
  }

  send(client, type, data) {
    if (!client.response || client.response.writableEnded || client.response.destroyed) return;
    const payload = JSON.stringify({ type, data });
    try {
      client.response.write(`id: ${++this.sequence}\nevent: oraculus\ndata: ${payload}\n\n`);
    } catch {
      this.disconnect(client.id);
    }
  }
}

function secret(directory, name) {
  const file = path.join(directory, `${name}.bin`);
  if (fs.existsSync(file)) {
    const value = fs.readFileSync(file);
    if (value.length !== 32) throw new Error(`invalid secret file: ${file}`);
    return value;
  }
  const value = crypto.randomBytes(32);
  fs.writeFileSync(file, value, { mode: 0o600 });
  return value;
}
function entitlementSigner(directory) {
  const privatePath = path.join(directory, 'entitlement-ed25519-private.pem');
  const publicPath = path.join(directory, 'entitlement-ed25519-public.der');
  let privateKey;
  if (fs.existsSync(privatePath)) {
    privateKey = crypto.createPrivateKey(fs.readFileSync(privatePath));
  } else {
    const generated = crypto.generateKeyPairSync('ed25519', {
      privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
      publicKeyEncoding: { type: 'spki', format: 'der' }
    });
    const temporary = `${privatePath}.new`;
    fs.writeFileSync(temporary, generated.privateKey, { encoding: 'utf8', mode: 0o600 });
    fs.renameSync(temporary, privatePath);
    privateKey = crypto.createPrivateKey(generated.privateKey);
    if (!fs.existsSync(publicPath)) fs.writeFileSync(publicPath, generated.publicKey, { mode: 0o644 });
  }
  fs.chmodSync(privatePath, 0o600);
  const publicKey = crypto.createPublicKey(privateKey);
  const publicDer = publicKey.export({ type: 'spki', format: 'der' });
  if (fs.existsSync(publicPath)) {
    const existing = fs.readFileSync(publicPath);
    if (!existing.equals(publicDer)) throw new Error(`entitlement public key does not match ${privatePath}`);
  } else {
    fs.writeFileSync(publicPath, publicDer, { mode: 0o644 });
  }
  return {
    privateKey,
    publicKey,
    keyId: crypto.createHash('sha256').update(publicDer).digest('base64url').slice(0, 16),
    publicDer
  };
}
function text(value) { return value == null ? '' : String(value); }
function normalizeUsername(value) { return text(value).trim().toUpperCase(); }
function validUsername(value) { return /^[A-Za-z0-9_]{3,24}$/.test(text(value)); }
function validFingerprint(value, version) { const field = text(value); return field.length >= 20 && field.length <= 256 && text(version) === 'v1'; }
function hwidQualityOf(value) { const quality = upper(value); return ['STRONG', 'DEGRADED', 'FALLBACK'].includes(quality) ? quality : 'DEGRADED'; }
function validatePassword(password, username) {
  password = text(password);
  if (password.length < 12 || password.length > 128) return '密码长度须为 12-128 个字符';
  if (password.toLowerCase() === text(username).toLowerCase()) return '密码不能与用户名相同';
  if (COMMON_PASSWORDS.has(password.toLowerCase())) return '该密码过于常见，请使用不同密码';
  return null;
}
function findUserByName(data, username) { return data.users.find(user => user.normalizedUsername === normalizeUsername(username)); }
function findUserById(data, userId) { return data.users.find(user => user.id === userId); }
function isAdmin(user) { return user && ['SUPPORT_ADMIN', 'SUPER_ADMIN'].includes(user.role); }
function newUser(username, passwordHash, overrides) {
  return Object.assign({
    id: id(), username, normalizedUsername: normalizeUsername(username), passwordHash, role: 'USER', tier: 'FREE',
    betaExpiresAtUtc: null, status: 'ACTIVE', banReason: null, hwidHash: null, hwidVersion: null, hwidQuality: null,
    passwordChangedAtUtc: null, hwidChangedAtUtc: null, createdAtUtc: now(), createdBy: null,
    creationSource: 'UNKNOWN', registrationIpHash: null, forcePasswordChange: false
  }, overrides || {});
}
function accountView(user, overrides) {
  const effective = overrides || {};
  const tier = effective.tier || user.tier;
  const betaExpiresAt = Object.prototype.hasOwnProperty.call(effective, 'betaExpiresAt') ? effective.betaExpiresAt : user.betaExpiresAtUtc;
  return { id: user.id, username: user.username, role: user.role, tier, status: user.status,
    betaExpiresAt, betaActive: tier === 'BETA' && Boolean(betaExpiresAt && betaExpiresAt > now()),
    betaPermanent: tier === 'BETA' && betaExpiresAt === PERMANENT_BETA_EXPIRES_AT_UTC,
    betaPublicAccess: Boolean(effective.betaPublicAccess), hwidBound: Boolean(user.hwidHash), hwidQuality: user.hwidQuality || '',
    passwordChangedAt: user.passwordChangedAtUtc, hwidChangedAt: user.hwidChangedAtUtc,
    forcePasswordChange: Boolean(user.forcePasswordChange) };
}
function redemptionView(redemption, batch, username) {
  return {
    id: redemption.id,
    username: username || undefined,
    batchId: redemption.batchId,
    batchLabel: batch ? batch.label : '',
    product: redemption.product || (batch && batch.product) || 'BETA_DURATION',
    durationSeconds: redemption.durationSeconds == null ? null : redemption.durationSeconds,
    previousBetaExpiresAt: redemption.previousBetaExpiresAtUtc,
    newBetaExpiresAt: redemption.newBetaExpiresAtUtc,
    redeemedAt: redemption.redeemedAtUtc,
    codeSuffix: text(redemption.codeSuffix).slice(-4)
  };
}
function successSession(account, access, refresh, accessExpiresAt, refreshExpiresAt, entitlementProof) {
  return { Ok: true, Message: '登录成功', AccessToken: access, RefreshToken: refresh,
    AccessExpiresAt: accessExpiresAt, RefreshExpiresAt: refreshExpiresAt, Account: account,
    EntitlementProof: entitlementProof || '' };
}
function verifiedEntitlementClaims(proof, publicKey) {
  const parts = text(proof).split('.');
  if (parts.length !== 2 || !parts[0] || !parts[1]) throw new Error('entitlement proof format is invalid');
  const signature = Buffer.from(parts[1], 'base64url');
  if (!crypto.verify(null, Buffer.from(parts[0], 'ascii'), publicKey, signature))
    throw new Error('entitlement proof signature is invalid');
  return JSON.parse(Buffer.from(parts[0], 'base64url').toString('utf8'));
}
function revokeAll(data, userId, reason) {
  for (const session of data.sessions) if (session.userId === userId && !session.revokedAtUtc) { session.revokedAtUtc = now(); session.revokeReason = reason; }
  data.webSessions = data.webSessions.filter(session => session.userId !== userId);
}

const STYLE = `*{box-sizing:border-box}body{margin:0;background:#0f1115;color:#edf0f4;font:14px 'Segoe UI','Microsoft YaHei',sans-serif}a{color:#8fc7ff}.shell{max-width:1120px;margin:0 auto;padding:32px 20px}.top{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px}.brand{font-size:23px;font-weight:650}.muted{color:#9aa3af}.panel{background:#171a20;border:1px solid #2a3039;border-radius:7px;padding:22px;margin-bottom:18px}h1,h2{margin:0 0 16px;font-weight:600}label{display:block;color:#bac2cd;margin:13px 0 6px}input,select{width:100%;background:#0e1014;border:1px solid #343b46;color:#fff;border-radius:5px;padding:10px 11px}input[type=checkbox]{width:auto;margin-right:7px}button{border:0;border-radius:5px;padding:10px 15px;background:#2f81f7;color:#fff;cursor:pointer}button.danger{background:#c44747}button.secondary{background:#3a414c}.row{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.message{border-left:3px solid #f0b84b;background:#282318;padding:11px 13px;margin-bottom:16px}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:10px 8px;border-bottom:1px solid #2a3039;vertical-align:top}th{color:#aeb7c3;font-weight:500}.actions form{display:inline-block;margin:2px}.badge{display:inline-block;padding:3px 7px;border-radius:4px;background:#2a3039}.ok{color:#79d28a}.bad{color:#ff8181}.compact input,.compact select{width:auto;min-width:105px;padding:7px}.compact button{padding:7px 10px}.search-bar{display:flex;gap:10px;align-items:end}.search-bar>div{flex:1}.search-bar label{margin-top:0}.pager{display:flex;align-items:center;justify-content:space-between;margin-top:16px}.pager a{display:inline-block;padding:7px 11px;border:1px solid #343b46;border-radius:5px;text-decoration:none}.audit-fail{color:#ff8181}.audit-ok{color:#79d28a}@media(max-width:760px){.row{grid-template-columns:1fr}.panel{padding:16px}.table-wrap{overflow:auto}.search-bar{align-items:stretch;flex-direction:column}}`;
function layout(title, content) { return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${escapeHtml(title)} · Oraculus</title><style>${STYLE}</style></head><body><main class="shell">${content}</main></body></html>`; }
function message(value) { return value ? `<div class="message">${escapeHtml(value)}</div>` : ''; }
function loginPage(admin, note) {
  const title = admin ? '管理员登录' : '用户登录'; const action = admin ? '/admin/login' : '/user/login';
  return layout(title, `<div class="panel" style="max-width:440px;margin:8vh auto"><h1>${title}</h1><p class="muted">Oraculus Authentication Service</p>${message(note)}<form method="post" action="${action}"><label>用户名</label><input name="username" autocomplete="username" required maxlength="24"><label>密码</label><input name="password" type="password" autocomplete="current-password" required maxlength="128"><div style="margin-top:18px"><button type="submit">登录</button></div></form></div>`);
}
function userPage(user, csrf, note, betaPublicAccess) {
  const beta = user.tier === 'BETA' && user.betaExpiresAtUtc ? `Beta，到期：${formatTime(user.betaExpiresAtUtc)}` : 'Free';
  const passwordNext = user.forcePasswordChange ? now()
    : (user.passwordChangedAtUtc ? user.passwordChangedAtUtc + USER_CHANGE_COOLDOWN : now());
  const hwidNext = user.hwidChangedAtUtc ? user.hwidChangedAtUtc + USER_CHANGE_COOLDOWN : now();
  const forced = user.forcePasswordChange ? '<div class="message">当前使用临时密码，请立即设置新密码。强制改密不受 168 小时冷却限制。</div>' : '';
  const promotion = betaPublicAccess && betaPublicAccess.enabled
    ? `<div class="panel"><h2>限时 Beta 公益</h2><p>当前已开放至：${escapeHtml(formatTime(betaPublicAccess.expiresAtUtc))}</p><p class="muted">可使用 Beta 客户端登录；账号原始等级不会被永久修改。</p></div>` : '';
  return layout('账号管理', `<div class="top"><div><div class="brand">Oraculus Account</div><div class="muted">${escapeHtml(user.username)}</div></div><form method="post" action="/user/logout"><input type="hidden" name="csrf" value="${escapeHtml(csrf)}"><button class="secondary">退出登录</button></form></div>${message(note)}${forced}${promotion}<div class="row"><div class="panel"><h2>账号</h2><p>等级：<span class="badge">${escapeHtml(beta)}</span></p><p>状态：<span class="ok">${escapeHtml(user.status)}</span></p><p>HWID：${user.hwidHash ? `已绑定 · ${escapeHtml(user.hwidQuality || '未知')}` : '<span class="bad">未绑定</span>'}</p></div><div class="panel"><h2>修改冷却</h2><p>密码下次可改：${escapeHtml(formatTime(passwordNext))}</p><p>HWID 下次可改：${escapeHtml(formatTime(hwidNext))}</p></div></div><div class="row"><div class="panel"><h2>修改密码</h2><form method="post" action="/user/password"><input type="hidden" name="csrf" value="${escapeHtml(csrf)}"><label>当前密码</label><input type="password" name="currentPassword" required><label>新密码（12-128 位）</label><input type="password" name="newPassword" required minlength="12"><div style="margin-top:16px"><button>修改并注销所有会话</button></div></form></div><div class="panel"><h2>重置 HWID</h2><p class="muted">重置后当前会话失效，下次客户端登录将绑定新设备。</p><form method="post" action="/user/hwid/reset"><input type="hidden" name="csrf" value="${escapeHtml(csrf)}"><label>当前密码</label><input type="password" name="currentPassword" required><div style="margin-top:16px"><button class="danger">重置 HWID</button></div></form></div></div>`);
}
function actionForm(csrf, userId, action, label, danger) { return `<form method="post" action="/admin/action"><input type="hidden" name="csrf" value="${escapeHtml(csrf)}"><input type="hidden" name="userId" value="${escapeHtml(userId)}"><input type="hidden" name="action" value="${escapeHtml(action)}"><button${danger ? ' class="danger"' : ' class="secondary"'}>${escapeHtml(label)}</button></form>`; }
function adminListUrl(query, page) {
  const parameters = new URLSearchParams();
  if (query) parameters.set('q', query);
  if (page > 1) parameters.set('page', String(page));
  const encoded = parameters.toString();
  return encoded ? `/admin?${encoded}` : '/admin';
}
function betaPublicAccessPanel(session, betaPublicAccess) {
  const superAdmin = session.user.role === 'SUPER_ADMIN';
  const status = betaPublicAccess.enabled
    ? `<span class="ok">已开放至 ${escapeHtml(formatTime(betaPublicAccess.expiresAtUtc))}</span>`
    : '<span class="muted">当前未开放</span>';
  if (!superAdmin) {
    return `<div class="panel"><h2>限时 Beta 公益</h2><p>${status}</p><p class="muted">仅超级管理员可以修改该全局开关。</p></div>`;
  }
  return `<div class="panel"><h2>限时 Beta 公益</h2><p>${status}</p><form class="row" method="post" action="/admin/beta-public"><input type="hidden" name="csrf" value="${escapeHtml(session.csrfToken)}"><div><label><input type="checkbox" name="enabled" value="true"${betaPublicAccess.enabled ? ' checked' : ''}> 开放所有 Free 用户使用 Beta 客户端</label><p class="muted">仅临时授权 Beta 会话；账号原始等级保持 Free。</p></div><div><label>结束时间（UTC）</label><input name="expiryText" value="${betaPublicAccess.enabled ? escapeHtml(new Date(betaPublicAccess.expiresAtUtc * 1000).toISOString().replace('.000Z', 'Z')) : ''}" placeholder="2026-12-31T23:59:59Z"><p class="muted">启用时必须填写未来时间；取消勾选即立即关闭。</p></div><div><button>保存公益设置</button></div></form></div>`;
}
function adminPage(session, listing, note, betaPublicAccess) {
  const superAdmin = session.user.role === 'SUPER_ADMIN';
  const rows = listing.users.map(user => {
    const manageable = superAdmin || user.role === 'USER';
    const controls = manageable
      ? `${actionForm(session.csrfToken, user.id, user.status === 'BANNED' ? 'unban' : 'ban', user.status === 'BANNED' ? '解封' : '封禁', false)}
        ${actionForm(session.csrfToken, user.id, 'reset-hwid', '重置 HWID', false)}
        ${actionForm(session.csrfToken, user.id, 'delete', '删除', true)}
        <details><summary>修改</summary>
          <form method="post" action="/admin/action"><input type="hidden" name="csrf" value="${escapeHtml(session.csrfToken)}"><input type="hidden" name="userId" value="${escapeHtml(user.id)}"><input type="hidden" name="action" value="password"><input name="password" type="password" minlength="12" placeholder="临时密码" required><button>改密</button></form>
          <form method="post" action="/admin/action"><input type="hidden" name="csrf" value="${escapeHtml(session.csrfToken)}"><input type="hidden" name="userId" value="${escapeHtml(user.id)}"><input type="hidden" name="action" value="set-hwid"><input name="fingerprint" placeholder="客户端设备指纹" required><button>设 HWID</button></form>
          <form method="post" action="/admin/action"><input type="hidden" name="csrf" value="${escapeHtml(session.csrfToken)}"><input type="hidden" name="userId" value="${escapeHtml(user.id)}"><input type="hidden" name="action" value="tier"><select name="tier"><option>FREE</option><option>BETA</option></select><input name="expiryText" placeholder="Beta 到期 UTC，例如 2026-12-31"><button>改等级</button></form>
        </details>`
      : '<span class="muted">仅超级管理员可管理</span>';
    return `<tr><td><strong>${escapeHtml(user.username)}</strong><br><span class="muted">${escapeHtml(user.id.slice(0, 8))}</span></td><td>${escapeHtml(user.role)}</td><td>${escapeHtml(user.tier)}<br><span class="muted">${escapeHtml(formatTime(user.betaExpiresAtUtc))}</span></td><td>${escapeHtml(user.status)}</td><td>${user.hwidHash ? `已绑定 · ${escapeHtml(user.hwidQuality || '')}` : '未绑定'}</td><td class="actions compact">${controls}</td></tr>`;
  }).join('') || '<tr><td colspan="6" class="muted">没有匹配的账号</td></tr>';
  const roleField = superAdmin
    ? '<div><label>角色</label><select name="role"><option value="USER">普通用户</option><option value="SUPPORT_ADMIN">支持管理员</option></select></div>'
    : '<input type="hidden" name="role" value="USER"><div><label>角色</label><input value="普通用户" disabled></div>';
  const create = `<div class="panel"><h2>创建账号</h2><form class="row" method="post" action="/admin/create"><input type="hidden" name="csrf" value="${escapeHtml(session.csrfToken)}"><div><label>用户名</label><input name="username" required maxlength="24"></div><div><label>临时密码</label><input type="password" name="password" required minlength="12"></div>${roleField}<div><label>等级</label><select name="tier"><option>FREE</option><option>BETA</option></select></div><div><label>Beta 到期（UTC，仅 Beta）</label><input name="expiryText" placeholder="2026-12-31"></div><div><button>创建账号</button></div></form></div>`;
  const previous = listing.page > 1 ? `<a href="${escapeHtml(adminListUrl(listing.query, listing.page - 1))}">上一页</a>` : '<span></span>';
  const next = listing.page < listing.pageCount ? `<a href="${escapeHtml(adminListUrl(listing.query, listing.page + 1))}">下一页</a>` : '<span></span>';
  const summary = listing.query
    ? `搜索到 ${listing.matched} 个账号，共 ${listing.total} 个`
    : `共 ${listing.total} 个账号`;
  return layout('管理面板', `<div class="top"><div><div class="brand">Oraculus Admin</div><div class="muted">${escapeHtml(session.user.username)} · ${escapeHtml(session.user.role)}</div></div><div><a href="/admin/audit">审计日志</a>　<form style="display:inline-block" method="post" action="/admin/logout"><input type="hidden" name="csrf" value="${escapeHtml(session.csrfToken)}"><button class="secondary">退出登录</button></form></div></div>${message(note)}${betaPublicAccessPanel(session, betaPublicAccess)}${create}<div class="panel"><form class="search-bar" method="get" action="/admin"><div><label>搜索账号</label><input name="q" value="${escapeHtml(listing.query)}" maxlength="64" placeholder="用户名、ID、角色、等级或状态"></div><button>搜索</button>${listing.query ? '<a href="/admin">清除</a>' : ''}</form></div><div class="panel"><h2>账号列表</h2><p class="muted">${escapeHtml(summary)}；每页 ${ADMIN_PAGE_SIZE} 条，不再限制为前 500 条</p><div class="table-wrap"><table><thead><tr><th>账号</th><th>角色</th><th>等级</th><th>状态</th><th>HWID</th><th>操作</th></tr></thead><tbody>${rows}</tbody></table></div><div class="pager">${previous}<span>第 ${listing.page} / ${listing.pageCount} 页</span>${next}</div></div>`);
}
function auditPage(session, logs) {
  const rows = logs.map(entry => `<tr><td>${escapeHtml(formatTime(entry.occurredAtUtc))}</td><td>${escapeHtml(entry.actor)}</td><td>${escapeHtml(entry.target)}</td><td>${escapeHtml(entry.action)}</td><td class="${entry.success ? 'audit-ok' : 'audit-fail'}">${entry.success ? '成功' : '失败'}</td><td>${escapeHtml(entry.details || '-')}</td><td class="muted">${escapeHtml(entry.requestId ? entry.requestId.slice(0, 12) : '-')}</td></tr>`).join('')
    || '<tr><td colspan="7" class="muted">暂无审计记录</td></tr>';
  return layout('审计日志', `<div class="top"><div><div class="brand">简明审计日志</div><div class="muted">最近 ${logs.length} 条；不会显示密码、令牌、HWID 或原始 IP</div></div><a href="/admin">返回管理面板</a></div><div class="panel"><div class="table-wrap"><table><thead><tr><th>时间</th><th>操作者</th><th>目标</th><th>动作</th><th>结果</th><th>简要信息</th><th>请求</th></tr></thead><tbody>${rows}</tbody></table></div></div>`);
}

function betaRedemptionRows(redemptions, includeUser) {
  return redemptions.map(item => `<tr>
    ${includeUser ? `<td>${escapeHtml(item.username || '-')}</td>` : ''}
    <td>${escapeHtml(formatTime(item.redeemedAt))}</td>
    <td>${item.product === 'BETA_PERMANENT' ? '永久 Beta' : `${Math.floor((item.durationSeconds || 0) / 86400)} 天`}</td>
    <td>${escapeHtml(item.codeSuffix ? `****${item.codeSuffix}` : '-')}</td>
    <td>${escapeHtml(formatTime(item.newBetaExpiresAt))}</td>
  </tr>`).join('') || `<tr><td colspan="${includeUser ? 5 : 4}" class="muted">暂无兑换记录</td></tr>`;
}

function userPageWithRedemption(user, csrf, note, betaPublicAccess, redemptions) {
  const base = userPage(user, csrf, note, betaPublicAccess);
  const active = user.tier === 'BETA' && user.betaExpiresAtUtc > now();
  const expiry = user.betaExpiresAtUtc === PERMANENT_BETA_EXPIRES_AT_UTC ? '永久（2099-12-31）' : formatTime(user.betaExpiresAtUtc);
  const panel = `<div class="panel"><h2>兑换 Beta 卡密</h2>
    <p>当前授权：<span class="badge">${active ? `Beta，${escapeHtml(expiry)}` : '未生效'}</span></p>
    <form method="post" action="/user/beta/redeem"><input type="hidden" name="csrf" value="${escapeHtml(csrf)}">
      <label>卡密</label><input name="code" autocomplete="off" maxlength="80" placeholder="ORA-BETA-..." required>
      <div style="margin-top:16px"><button>兑换并升级账号</button></div>
    </form><p class="muted">兑换成功后，请重新登录 Beta 客户端以取得新的授权证明。</p>
    <div class="table-wrap"><table><thead><tr><th>兑换时间</th><th>产品</th><th>卡密</th><th>到期时间</th></tr></thead>
      <tbody>${betaRedemptionRows(redemptions || [], false)}</tbody></table></div></div>`;
  return base.replace('</main>', `${panel}</main>`);
}

function adminPageWithBetaLink(session, listing, note, betaPublicAccess) {
  return adminPage(session, listing, note, betaPublicAccess)
    .replace('<a href="/admin/audit">', '<a href="/admin/beta-codes">Beta 卡密</a>　<a href="/admin/audit">');
}

function betaCodeBillingPanel(billing) {
  const rows = billing.Ok ? billing.Categories.map(category => `<tr>
    <td>${escapeHtml(category.label)}</td><td>${category.quantity}</td>
    <td>${category.unitValue == null ? '-' : `¥${category.unitValue} / 张`}</td>
    <td>${category.unitValue == null ? '-' : `¥${category.subtotal}`}</td>
  </tr>`).join('') : '<tr><td colspan="4" class="muted">日期范围有效后显示统计</td></tr>';
  const summary = billing.Ok
    ? `<p><strong>总价值：¥${billing.TotalValue}</strong>　批次：${billing.BatchCount}　生成卡密：${billing.TotalQuantity} 张　已计价：${billing.PricedQuantity} 张　未定价：${billing.UnpricedQuantity} 张</p>`
    : `<p class="bad">${escapeHtml(billing.Message)}</p>`;
  const warning = billing.Ok && billing.UnpricedQuantity > 0
    ? '<p class="bad">存在未定价时长，未计入总价值，请核对上表。</p>' : '';
  return `<div class="panel"><h2>卡密计费</h2>
    <form class="row" method="get" action="/admin/beta-codes">
      <div><label>开始日期（UTC，包含）</label><input type="date" name="billingFrom" value="${escapeHtml(billing.FromDate)}" required></div>
      <div><label>结束日期（UTC，包含）</label><input type="date" name="billingTo" value="${escapeHtml(billing.ToDate)}" required></div>
      <div><button>计算区间总价值</button></div>
    </form>${summary}${warning}
    <div class="table-wrap"><table><thead><tr><th>分类</th><th>生成数量</th><th>单价</th><th>小计</th></tr></thead><tbody>${rows}</tbody></table></div>
    <p class="muted">定价：1 天 ¥3，2 天 ¥5，7 天 ¥10，30 天 ¥25，365 天 ¥40，永久或超过 400 天 ¥50。其他时长单列为未定价，不计入总价值；统计按批次生成时间计算，包含已兑换、已禁用卡密。</p>
  </div>`;
}

function betaCodesPage(session, batches, redemptions, billing, note) {
  const superAdmin = session.user.role === 'SUPER_ADMIN';
  const create = superAdmin ? `<div class="panel"><h2>生成 Beta 卡密批次</h2>
    <form class="row" method="post" action="/admin/beta-codes/batches/create">
      <input type="hidden" name="csrf" value="${escapeHtml(session.csrfToken)}">
      <div><label>批次名称</label><input name="label" maxlength="80" required></div>
      <div><label>卡密类型</label><select name="product"><option value="BETA_DURATION">时长卡</option><option value="BETA_PERMANENT">永久卡（2099-12-31）</option></select></div>
      <div><label>时长（天，仅时长卡）</label><input name="durationDays" type="number" min="1" max="3650" value="30"></div>
      <div><label>数量</label><input name="quantity" type="number" min="1" max="100" value="1" required></div>
      <div><label>卡密失效时间（UTC，可留空）</label><input name="codeExpiresAt" placeholder="2026-12-31T23:59:59Z"></div>
      <div><label>备注</label><input name="note" maxlength="200"></div>
      <div><button>生成并下载明文卡密</button></div>
    </form><p class="muted">明文只在本次下载中出现，服务器只保存带 pepper 的 HMAC 查询值。</p></div>`
    : '<div class="panel"><p class="muted">只有超级管理员可以生成或禁用卡密批次。</p></div>';
  const lookup = `<div class="panel"><h2>查询卡密</h2><form class="search-bar" method="post" action="/admin/beta-codes/lookup"><input type="hidden" name="csrf" value="${escapeHtml(session.csrfToken)}"><div><label>完整卡密</label><input name="code" autocomplete="off" maxlength="80" placeholder="ORA-BETA-..." required></div><button>查询状态</button></form></div>`;
  const reviewLabel = batch => batch.issueSource === 'CLIENT_API'
    ? `${batch.reviewStatus === 'REVIEWED' ? '已审' : '待审'} · ${escapeHtml(batch.requestedByUsernameSnapshot || batch.requestedByUserId || '-')}`
    : '后台生成';
  const batchRows = batches.map(batch => `<tr><td><strong>${escapeHtml(batch.label)}</strong><br><span class="muted">${escapeHtml(batch.id.slice(0, 8))}</span></td>
    <td>${batch.product === 'BETA_PERMANENT' ? '永久卡' : `${Math.floor((batch.durationSeconds || 0) / 86400)} 天`}</td>
    <td>${batch.quantity}</td><td>${batch.unused} / ${batch.redeemed} / ${batch.disabled}</td>
    <td>${escapeHtml(batch.status)}<br><span class="muted">${reviewLabel(batch)}<br>${escapeHtml(formatTime(batch.codeExpiresAtUtc))}</span></td>
    <td>${superAdmin && batch.issueSource === 'CLIENT_API' && batch.reviewStatus !== 'REVIEWED' ? `<form method="post" action="/admin/beta-codes/batches/review"><input type="hidden" name="csrf" value="${escapeHtml(session.csrfToken)}"><input type="hidden" name="batchId" value="${escapeHtml(batch.id)}"><input name="reviewNote" maxlength="200" placeholder="审查备注"><button>标记已审</button></form>` : ''}
      ${superAdmin && batch.status === 'ACTIVE' ? `<form method="post" action="/admin/beta-codes/batches/disable"><input type="hidden" name="csrf" value="${escapeHtml(session.csrfToken)}"><input type="hidden" name="batchId" value="${escapeHtml(batch.id)}"><button class="danger">禁用</button></form>` : (!superAdmin ? '-' : '')}</td></tr>`).join('')
    || '<tr><td colspan="6" class="muted">暂无卡密批次</td></tr>';
  const reviewRows = batches.filter(batch => batch.issueSource === 'CLIENT_API').map(batch => `<tr>
    <td><strong>${escapeHtml(batch.requestedByUsernameSnapshot || '-')}</strong><br><span class="muted">${escapeHtml(batch.requestedByRoleSnapshot || '-')} · ${escapeHtml(batch.requestedByUserId || '-')}</span></td>
    <td>${escapeHtml(formatTime(batch.createdAtUtc))}<br><span class="muted">${escapeHtml(batch.requestId || '-')}</span></td>
    <td>${batch.product === 'BETA_PERMANENT' ? '永久卡' : `${Math.floor((batch.durationSeconds || 0) / 86400)} 天`} · ${batch.quantity} 张</td>
    <td>${batch.unused} / ${batch.redeemed} / ${batch.disabled}</td>
    <td>${escapeHtml(batch.reviewStatus || 'PENDING_REVIEW')}<br><span class="muted">${escapeHtml(batch.reviewNote || '')}<br>${escapeHtml(batch.issueClientEdition || '-')} · ${escapeHtml(batch.issueClientVersion || '-')} · ${escapeHtml(batch.issueBuildId || '-')} · ${escapeHtml(batch.issueLauncherVersion || '-')}</span></td>
  </tr>`).join('') || '<tr><td colspan="5" class="muted">暂无客户端发卡记录</td></tr>';
  return layout('Beta 卡密管理', `<div class="top"><div><div class="brand">Beta 卡密管理</div><div class="muted">${escapeHtml(session.user.username)} · ${escapeHtml(session.user.role)}</div></div><a href="/admin">返回管理面板</a></div>
    ${message(note)}<div class="panel"><h2>客户端发卡审查</h2><div class="table-wrap"><table><thead><tr><th>请求账号</th><th>时间 / requestId</th><th>规格</th><th>未用 / 已兑 / 禁用</th><th>审查状态</th></tr></thead><tbody>${reviewRows}</tbody></table></div></div>
    ${betaCodeBillingPanel(billing)}${create}${lookup}<div class="panel"><h2>批次</h2><div class="table-wrap"><table><thead><tr><th>批次</th><th>产品</th><th>总数</th><th>未用 / 已兑 / 禁用</th><th>状态 / 审查 / 失效</th><th>操作</th></tr></thead><tbody>${batchRows}</tbody></table></div></div>
    <div class="panel"><h2>最近兑换记录</h2><div class="table-wrap"><table><thead><tr><th>账号</th><th>兑换时间</th><th>产品</th><th>卡密</th><th>到期时间</th></tr></thead><tbody>${betaRedemptionRows(redemptions, true)}</tbody></table></div></div>`);
}

class RequestError extends Error { constructor(status, code, message) { super(message); this.status = status; this.code = code; } }
function body(request) {
  return new Promise((resolve, reject) => {
    let size = 0; const chunks = [];
    request.on('data', chunk => { size += chunk.length; if (size > MAX_BODY_BYTES) { reject(new RequestError(413, 'REQUEST_TOO_LARGE', '请求过大')); request.destroy(); } else chunks.push(chunk); });
    request.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    request.on('error', reject);
  });
}
function contentType(request, wanted) { return text(request.headers['content-type']).split(';', 1)[0].trim().toLowerCase() === wanted; }
function minecraftHasJoined(username, serverId) {
  return new Promise((resolve, reject) => {
    const url = new URL('https://sessionserver.mojang.com/session/minecraft/hasJoined');
    url.searchParams.set('username', username);
    url.searchParams.set('serverId', serverId);
    const request = https.get(url, {
      headers: { Accept: 'application/json', 'User-Agent': 'Oraculus-Auth/1.0' },
      timeout: 8000
    }, response => {
      const chunks = [];
      let size = 0;
      response.on('data', chunk => {
        size += chunk.length;
        if (size > MAX_BODY_BYTES) {
          request.destroy(new Error('Minecraft identity response exceeds size limit'));
          return;
        }
        chunks.push(chunk);
      });
      response.on('end', () => {
        if (response.statusCode === 204) return resolve(null);
        if (response.statusCode !== 200)
          return reject(new RequestError(502, 'IRC_IDENTITY_PROVIDER_ERROR', 'Minecraft 身份服务暂时不可用'));
        try {
          resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')));
        } catch {
          reject(new RequestError(502, 'IRC_IDENTITY_PROVIDER_ERROR', 'Minecraft 身份服务返回无效数据'));
        }
      });
    });
    request.on('timeout', () => request.destroy(new Error('Minecraft identity request timed out')));
    request.on('error', cause => {
      if (cause instanceof RequestError) reject(cause);
      else reject(new RequestError(502, 'IRC_IDENTITY_PROVIDER_ERROR', 'Minecraft 身份服务暂时不可用'));
    });
  });
}
function bearer(request) { const header = text(request.headers.authorization); return /^Bearer\s+/i.test(header) ? header.replace(/^Bearer\s+/i, '').trim() : ''; }
function cookies(request) { return Object.fromEntries(text(request.headers.cookie).split(';').map(value => value.trim().split(/=(.*)/s)).filter(pair => pair[0]).map(([key, value]) => [key, decodeURIComponent(value || '')])); }
function hostAllowed(config, request) { const host = text(request.headers.host).replace(/^\[([^\]]+)](?::\d+)?$/, '$1').replace(/:\d+$/, '').toLowerCase(); return config.AllowedHosts.includes(host); }
function securityHeaders(response, requestId, secure) {
  response.setHeader('X-Content-Type-Options', 'nosniff'); response.setHeader('X-Frame-Options', 'DENY');
  response.setHeader('Referrer-Policy', 'no-referrer'); response.setHeader('Content-Security-Policy', "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
  response.setHeader('Cache-Control', 'no-store'); response.setHeader('X-Request-Id', requestId);
  if (secure) response.setHeader('Strict-Transport-Security', 'max-age=31536000');
}
function writeJson(response, status, payload) { const output = JSON.stringify(payload); response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(output) }); response.end(output); }
function writeHtml(response, output) { response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Content-Length': Buffer.byteLength(output) }); response.end(output); }
function writeText(response, status, output) { response.writeHead(status, { 'Content-Type': 'text/plain; charset=utf-8', 'Content-Length': Buffer.byteLength(output) }); response.end(output); }
function writeAttachment(response, filename, output) {
  response.writeHead(200, {
    'Content-Type': 'text/plain; charset=utf-8',
    'Content-Disposition': `attachment; filename="${filename.replace(/[^A-Za-z0-9._-]/g, '_')}"`,
    'Cache-Control': 'no-store', 'Content-Length': Buffer.byteLength(output)
  });
  response.end(output);
}
function redirect(response, location) { response.writeHead(303, { Location: location }); response.end(); }
function redirectMessage(response, location, note) { redirect(response, `${location}?message=${encodeURIComponent(note || '')}`); }
function setCookie(response, config, name, value, expired) { response.setHeader('Set-Cookie', `${name}=${encodeURIComponent(value || '')}; Path=/; HttpOnly; SameSite=Strict${config.SecureCookies ? '; Secure' : ''}${expired ? '; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0' : ''}`); }
function parseExpiry(value) { if (!value || !String(value).trim()) return null; const parsed = Date.parse(String(value).trim()); return Number.isFinite(parsed) ? Math.floor(parsed / 1000) : null; }
function parseUtcDate(value) {
  const normalized = text(value).trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(normalized)) return null;
  const milliseconds = Date.parse(`${normalized}T00:00:00Z`);
  if (!Number.isFinite(milliseconds) || new Date(milliseconds).toISOString().slice(0, 10) !== normalized) return null;
  return Math.floor(milliseconds / 1000);
}
function betaCodeBillingRange(fromValue, toValue) {
  const today = new Date().toISOString().slice(0, 10);
  const defaultFrom = `${today.slice(0, 8)}01`;
  const fromDate = text(fromValue).trim() || defaultFrom;
  const toDate = text(toValue).trim() || today;
  const fromUtc = parseUtcDate(fromDate);
  const toUtc = parseUtcDate(toDate);
  if (fromUtc == null || toUtc == null)
    return { Ok: false, Message: '计费日期格式无效', FromDate: fromDate, ToDate: toDate };
  if (fromUtc > toUtc)
    return { Ok: false, Message: '计费开始日期不能晚于结束日期', FromDate: fromDate, ToDate: toDate };
  return { Ok: true, FromDate: fromDate, ToDate: toDate, FromUtc: fromUtc, ToExclusiveUtc: toUtc + 86400 };
}
function isLoopbackAddress(value) {
  const address = normalizeRemoteIp(value);
  return address === '127.0.0.1' || address === '::1';
}
function normalizeRemoteIp(value) {
  const address = text(value).trim().replace(/^::ffff:/i, '');
  return net.isIP(address) ? address : '';
}
function internalClientIp(request, fallback) {
  return normalizeRemoteIp(request.headers['x-oraculus-client-ip']) || fallback;
}
function internalWebsiteAuthorized(config, request, remoteIp) {
  const normalizedRemoteIp = normalizeRemoteIp(remoteIp);
  return Boolean(config.InternalWebsiteSecret)
    && (isLoopbackAddress(normalizedRemoteIp) || config.InternalWebAllowedIps.includes(normalizedRemoteIp))
    && safeEqual(request.headers['x-oraculus-website-secret'], config.InternalWebsiteSecret);
}
async function jsonPayload(request, method) {
  if (method === 'GET') return {};
  if (!contentType(request, 'application/json')) throw new RequestError(415, 'INVALID_CONTENT_TYPE', '请求必须使用 application/json');
  let payload;
  try { payload = JSON.parse(await body(request)); }
  catch (cause) { if (cause instanceof RequestError) throw cause; throw new RequestError(400, 'INVALID_JSON', 'JSON 格式无效'); }
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) throw new RequestError(400, 'INVALID_JSON', 'JSON 格式无效');
  return payload;
}

function startServer(configPath) {
  const config = AppConfig.load(configPath); const auth = new AuthService(config);
  const irc = new IrcHub(auth); let active = 0;
  irc.start();
  const handler = internalListener => async (request, response) => {
    const requestId = id(); let counted = false;
    try {
      const secure = Boolean(request.socket.encrypted); securityHeaders(response, requestId, secure);
      if (!internalListener && !hostAllowed(config, request)) return writeText(response, 404, 'Not Found');
      if (!internalListener && config.RequireHttps && !secure) return writeJson(response, 426, { ok: false, error: 'HTTPS_REQUIRED', message: '必须使用 HTTPS', requestId });
      const url = new URL(request.url, `${secure ? 'https' : 'http'}://${request.headers.host || 'localhost'}`);
      const route = url.pathname.replace(/\/$/, '') || '/'; const method = request.method.toUpperCase();
      const remoteIp = request.socket.remoteAddress || '';
      const isIrcStream = method === 'GET' && route === '/api/v1/irc/stream';
      if (!isIrcStream) {
        active += 1;
        counted = true;
        if (active > MAX_CONCURRENT_REQUESTS)
          return writeJson(response, 503, { ok: false, error: 'SERVER_BUSY', message: '服务器繁忙，请稍后重试', requestId });
      }
      if (internalListener) {
        if (route.startsWith('/internal/web/v1/'))
          return await handleInternalWebApi(auth, config, request, response, method, route, remoteIp, requestId);
        return writeText(response, 404, 'Not Found');
      }
      if (method === 'GET' && route === '/health/live') return writeJson(response, 200, { ok: true });
      if (method === 'GET' && route === '/health/ready') return writeJson(response, auth.ready() ? 200 : 503, { ok: auth.ready() });
      if (route.startsWith('/api/v1/')) return await handleApi(auth, irc, request, response, method, route, remoteIp, requestId);
      return await handleWeb(auth, config, request, response, method, route, url, remoteIp, requestId);
    } catch (cause) {
      if (response.writableEnded) return;
      if (cause instanceof RequestError) return writeJson(response, cause.status, { ok: false, error: cause.code, message: cause.message, requestId });
      console.error(`[${new Date().toISOString()}] ${requestId}`, cause && cause.stack || cause);
      return writeJson(response, 500, { ok: false, error: 'INTERNAL_ERROR', message: '服务器内部错误', requestId });
    } finally { if (counted) active -= 1; }
  };
  const servers = [];
  if (config.EnableHttp) { const server = http.createServer(handler(false)); server.listen(config.HttpPort, config.ListenHost); servers.push(server); }
  if (config.TlsCertificatePath || config.TlsPrivateKeyPath) {
    if (!config.TlsCertificatePath || !config.TlsPrivateKeyPath) throw new Error('TLS certificate and private key must be configured together');
    const server = https.createServer({ cert: fs.readFileSync(config.TlsCertificatePath), key: fs.readFileSync(config.TlsPrivateKeyPath), minVersion: 'TLSv1.2' }, handler(false));
    server.listen(config.HttpsPort, config.ListenHost); servers.push(server);
  }
  if (config.InternalWebsiteSecret && config.InternalWebPort) {
    const server = config.InternalWebTls
      ? https.createServer({ cert: fs.readFileSync(config.TlsCertificatePath), key: fs.readFileSync(config.TlsPrivateKeyPath), minVersion: 'TLSv1.2' }, handler(true))
      : http.createServer(handler(true));
    server.listen(config.InternalWebPort, config.InternalWebHost); servers.push(server);
  }
  if (!servers.length) throw new Error('Neither HTTP nor HTTPS listener is enabled');
  for (const server of servers) server.on('listening', () => console.log(`Oraculus Auth listening on ${config.ListenHost}:${server.address().port}`));
  const stop = () => { irc.close(); for (const server of servers) server.close(); };
  process.once('SIGINT', stop); process.once('SIGTERM', stop);
}

async function handleApi(auth, irc, request, response, method, route, remoteIp, requestId) {
  const getAllowed = ['/api/v1/auth/status', '/api/v1/irc/stream', '/api/v1/irc/roster'].includes(route);
  if (method !== 'POST' && !(method === 'GET' && getAllowed))
    throw new RequestError(405, 'METHOD_NOT_ALLOWED', '请求方法不受支持');
  if (method === 'GET' && route === '/api/v1/irc/stream')
    return irc.connect(request, response, bearer(request), requestId);
  if (method === 'GET' && route === '/api/v1/irc/roster') {
    if (!irc.authorize(bearer(request)))
      throw new RequestError(401, 'SESSION_REVOKED', '登录会话无效或已过期');
    return writeJson(response, 200, { Ok: true, Users: irc.roster(), requestId });
  }
  let payload = {};
  if (method === 'POST') {
    if (!contentType(request, 'application/json')) throw new RequestError(415, 'INVALID_CONTENT_TYPE', '请求必须使用 application/json');
    try { payload = JSON.parse(await body(request)); } catch (cause) { if (cause instanceof RequestError) throw cause; throw new RequestError(400, 'INVALID_JSON', 'JSON 格式无效'); }
    if (!payload || typeof payload !== 'object' || Array.isArray(payload)) throw new RequestError(400, 'INVALID_JSON', 'JSON 格式无效');
  }
  let result;
  if (route === '/api/v1/auth/register') result = auth.register(payload, remoteIp, requestId);
  else if (route === '/api/v1/auth/login') result = auth.login(payload, remoteIp, requestId);
  else if (route === '/api/v1/auth/launcher-login')
    result = auth.launcherLogin(payload, bearer(request), remoteIp, requestId);
  else if (route === '/api/v1/auth/refresh') result = auth.refresh(payload, remoteIp, requestId);
  else if (route === '/api/v1/auth/heartbeat' || route === '/api/v1/auth/status') result = auth.heartbeat(bearer(request), remoteIp, requestId);
  else if (route === '/api/v1/auth/legality') result = auth.legality(bearer(request));
  else if (route === '/api/v1/auth/logout') { auth.logout(bearer(request), remoteIp, requestId); result = { Ok: true, Message: '已退出登录' }; }
  else if (route === '/api/v1/beta-codes/issue') {
    result = auth.issueBetaCodesFromClient(
      bearer(request), payload, text(request.headers['idempotency-key']), remoteIp, requestId
    );
  }
  else if (route === '/api/v1/irc/presence') {
    const authenticated = irc.authorize(bearer(request));
    if (!authenticated) throw new RequestError(401, 'SESSION_REVOKED', '登录会话无效或已过期');
    result = irc.updatePresence(authenticated, payload);
  }
  else if (route === '/api/v1/irc/identity/challenge') {
    const authenticated = irc.authorize(bearer(request));
    if (!authenticated) throw new RequestError(401, 'SESSION_REVOKED', '登录会话无效或已过期');
    result = irc.createIdentityChallenge(authenticated, payload);
  }
  else if (route === '/api/v1/irc/identity/verify') {
    const authenticated = irc.authorize(bearer(request));
    if (!authenticated) throw new RequestError(401, 'SESSION_REVOKED', '登录会话无效或已过期');
    result = await irc.verifyIdentity(authenticated);
  }
  else if (route === '/api/v1/irc/message' || route === '/api/v1/irc/messages') {
    const authenticated = irc.authorize(bearer(request));
    if (!authenticated) throw new RequestError(401, 'SESSION_REVOKED', '登录会话无效或已过期');
    result = irc.postMessage(authenticated, payload);
  }
  else throw new RequestError(404, 'NOT_FOUND', '接口不存在');
  writeJson(response, result.Ok ? 200 : apiStatus(result.Error), Object.assign({ requestId }, result));
}

async function handleInternalWebApi(auth, config, request, response, method, route, remoteIp, requestId) {
  if (!internalWebsiteAuthorized(config, request, remoteIp))
    throw new RequestError(404, 'NOT_FOUND', '接口不存在');
  const clientIp = internalClientIp(request, remoteIp);
  const payload = await jsonPayload(request, method);
  const sessionToken = text(request.headers['x-oraculus-web-session']);
  const write = (status, value) => writeJson(response, status, Object.assign({ requestId }, value));
  if (route === '/internal/web/v1/session/login') {
    if (method !== 'POST') throw new RequestError(405, 'METHOD_NOT_ALLOWED', '请求方法不受支持');
    const login = auth.createWebSession(text(payload.username), text(payload.password), false, clientIp, requestId);
    return login.error ? write(401, { ok: false, error: 'INVALID_CREDENTIALS', message: login.error })
      : write(200, { ok: true, sessionToken: login.token });
  }
  if (route === '/internal/web/v1/session/register') {
    if (method !== 'POST') throw new RequestError(405, 'METHOD_NOT_ALLOWED', '请求方法不受支持');
    const registered = auth.registerWeb(text(payload.username), text(payload.password), clientIp, requestId);
    return registered.Ok ? write(201, { ok: true, sessionToken: registered.Token, account: registered.Account })
      : write(apiStatus(registered.Error), { ok: false, error: registered.Error, message: registered.Message });
  }
  if (route === '/internal/web/v1/session/logout') {
    if (method !== 'POST') throw new RequestError(405, 'METHOD_NOT_ALLOWED', '请求方法不受支持');
    auth.logoutWeb(sessionToken, clientIp, requestId);
    return write(200, { ok: true });
  }
  const current = auth.webAccount(sessionToken);
  if (!current) return write(401, { ok: false, error: 'SESSION_REVOKED', message: '登录会话无效或已过期' });
  if (route === '/internal/web/v1/account') {
    if (method !== 'GET') throw new RequestError(405, 'METHOD_NOT_ALLOWED', '请求方法不受支持');
    return write(200, { ok: true, account: current.account, sessionExpiresAt: current.session.expiresAtUtc });
  }
  if (route === '/internal/web/v1/account/beta/redeem') {
    if (method !== 'POST') throw new RequestError(405, 'METHOD_NOT_ALLOWED', '请求方法不受支持');
    const redeemed = auth.redeemBetaCode(current.session, text(payload.code), clientIp, requestId);
    if (!redeemed.Ok) return write(apiStatus(redeemed.Error), {
      ok: false, error: redeemed.Error, message: redeemed.Message
    });
    const refreshed = auth.webAccount(sessionToken);
    return write(200, {
      ok: true,
      message: redeemed.AlreadyApplied ? '该卡密此前已兑换，账号授权未重复增加' : 'Beta 授权兑换成功，请重新登录 Beta 客户端',
      account: refreshed ? refreshed.account : redeemed.Account,
      redemption: Object.assign({}, redeemed.Redemption, { alreadyApplied: Boolean(redeemed.AlreadyApplied) })
    });
  }
  if (route === '/internal/web/v1/account/password') {
    if (method !== 'POST') throw new RequestError(405, 'METHOD_NOT_ALLOWED', '请求方法不受支持');
    const problem = auth.changePassword(current.session, text(payload.currentPassword), text(payload.newPassword), clientIp, requestId);
    return problem ? write(400, { ok: false, error: 'PASSWORD_CHANGE_REJECTED', message: problem })
      : write(200, { ok: true, reauthenticationRequired: true });
  }
  if (route === '/internal/web/v1/account/hwid/reset') {
    if (method !== 'POST') throw new RequestError(405, 'METHOD_NOT_ALLOWED', '请求方法不受支持');
    const problem = auth.resetHwid(current.session, text(payload.currentPassword), clientIp, requestId);
    return problem ? write(400, { ok: false, error: 'HWID_RESET_REJECTED', message: problem })
      : write(200, { ok: true, reauthenticationRequired: true });
  }
  throw new RequestError(404, 'NOT_FOUND', '接口不存在');
}

async function handleWeb(auth, config, request, response, method, route, url, remoteIp, requestId) {
  if (method === 'GET' && route === '/') return redirect(response, '/user');
  if (method === 'GET' && route === '/user/login') return writeHtml(response, loginPage(false, url.searchParams.get('message')));
  if (method === 'GET' && route === '/admin/login') return writeHtml(response, loginPage(true, url.searchParams.get('message')));
  if (method === 'POST' && (route === '/user/login' || route === '/admin/login')) {
    if (!contentType(request, 'application/x-www-form-urlencoded')) throw new RequestError(415, 'INVALID_CONTENT_TYPE', '表单格式无效');
    const form = querystring.parse(await body(request)); const admin = route.startsWith('/admin');
    const login = auth.createWebSession(text(form.username), text(form.password), admin, remoteIp, requestId);
    if (login.error) return redirectMessage(response, admin ? '/admin/login' : '/user/login', login.error);
    setCookie(response, config, admin ? 'oraculus_admin_session' : 'oraculus_user_session', login.token, false);
    return redirect(response, admin ? '/admin' : '/user');
  }
  if (route.startsWith('/user')) return handleUser(auth, config, request, response, method, route, url, remoteIp, requestId);
  if (route.startsWith('/admin')) return handleAdmin(auth, config, request, response, method, route, url, remoteIp, requestId);
  return writeText(response, 404, 'Not Found');
}
async function form(request) { if (!contentType(request, 'application/x-www-form-urlencoded')) throw new RequestError(415, 'INVALID_CONTENT_TYPE', '表单格式无效'); return querystring.parse(await body(request)); }
function csrf(session, value) { if (!session || !safeEqual(session.csrfToken, value)) throw new RequestError(403, 'CSRF_REJECTED', '页面已过期，请刷新后重试'); }
async function handleUser(auth, config, request, response, method, route, url, remoteIp, requestId) {
  const cookie = cookies(request); const session = auth.getWebSession(cookie.oraculus_user_session, false);
  if (!session) return redirect(response, '/user/login');
  if (method === 'GET' && route === '/user') return writeHtml(response, userPageWithRedemption(
    auth.getUser(session.userId), session.csrfToken, url.searchParams.get('message'), auth.betaPublicAccess(),
    auth.listBetaRedemptions(session.userId, 5)));
  if (method === 'POST' && route === '/user/beta/redeem') {
    const values = await form(request); csrf(session, text(values.csrf));
    const redeemed = auth.redeemBetaCode(session, text(values.code), remoteIp, requestId);
    return redirectMessage(response, '/user', redeemed.Ok
      ? (redeemed.AlreadyApplied ? '该卡密此前已兑换，授权未重复增加' : 'Beta 授权兑换成功，请重新登录 Beta 客户端')
      : redeemed.Message);
  }
  if (method === 'GET' && route === '/user') return writeHtml(response, userPage(auth.getUser(session.userId), session.csrfToken, url.searchParams.get('message'), auth.betaPublicAccess()));
  if (method === 'POST' && route === '/user/logout') { const values = await form(request); csrf(session, text(values.csrf)); auth.logoutWeb(cookie.oraculus_user_session, remoteIp, requestId); setCookie(response, config, 'oraculus_user_session', '', true); return redirect(response, '/user/login'); }
  if (method === 'POST' && (route === '/user/password' || route === '/user/hwid/reset')) { const values = await form(request); csrf(session, text(values.csrf)); const problem = route === '/user/password' ? auth.changePassword(session, text(values.currentPassword), text(values.newPassword), remoteIp, requestId) : auth.resetHwid(session, text(values.currentPassword), remoteIp, requestId); if (problem) return redirectMessage(response, '/user', problem); setCookie(response, config, 'oraculus_user_session', '', true); return redirectMessage(response, '/user/login', '操作成功，请重新登录'); }
  return writeText(response, 404, 'Not Found');
}
async function handleAdmin(auth, config, request, response, method, route, url, remoteIp, requestId) {
  const cookie = cookies(request); const session = auth.getWebSession(cookie.oraculus_admin_session, true);
  if (!session) return redirect(response, '/admin/login');
  if (method === 'GET' && route === '/admin') {
    const listing = auth.listUsers(url.searchParams.get('q'), url.searchParams.get('page'));
    return writeHtml(response, adminPageWithBetaLink(session, listing, url.searchParams.get('message'), auth.betaPublicAccess()));
  }
  if (method === 'GET' && route === '/admin/beta-codes') {
    const range = betaCodeBillingRange(url.searchParams.get('billingFrom'), url.searchParams.get('billingTo'));
    const billing = range.Ok
      ? Object.assign({}, range, auth.calculateBetaCodeValue(session, range.FromUtc, range.ToExclusiveUtc))
      : range;
    return writeHtml(response, betaCodesPage(session, auth.listBetaCodeBatches(),
      auth.listBetaRedemptions(null, 200), billing, url.searchParams.get('message')));
  }
  if (method === 'POST' && route === '/admin/beta-codes/batches/create') {
    const values = await form(request); csrf(session, text(values.csrf));
    const created = auth.createBetaCodeBatch(session, {
      label: values.label, product: values.product, durationDays: values.durationDays,
      quantity: values.quantity, codeExpiresAtUtc: parseExpiry(values.codeExpiresAt), note: values.note
    }, remoteIp, requestId);
    if (!created.Ok) return redirectMessage(response, '/admin/beta-codes', created.Message);
    const metadata = [
      '# Oraculus Beta codes', `# Batch: ${created.Batch.id}`, `# Label: ${created.Batch.label}`,
      `# Product: ${created.Batch.product}`, `# Generated: ${new Date(created.Batch.createdAtUtc * 1000).toISOString()}`, ''
    ];
    return writeAttachment(response, `oraculus-beta-${created.Batch.id.slice(0, 8)}.txt`, `${metadata.concat(created.Codes).join('\n')}\n`);
  }
  if (method === 'POST' && route === '/admin/beta-codes/batches/disable') {
    const values = await form(request); csrf(session, text(values.csrf));
    const disabled = auth.disableBetaCodeBatch(session, text(values.batchId), remoteIp, requestId);
    return redirectMessage(response, '/admin/beta-codes', disabled.Ok ? '卡密批次已禁用' : disabled.Message);
  }
  if (method === 'POST' && route === '/admin/beta-codes/batches/review') {
    const values = await form(request); csrf(session, text(values.csrf));
    const reviewed = auth.reviewBetaCodeBatch(session, text(values.batchId), text(values.reviewNote), remoteIp, requestId);
    return redirectMessage(response, '/admin/beta-codes', reviewed.Ok ? '客户端发卡批次已标记为已审' : reviewed.Message);
  }
  if (method === 'POST' && route === '/admin/beta-codes/lookup') {
    const values = await form(request); csrf(session, text(values.csrf));
    const found = auth.lookupBetaCode(session, text(values.code), remoteIp, requestId);
    return redirectMessage(response, '/admin/beta-codes', found.Message);
  }
  if (method === 'GET' && route === '/admin') {
    const listing = auth.listUsers(url.searchParams.get('q'), url.searchParams.get('page'));
    return writeHtml(response, adminPage(session, listing, url.searchParams.get('message'), auth.betaPublicAccess()));
  }
  if (method === 'GET' && route === '/admin/audit') return writeHtml(response, auditPage(session, auth.listAuditLogs()));
  if (method === 'POST' && route === '/admin/logout') { const values = await form(request); csrf(session, text(values.csrf)); auth.logoutWeb(cookie.oraculus_admin_session, remoteIp, requestId); setCookie(response, config, 'oraculus_admin_session', '', true); return redirect(response, '/admin/login'); }
  if (method === 'POST' && route === '/admin/create') { const values = await form(request); csrf(session, text(values.csrf)); const problem = auth.createUser(session, text(values.username), text(values.password), text(values.role), text(values.tier), parseExpiry(values.expiryText), remoteIp, requestId); return redirectMessage(response, '/admin', problem || '账号已创建'); }
  if (method === 'POST' && route === '/admin/beta-public') { const values = await form(request); csrf(session, text(values.csrf)); const problem = auth.setBetaPublicAccess(session, text(values.enabled) === 'true', parseExpiry(values.expiryText), remoteIp, requestId); return redirectMessage(response, '/admin', problem || '限时 Beta 公益设置已保存'); }
  if (method === 'POST' && route === '/admin/action') { const values = await form(request); csrf(session, text(values.csrf)); if (values.expiryText) values.expiry = parseExpiry(values.expiryText) || ''; const problem = auth.adminAction(session, text(values.userId), text(values.action), values, remoteIp, requestId); return redirectMessage(response, '/admin', problem || '操作已完成'); }
  return writeText(response, 404, 'Not Found');
}

function commandLine() {
  const args = process.argv.slice(2); const configIndex = args.findIndex(value => value === '--config');
  const configPath = configIndex >= 0 && args[configIndex + 1] ? args[configIndex + 1] : path.join(__dirname, 'server.json');
  if (args.includes('--version')) { console.log('Oraculus Auth Node Server 1.0.0'); return; }
  if (args.includes('--self-test')) { selfTest(); return; }
  const config = AppConfig.load(configPath);
  if (args.includes('--check-config')) { console.log('CONFIGURATION OK'); return; }
  const auth = new AuthService(config);
  if (args.includes('--check-ready')) { if (!auth.ready()) throw new Error('Database readiness check failed'); console.log('READY'); return; }
  const ensure = args.indexOf('--ensure-admin');
  if (ensure >= 0) { const username = args[ensure + 1]; if (!username) throw new Error('--ensure-admin requires a username'); const password = fs.readFileSync(0, 'utf8').trim(); if (!password) throw new Error('administrator password was not provided through standard input'); console.log(auth.ensureAdmin(username, password) ? 'ADMIN CREATED' : 'ADMIN RETAINED'); return; }
  startServer(configPath);
}
function selfTest() {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'oraculus-auth-self-test-'));
  try {
    const configPath = path.join(directory, 'server.json'); const config = AppConfig.defaults(configPath); config.DataDirectory = path.join(directory, 'data'); fs.writeFileSync(configPath, JSON.stringify(config), 'utf8');
    const remoteConfigPath = path.join(directory, 'remote-server.json'); const remoteConfig = AppConfig.defaults(remoteConfigPath);
    remoteConfig.DataDirectory = path.join(directory, 'remote-data'); remoteConfig.InternalWebsiteSecret = 'website-remote-self-test-secret-2026-0123456789';
    remoteConfig.InternalWebHost = '0.0.0.0'; remoteConfig.InternalWebPort = 3101; remoteConfig.InternalWebTls = true;
    remoteConfig.InternalWebAllowedIps = ['160.202.238.53']; remoteConfig.TlsCertificatePath = 'test.crt'; remoteConfig.TlsPrivateKeyPath = 'test.key';
    fs.writeFileSync(remoteConfigPath, JSON.stringify(remoteConfig), 'utf8');
    const loadedRemoteConfig = AppConfig.load(remoteConfigPath);
    if (!loadedRemoteConfig.InternalWebTls || loadedRemoteConfig.InternalWebAllowedIps[0] !== '160.202.238.53')
      throw new Error('remote website bridge configuration self-test failed');
    const auth = new AuthService(AppConfig.load(configPath)); const fingerprint = 'self-test-fingerprint-01234567890123456789';
    if (!auth.ensureAdmin('root_admin', 'InitialAdminPassword!2026')) throw new Error('administrator bootstrap self-test failed');
    const originalAdminHash = findUserByName(auth.store.data, 'root_admin').passwordHash;
    if (auth.ensureAdmin('root_admin', 'DifferentAdminPassword!2026')) throw new Error('administrator retention self-test failed');
    if (findUserByName(auth.store.data, 'root_admin').passwordHash !== originalAdminHash) throw new Error('administrator password was changed during retention');
    const rootAdmin = findUserByName(auth.store.data, 'root_admin');
    const rootSession = { userId: rootAdmin.id, user: rootAdmin };
    const futureExpiry = now() + 30 * 86400;
    if (auth.createUser(rootSession, 'support_admin', 'SupportAdminPassword!2026', 'SUPPORT_ADMIN',
      'FREE', null, '127.0.0.1', 'self-create-support')) throw new Error('support administrator creation self-test failed');
    const supportAdmin = findUserByName(auth.store.data, 'support_admin');
    const supportSession = { userId: supportAdmin.id, user: supportAdmin };
    if (auth.createUser(supportSession, 'managed_user', 'ManagedUserPassword!2026', 'USER',
      'FREE', null, '127.0.0.1', 'self-support-create-user')) throw new Error('support user creation self-test failed');
    if (!auth.createUser(supportSession, 'denied_support', 'DeniedSupportPassword!2026', 'SUPPORT_ADMIN',
      'FREE', null, '127.0.0.1', 'self-support-create-support')) throw new Error('support hierarchy self-test failed');
    const managedUser = findUserByName(auth.store.data, 'managed_user');
    if (auth.adminAction(supportSession, managedUser.id, 'tier', { tier: 'BETA', expiry: futureExpiry },
      '127.0.0.1', 'self-support-tier')) throw new Error('support tier permission self-test failed');
    if (!auth.adminAction(supportSession, supportAdmin.id, 'tier', { tier: 'BETA', expiry: futureExpiry },
      '127.0.0.1', 'self-support-target-support')) throw new Error('support target isolation self-test failed');
    const searchResult = auth.listUsers('managed_', 1);
    if (searchResult.matched !== 1 || searchResult.users[0].username !== 'managed_user')
      throw new Error('administrator search self-test failed');
    auth.store.mutate(data => {
      for (let index = 0; index < 520; index += 1)
        data.users.push(newUser(`bulk_${String(index).padStart(4, '0')}`, originalAdminHash,
          { creationSource: 'SELF_TEST_BULK' }));
    });
    const pagedUsers = auth.listUsers('', 3);
    if (pagedUsers.total < 523 || pagedUsers.pageCount < 3 || pagedUsers.page !== 3 || !pagedUsers.users.length)
      throw new Error('administrator pagination self-test failed');
    if (auth.listUsers('bulk_0519', 1).matched !== 1)
      throw new Error('large user search self-test failed');
    const superPage = adminPage(rootSession, auth.listUsers('bulk_', 3), null, auth.betaPublicAccess());
    if (!superPage.includes('SUPPORT_ADMIN') || !superPage.includes('/admin/audit') || !superPage.includes('第 3 / 3 页'))
      throw new Error('super administrator page self-test failed');
    const supportPage = adminPage(supportSession, auth.listUsers('support_admin', 1), null, auth.betaPublicAccess());
    if (!supportPage.includes('name="role" value="USER"') || !supportPage.includes('仅超级管理员可管理'))
      throw new Error('support administrator page self-test failed');
    const pricingCases = [
      [{ product: 'BETA_DURATION', durationSeconds: 1 * 86400 }, 'DAY_1', 3],
      [{ product: 'BETA_DURATION', durationSeconds: 2 * 86400 }, 'DAY_2', 5],
      [{ product: 'BETA_DURATION', durationSeconds: 7 * 86400 }, 'DAY_7', 10],
      [{ product: 'BETA_DURATION', durationSeconds: 30 * 86400 }, 'DAY_30', 25],
      [{ product: 'BETA_DURATION', durationSeconds: 365 * 86400 }, 'DAY_365', 40],
      [{ product: 'BETA_DURATION', durationSeconds: 400 * 86400 }, 'UNPRICED:400 天', null],
      [{ product: 'BETA_DURATION', durationSeconds: 401 * 86400 }, 'PERMANENT', 50],
      [{ product: 'BETA_PERMANENT', durationSeconds: null }, 'PERMANENT', 50]
    ];
    for (const [batch, key, value] of pricingCases) {
      const category = betaCodeValueCategory(batch);
      if (category.key !== key || category.unitValue !== value)
        throw new Error(`beta billing pricing self-test failed for ${key}`);
    }
    if (!auditPage(rootSession, auth.listAuditLogs()).includes('简明审计日志'))
      throw new Error('audit page self-test failed');
    if (auth.adminAction(rootSession, supportAdmin.id, 'password', { password: 'SupportTemporary!2026' },
      '127.0.0.1', 'self-support-temp-password')) throw new Error('temporary password setup self-test failed');
    const blockedAdminLogin = auth.createWebSession('support_admin', 'SupportTemporary!2026', true,
      '127.0.0.1', 'self-support-admin-login-blocked');
    if (!blockedAdminLogin.error) throw new Error('temporary password administrator login self-test failed');
    const supportUserLogin = auth.createWebSession('support_admin', 'SupportTemporary!2026', false,
      '127.0.0.1', 'self-support-user-login');
    if (!supportUserLogin.token) throw new Error('temporary password user-panel login self-test failed');
    if (!auth.changePassword({ userId: supportAdmin.id }, 'SupportTemporary!2026', 'SupportTemporary!2026',
      '127.0.0.1', 'self-support-reuse-temp-password')) throw new Error('temporary password reuse self-test failed');
    if (auth.changePassword({ userId: supportAdmin.id }, 'SupportTemporary!2026', 'SupportChangedPassword!2026',
      '127.0.0.1', 'self-support-change-password')) throw new Error('forced password cooldown bypass self-test failed');
    if (!auth.createWebSession('support_admin', 'SupportChangedPassword!2026', true,
      '127.0.0.1', 'self-support-admin-login').token) throw new Error('support administrator login self-test failed');
    if (!auth.listAuditLogs().some(entry => entry.action === 'ADMIN_CREATE_SUPPORT' && entry.success))
      throw new Error('audit log self-test failed');
    const blockedLauncher = auth.register({ username: 'blocked_launcher', password: 'SelfTestPassword!2026', deviceFingerprint: fingerprint, hwidVersion: 'v1', hwidQuality: 'STRONG', edition: 'FREE', clientVersion: 'b8', buildId: 'b8-free', launcherVersion: 'v0.9.20' }, '127.0.0.1', 'self-launcher-blocked');
    if (blockedLauncher.Ok || blockedLauncher.Error !== 'LAUNCHER_VERSION_BLOCKED')
      throw new Error('launcher version rejection self-test failed');
    const registered = auth.register({ username: 'selftest_user', password: 'SelfTestPassword!2026', deviceFingerprint: fingerprint, hwidVersion: 'v1', hwidQuality: 'STRONG', edition: 'FREE', clientVersion: 'b8', buildId: 'b8-free', launcherVersion: 'v0.9.21' }, '127.0.0.1', 'self-register');
    if (!registered.Ok || !registered.AccessToken || !registered.RefreshToken) throw new Error('registration self-test failed');
    const launcherLogin = auth.launcherLogin({ username: 'selftest_user', deviceFingerprint: fingerprint, hwidVersion: 'v1', hwidQuality: 'STRONG', edition: 'FREE', clientVersion: 'b8', buildId: 'b8-free' }, registered.AccessToken, '127.0.0.1', 'self-launcher-login');
    if (!launcherLogin.Ok || !launcherLogin.AccessToken || !launcherLogin.RefreshToken
      || launcherLogin.AccessToken === registered.AccessToken || launcherLogin.Account.username !== 'selftest_user')
      throw new Error('launcher access token login self-test failed');
    const mismatchedLauncherLogin = auth.launcherLogin({ username: 'different_user', deviceFingerprint: fingerprint, hwidVersion: 'v1', hwidQuality: 'STRONG', edition: 'FREE', clientVersion: 'b8', buildId: 'b8-free' }, registered.AccessToken, '127.0.0.1', 'self-launcher-login-mismatch');
    if (mismatchedLauncherLogin.Ok || mismatchedLauncherLogin.Error !== 'SESSION_REVOKED')
      throw new Error('launcher access token username binding self-test failed');
    const legalFreeAccount = auth.legality(registered.AccessToken);
    if (!legalFreeAccount.Ok || !legalFreeAccount.Exists || !legalFreeAccount.Active
      || legalFreeAccount.BetaEligible || legalFreeAccount.Username !== 'selftest_user')
      throw new Error('account legality self-test failed');
    auth.store.mutate(data => { findUserByName(data, 'selftest_user').status = 'DELETED'; });
    const deletedAccount = auth.legality(registered.AccessToken);
    if (!deletedAccount.Ok || deletedAccount.Exists || deletedAccount.Active || deletedAccount.BetaEligible)
      throw new Error('deleted account legality self-test failed');
    auth.store.mutate(data => { findUserByName(data, 'selftest_user').status = 'ACTIVE'; });
    const registeredClaims = verifiedEntitlementClaims(registered.EntitlementProof, auth.entitlementSigner.publicKey);
    if (registeredClaims.version !== 1 || registeredClaims.keyId !== auth.entitlementSigner.keyId
      || registeredClaims.subject !== registered.Account.id || registeredClaims.username !== registered.Account.username
      || registeredClaims.edition !== 'FREE' || registeredClaims.clientVersion !== 'b8'
      || registeredClaims.buildId !== 'b8-free' || registeredClaims.deviceFingerprintHash !== sha256(fingerprint)
      || registeredClaims.accessTokenHash !== sha256(registered.AccessToken)
      || registeredClaims.accessExpiresAt !== registered.AccessExpiresAt
      || registeredClaims.refreshExpiresAt !== registered.RefreshExpiresAt)
      throw new Error('registration entitlement claims self-test failed');
    const proofParts = registered.EntitlementProof.split('.');
    let tamperedProofRejected = false;
    try {
      const tamperedPayload = `${proofParts[0][0] === 'A' ? 'B' : 'A'}${proofParts[0].slice(1)}`;
      verifiedEntitlementClaims(`${tamperedPayload}.${proofParts[1]}`, auth.entitlementSigner.publicKey);
    }
    catch { tamperedProofRejected = true; }
    if (!tamperedProofRejected) throw new Error('tampered entitlement proof self-test failed');
    const clientOnly = auth.login({ username: 'selftest_user', password: 'SelfTestPassword!2026', deviceFingerprint: fingerprint, hwidVersion: 'v1', hwidQuality: 'STRONG', edition: 'FREE', clientVersion: 'b8', buildId: 'b8-free' }, '127.0.0.1', 'self-client-only-login');
    if (!clientOnly.Ok || !auth.heartbeat(clientOnly.AccessToken, '127.0.0.1', 'self-client-only-heartbeat').Ok)
      throw new Error('client-only version gate self-test failed');
    const launcherPriority = auth.login({ username: 'selftest_user', password: 'SelfTestPassword!2026', deviceFingerprint: fingerprint, hwidVersion: 'v1', hwidQuality: 'STRONG', edition: 'FREE', clientVersion: 'unsupported-client', buildId: 'unsupported-build', launcherVersion: 'v0.9.21' }, '127.0.0.1', 'self-launcher-priority-login');
    if (!launcherPriority.Ok)
      throw new Error('launcher-priority version gate self-test failed');
    const blockedClientOnly = auth.login({ username: 'selftest_user', password: 'SelfTestPassword!2026', deviceFingerprint: fingerprint, hwidVersion: 'v1', hwidQuality: 'STRONG', edition: 'FREE', clientVersion: 'unsupported-client', buildId: 'unsupported-build' }, '127.0.0.1', 'self-client-only-blocked');
    if (blockedClientOnly.Ok || blockedClientOnly.Error !== 'CLIENT_VERSION_BLOCKED')
      throw new Error('client-only version rejection self-test failed');
    const webRegistered = auth.registerWeb('website_user', 'WebsitePassword!2026', '127.0.0.2', 'self-web-register');
    if (!webRegistered.Ok || !webRegistered.Token || webRegistered.Account.hwidBound)
      throw new Error('website registration self-test failed');
    const webAccount = auth.webAccount(webRegistered.Token);
    if (!webAccount || webAccount.account.username !== 'website_user')
      throw new Error('website account session self-test failed');
    const clientIssuer = registered;
    if (!clientIssuer.Ok || !clientIssuer.AccessToken || clientIssuer.Account.role !== 'USER')
      throw new Error('client issuer login self-test failed');
    const issueValues = {
      product: 'BETA_DURATION', durationDays: 30, quantity: 2,
      label: 'self-client-issue', note: 'ordinary user issuance', codeExpiresAtUtc: null
    };
    const issueKey = 'self-client-issue-key-20260804-0001';
    const clientIssued = auth.issueBetaCodesFromClient(clientIssuer.AccessToken, issueValues,
      issueKey, '127.0.0.30', 'self-client-issue');
    if (!clientIssued.Ok || clientIssued.IdempotentReplay || clientIssued.Codes.length !== 2
      || clientIssued.Batch.reviewStatus !== 'PENDING_REVIEW'
      || clientIssued.Batch.requestedByRoleSnapshot !== 'USER')
      throw new Error('client beta issuance self-test failed');
    if (JSON.stringify(auth.store.data).includes(clientIssued.Codes[0]))
      throw new Error('client issuance plaintext persistence self-test failed');
    const clientReplay = auth.issueBetaCodesFromClient(clientIssuer.AccessToken, issueValues,
      issueKey, '127.0.0.30', 'self-client-issue-replay');
    if (!clientReplay.Ok || !clientReplay.IdempotentReplay
      || JSON.stringify(clientReplay.Codes) !== JSON.stringify(clientIssued.Codes)
      || clientReplay.Batch.id !== clientIssued.Batch.id)
      throw new Error('client issuance idempotent replay self-test failed');
    const clientConflict = auth.issueBetaCodesFromClient(clientIssuer.AccessToken,
      Object.assign({}, issueValues, { quantity: 1 }), issueKey, '127.0.0.30', 'self-client-issue-conflict');
    if (clientConflict.Ok || clientConflict.Error !== 'IDEMPOTENCY_KEY_REUSED')
      throw new Error('client issuance idempotency conflict self-test failed');
    const clientIssueRecord = auth.store.data.betaCodeIssueRequests.find(item => item.batchId === clientIssued.Batch.id);
    if (!clientIssueRecord || !clientIssueRecord.clientSessionId || !clientIssueRecord.remoteIpHash
      || clientIssueRecord.actorRoleSnapshot !== 'USER')
      throw new Error('client issuance review metadata self-test failed');
    const supportReviewDenied = auth.reviewBetaCodeBatch(supportSession, clientIssued.Batch.id,
      'support cannot approve', '127.0.0.31', 'self-client-review-denied');
    if (supportReviewDenied.Ok || supportReviewDenied.Error !== 'ADMIN_PERMISSION_DENIED')
      throw new Error('client issuance support review isolation self-test failed');
    const reviewedClientBatch = auth.reviewBetaCodeBatch(rootSession, clientIssued.Batch.id,
      'self-test approved', '127.0.0.32', 'self-client-review');
    if (!reviewedClientBatch.Ok || reviewedClientBatch.Batch.reviewStatus !== 'REVIEWED'
      || reviewedClientBatch.Batch.reviewedByUserId !== rootAdmin.id)
      throw new Error('client issuance review self-test failed');
    const clientPermanent = auth.issueBetaCodesFromClient(clientIssuer.AccessToken,
      { product: 'BETA_PERMANENT', quantity: 1, label: 'self-client-permanent' },
      'self-client-permanent-key-20260804', '127.0.0.30', 'self-client-permanent');
    if (!clientPermanent.Ok || clientPermanent.Batch.product !== 'BETA_PERMANENT')
      throw new Error('client permanent issuance self-test failed');
    auth.store.mutate(data => {
      const record = data.betaCodeIssueRequests.find(item => item.batchId === clientIssued.Batch.id);
      if (record) record.deliveryExpiresAtUtc = now() - 1;
    });
    const expiredClientReplay = auth.issueBetaCodesFromClient(clientIssuer.AccessToken, issueValues,
      issueKey, '127.0.0.30', 'self-client-issue-expired-replay');
    if (expiredClientReplay.Ok || expiredClientReplay.Error !== 'ISSUE_RESULT_EXPIRED'
      || !expiredClientReplay.BatchId)
      throw new Error('client issuance delivery expiry self-test failed');
    const durationBatch = auth.createBetaCodeBatch(rootSession, {
      label: 'self-test-duration', product: 'BETA_DURATION', durationDays: 30, quantity: 6
    }, '127.0.0.10', 'self-beta-duration-batch');
    if (!durationBatch.Ok || durationBatch.Codes.length !== 6
      || new Set(durationBatch.Codes).size !== 6
      || durationBatch.Codes.some(code => !normalizeBetaCode(code)))
      throw new Error('beta duration batch generation self-test failed');
    if (!auth.lookupBetaCode(supportSession, durationBatch.Codes[0], '127.0.0.10', 'self-beta-lookup').Ok)
      throw new Error('beta code lookup self-test failed');
    if (JSON.stringify(auth.store.data).includes(durationBatch.Codes[0]))
      throw new Error('plaintext beta code persistence self-test failed');
    const deniedBatch = auth.createBetaCodeBatch(supportSession, {
      label: 'denied', product: 'BETA_DURATION', durationDays: 30, quantity: 1
    }, '127.0.0.11', 'self-beta-denied-batch');
    if (deniedBatch.Ok || deniedBatch.Error !== 'ADMIN_PERMISSION_DENIED')
      throw new Error('beta batch administrator permission self-test failed');
    const firstRedeemAt = now();
    const firstRedeem = auth.redeemBetaCode(webAccount.session, durationBatch.Codes[0], '127.0.0.12', 'self-beta-free-redeem');
    if (!firstRedeem.Ok || firstRedeem.AlreadyApplied || firstRedeem.Account.tier !== 'BETA'
      || firstRedeem.Account.betaExpiresAt < firstRedeemAt + 30 * 86400
      || !firstRedeem.Account.betaActive)
      throw new Error('free-to-beta redemption self-test failed');
    const firstExpiry = firstRedeem.Account.betaExpiresAt;
    const retryRedeem = auth.redeemBetaCode(webAccount.session, durationBatch.Codes[0], '127.0.0.12', 'self-beta-retry');
    if (!retryRedeem.Ok || !retryRedeem.AlreadyApplied || retryRedeem.Account.betaExpiresAt !== firstExpiry)
      throw new Error('idempotent beta redemption self-test failed');
    let expiredUser; let publicUser;
    auth.store.mutate(data => {
      expiredUser = newUser('expired_beta_user', originalAdminHash, { tier: 'BETA', betaExpiresAtUtc: now() - 60 });
      publicUser = newUser('public_beta_user', originalAdminHash, { tier: 'FREE' });
      data.users.push(expiredUser, publicUser);
    });
    const otherReuse = auth.redeemBetaCode({ userId: expiredUser.id }, durationBatch.Codes[0], '127.0.0.13', 'self-beta-other-reuse');
    if (otherReuse.Ok || otherReuse.Error !== 'INVALID_REDEEM_CODE')
      throw new Error('cross-account beta code reuse self-test failed');
    const stacked = auth.redeemBetaCode(webAccount.session, durationBatch.Codes[1], '127.0.0.12', 'self-beta-stack');
    if (!stacked.Ok || stacked.Account.betaExpiresAt !== firstExpiry + 30 * 86400)
      throw new Error('active beta duration stacking self-test failed');
    const expiredRedeemAt = now();
    const expiredRedeem = auth.redeemBetaCode({ userId: expiredUser.id }, durationBatch.Codes[2], '127.0.0.14', 'self-beta-expired');
    if (!expiredRedeem.Ok || expiredRedeem.Account.betaExpiresAt < expiredRedeemAt + 30 * 86400)
      throw new Error('expired beta restart self-test failed');
    if (auth.setBetaPublicAccess(rootSession, true, now() + 3600, '127.0.0.15', 'self-beta-public-enable'))
      throw new Error('public beta setup for redemption self-test failed');
    const publicRedeem = auth.redeemBetaCode({ userId: publicUser.id }, durationBatch.Codes[3], '127.0.0.16', 'self-beta-public-redeem');
    if (!publicRedeem.Ok || publicRedeem.Account.tier !== 'BETA' || !publicRedeem.Account.betaActive)
      throw new Error('public beta persistent redemption self-test failed');
    if (auth.setBetaPublicAccess(rootSession, false, null, '127.0.0.15', 'self-beta-public-disable'))
      throw new Error('public beta cleanup self-test failed');
    const permanentBatch = auth.createBetaCodeBatch(rootSession, {
      label: 'self-test-permanent', product: 'BETA_PERMANENT', quantity: 2
    }, '127.0.0.17', 'self-beta-permanent-batch');
    if (!permanentBatch.Ok) throw new Error('permanent beta batch generation self-test failed');
    const permanentRedeem = auth.redeemBetaCode(webAccount.session, permanentBatch.Codes[0], '127.0.0.18', 'self-beta-permanent-redeem');
    if (!permanentRedeem.Ok || permanentRedeem.Account.betaExpiresAt !== PERMANENT_BETA_EXPIRES_AT_UTC
      || !permanentRedeem.Account.betaPermanent)
      throw new Error('permanent beta redemption self-test failed');
    const durationAfterPermanent = auth.redeemBetaCode(webAccount.session, durationBatch.Codes[4], '127.0.0.19', 'self-beta-after-permanent');
    if (durationAfterPermanent.Ok || durationAfterPermanent.Error !== 'BETA_EXPIRY_LIMIT')
      throw new Error('duration-after-permanent boundary self-test failed');
    const disabledBatch = auth.createBetaCodeBatch(rootSession, {
      label: 'self-test-disabled', product: 'BETA_DURATION', durationDays: 7, quantity: 1
    }, '127.0.0.20', 'self-beta-disabled-batch');
    if (!disabledBatch.Ok || !auth.disableBetaCodeBatch(rootSession, disabledBatch.Batch.id,
      '127.0.0.20', 'self-beta-disable').Ok)
      throw new Error('beta batch disable self-test failed');
    const disabledRedeem = auth.redeemBetaCode({ userId: expiredUser.id }, disabledBatch.Codes[0], '127.0.0.21', 'self-beta-disabled-redeem');
    if (disabledRedeem.Ok || disabledRedeem.Error !== 'INVALID_REDEEM_CODE')
      throw new Error('disabled beta code rejection self-test failed');
    const expiringBatch = auth.createBetaCodeBatch(rootSession, {
      label: 'self-test-expired-code', product: 'BETA_DURATION', durationDays: 7, quantity: 1,
      codeExpiresAtUtc: now() + 60
    }, '127.0.0.22', 'self-beta-expiring-batch');
    auth.store.mutate(data => { data.betaCodeBatches.find(batch => batch.id === expiringBatch.Batch.id).codeExpiresAtUtc = now() - 1; });
    const expiredCodeRedeem = auth.redeemBetaCode({ userId: publicUser.id }, expiringBatch.Codes[0], '127.0.0.23', 'self-beta-expired-code');
    if (expiredCodeRedeem.Ok || expiredCodeRedeem.Error !== 'INVALID_REDEEM_CODE')
      throw new Error('expired beta code rejection self-test failed');
    const billing = auth.calculateBetaCodeValue(supportSession, now() - 60, now() + 60);
    if (!billing.Ok || billing.BatchCount !== 6 || billing.TotalQuantity !== 13
      || billing.PricedQuantity !== 13 || billing.UnpricedQuantity !== 0 || billing.TotalValue !== 370)
      throw new Error('beta billing aggregation self-test failed');
    const supportBillingPage = betaCodesPage(supportSession, auth.listBetaCodeBatches(),
      auth.listBetaRedemptions(null, 200), Object.assign({ FromDate: '2026-01-01', ToDate: '2026-01-31' }, billing), null);
    if (!supportBillingPage.includes('卡密计费') || !supportBillingPage.includes('总价值：¥370')
      || !supportBillingPage.includes('name="billingFrom"') || !supportBillingPage.includes('客户端发卡审查')
      || !supportBillingPage.includes('selftest_user'))
      throw new Error('support billing page self-test failed');
    if (auth.store.data.betaRedemptions.length !== 5 || auth.listBetaRedemptions(webAccount.session.userId, 5).length !== 3)
      throw new Error('beta redemption history self-test failed');
    if (auth.changePassword(webAccount.session, 'WebsitePassword!2026', 'WebsiteChangedPassword!2026',
      '127.0.0.2', 'self-web-password')) throw new Error('website password self-test failed');
    if (auth.getWebSession(webRegistered.Token, false)) throw new Error('website password revocation self-test failed');
    const irc = new IrcHub(auth);
    const ircAuthentication = irc.authorize(registered.AccessToken);
    if (!ircAuthentication || ircAuthentication.user.username !== 'selftest_user')
      throw new Error('IRC authentication self-test failed');
    irc.clients.set('self-test-client', {
      id: 'self-test-client', userId: ircAuthentication.user.id,
      accessToken: registered.AccessToken, response: null, connectedAtUtc: now()
    });
    const ircPresence = irc.updatePresence(ircAuthentication, {
      minecraftProfileId: '0123456789abcdef0123456789abcdef',
      minecraftProfileName: 'SelfTestPlayer',
      state: 'IN_GAME'
    });
    if (!ircPresence.Ok || irc.roster().length !== 1 || irc.roster()[0].profileVerified)
      throw new Error('IRC presence self-test failed');
    const identityChallenge = irc.createIdentityChallenge(ircAuthentication, {
      minecraftProfileId: '0123456789abcdef0123456789abcdef',
      minecraftProfileName: 'SelfTestPlayer'
    });
    if (!identityChallenge.Ok || !/^[0-9a-f]{40}$/.test(identityChallenge.ServerId))
      throw new Error('IRC identity challenge self-test failed');
    const ircMessage = irc.postMessage(ircAuthentication, { content: 'IRC self test' });
    if (!ircMessage.Ok || irc.recentMessages.length !== 1)
      throw new Error('IRC message self-test failed');
    irc.close();
    if (!auth.heartbeat(registered.AccessToken, '127.0.0.1', 'self-heartbeat').Ok) throw new Error('heartbeat self-test failed');
    const refreshed = auth.refresh({ refreshToken: registered.RefreshToken, deviceFingerprint: fingerprint, edition: 'FREE', clientVersion: 'b8', buildId: 'b8-free', launcherVersion: 'v0.9.21' }, '127.0.0.1', 'self-refresh');
    if (!refreshed.Ok || refreshed.RefreshToken === registered.RefreshToken) throw new Error('refresh self-test failed');
    const refreshedClaims = verifiedEntitlementClaims(refreshed.EntitlementProof, auth.entitlementSigner.publicKey);
    if (refreshedClaims.accessTokenHash !== sha256(refreshed.AccessToken)
      || refreshedClaims.deviceFingerprintHash !== sha256(fingerprint)
      || refreshedClaims.accessExpiresAt !== refreshed.AccessExpiresAt)
      throw new Error('refresh entitlement claims self-test failed');
    const replay = auth.refresh({ refreshToken: registered.RefreshToken, deviceFingerprint: fingerprint, edition: 'FREE', clientVersion: 'b8', buildId: 'b8-free', launcherVersion: 'v0.9.21' }, '127.0.0.1', 'self-replay');
    if (replay.Ok || auth.heartbeat(refreshed.AccessToken, '127.0.0.1', 'self-heartbeat-2').Ok) throw new Error('refresh replay self-test failed');
    const beta = auth.login({ username: 'selftest_user', password: 'SelfTestPassword!2026', deviceFingerprint: fingerprint, hwidVersion: 'v1', hwidQuality: 'STRONG', edition: 'BETA', clientVersion: 'b8', buildId: 'b8-beta', launcherVersion: 'v0.9.21' }, '127.0.0.1', 'self-beta');
    if (beta.Ok || beta.Error !== 'LICENSE_REQUIRED') throw new Error('beta entitlement self-test failed');
    const publicBetaExpiry = now() + 3600;
    if (!auth.setBetaPublicAccess(supportSession, true, publicBetaExpiry, '127.0.0.1', 'self-public-beta-support-denied'))
      throw new Error('public beta support-administrator isolation self-test failed');
    if (auth.setBetaPublicAccess(rootSession, true, publicBetaExpiry, '127.0.0.1', 'self-public-beta-enable'))
      throw new Error('public beta enable self-test failed');
    const activePublicBeta = auth.betaPublicAccess();
    if (!activePublicBeta.enabled || activePublicBeta.expiresAtUtc !== publicBetaExpiry)
      throw new Error('public beta persistence self-test failed');
    const publicBeta = auth.login({ username: 'selftest_user', password: 'SelfTestPassword!2026', deviceFingerprint: fingerprint, hwidVersion: 'v1', hwidQuality: 'STRONG', edition: 'BETA', clientVersion: 'b8', buildId: 'b8-beta', launcherVersion: 'v0.9.21' }, '127.0.0.1', 'self-public-beta-login');
    if (!publicBeta.Ok || publicBeta.Account.tier !== 'BETA' || publicBeta.Account.betaExpiresAt !== publicBetaExpiry || !publicBeta.Account.betaPublicAccess)
      throw new Error('public beta client entitlement self-test failed');
    const freeDuringPublicBeta = auth.login({ username: 'selftest_user', password: 'SelfTestPassword!2026', deviceFingerprint: fingerprint, hwidVersion: 'v1', hwidQuality: 'STRONG', edition: 'FREE', clientVersion: 'b8', buildId: 'b8-free', launcherVersion: 'v0.9.21' }, '127.0.0.1', 'self-public-beta-free-login');
    if (!freeDuringPublicBeta.Ok || freeDuringPublicBeta.Account.tier !== 'FREE' || freeDuringPublicBeta.Account.betaPublicAccess)
      throw new Error('public beta free-edition isolation self-test failed');
    if (!auth.heartbeat(publicBeta.AccessToken, '127.0.0.1', 'self-public-beta-heartbeat').Ok)
      throw new Error('public beta heartbeat self-test failed');
    const legalPublicBeta = auth.legality(publicBeta.AccessToken);
    if (!legalPublicBeta.Ok || !legalPublicBeta.Exists || !legalPublicBeta.Active || !legalPublicBeta.BetaEligible)
      throw new Error('public beta legality self-test failed');
    if (auth.setBetaPublicAccess(rootSession, false, null, '127.0.0.1', 'self-public-beta-disable'))
      throw new Error('public beta disable self-test failed');
    if (auth.heartbeat(publicBeta.AccessToken, '127.0.0.1', 'self-public-beta-heartbeat-disabled').Ok)
      throw new Error('public beta expiry enforcement self-test failed');
    console.log('SELF-TEST OK');
  } finally { fs.rmSync(directory, { recursive: true, force: true }); }
}

if (require.main === module) {
  try { commandLine(); } catch (cause) { console.error(cause.stack || cause.message || cause); process.exitCode = 1; }
}

module.exports = { AppConfig, AuthService, JsonStore, IrcHub };
