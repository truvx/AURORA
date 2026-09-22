/**
 * Failures the gateway reports, each with a stable code the clients can act on.
 *
 * Before these existed every failure reached the client as one generic 500, so the Android
 * app could only show "HTTP 500" - it had no way to tell "AI is not set up" from "try again
 * in a minute". The codes are the contract; the messages are for people and may change.
 */
export type GatewayErrorCode =
  | "unauthorized"
  | "gateway_not_configured"
  | "rate_limited"
  | "payload_too_large"
  | "invalid_request"
  | "ai_not_configured"
  | "provider_busy"
  | "provider_unavailable"
  | "invalid_provider_response";

export const STATUS: Record<GatewayErrorCode, number> = {
  unauthorized: 401,
  gateway_not_configured: 503,
  rate_limited: 429,
  payload_too_large: 413,
  invalid_request: 400,
  ai_not_configured: 503,
  provider_busy: 503,
  provider_unavailable: 502,
  invalid_provider_response: 502,
};

export class GatewayError extends Error {
  constructor(readonly code: GatewayErrorCode, message: string) {
    super(message);
    this.name = "GatewayError";
  }
}

/** The HTTP status an SDK error carries, if any. Both provider SDKs expose `status`. */
export function statusOf(error: unknown): number | undefined {
  if (typeof error !== "object" || error === null || !("status" in error)) return undefined;
  const status = (error as { status: unknown }).status;
  return typeof status === "number" ? status : undefined;
}
