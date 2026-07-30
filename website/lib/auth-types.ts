export type Account = {
  id: string;
  username: string;
  role: "USER" | "SUPPORT_ADMIN" | "SUPER_ADMIN";
  tier: "FREE" | "BETA";
  status: "ACTIVE" | "BANNED" | "DELETED";
  betaExpiresAt: number | null;
  hwidBound: boolean;
  hwidQuality: string;
  passwordChangedAt: number | null;
  hwidChangedAt: number | null;
  forcePasswordChange: boolean;
};

export type InternalResult<T> = {
  ok: boolean;
  error?: string;
  message?: string;
  requestId?: string;
} & T;
