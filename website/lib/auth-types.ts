export type Account = {
  id: string;
  username: string;
  role: "USER" | "SUPPORT_ADMIN" | "SUPER_ADMIN";
  tier: "FREE" | "BETA";
  status: "ACTIVE" | "BANNED" | "DELETED";
  betaExpiresAt: number | null;
  betaActive: boolean;
  betaPermanent: boolean;
  betaPublicAccess: boolean;
  betaRedemptions?: BetaRedemption[];
  hwidBound: boolean;
  hwidQuality: string;
  passwordChangedAt: number | null;
  hwidChangedAt: number | null;
  forcePasswordChange: boolean;
};

export type BetaRedemption = {
  id: string;
  product: "BETA_DURATION" | "BETA_PERMANENT";
  durationSeconds: number | null;
  redeemedAt: number;
  newBetaExpiresAt: number;
  codeSuffix: string;
  alreadyApplied?: boolean;
};

export type InternalResult<T> = {
  ok: boolean;
  error?: string;
  message?: string;
  requestId?: string;
} & T;
